# Issue Tracker

A JIRA-style issue tracker: Spring Boot 3 + Java 21 on the back end, React 18 + TypeScript
(Vite) on the front end. Multiple users, multiple projects, and per-project ticket numbering
(`PROJ1-1232`, `PROJ2-1234`) — each project keeps its own counter. The database is switchable
between H2 and MySQL, and the schema is created and versioned by Flyway.

```
IssueTracker/
├── pom.xml             Aggregator + parent — build everything from here
├── backend/            Spring Boot API (behrainwala.issuetracker)
├── frontend/           React + TypeScript SPA
└── docker-compose.yml  MySQL 8 for the mysql profile
```

## Build one deployable jar

```bash
./mvnw clean package
java -jar backend/target/issue-tracker.jar
```

That single jar contains the API **and** the compiled React UI — open
http://localhost:8080 and the app is there, no separate web server. The root build runs
`npm ci && npm run build`, drops the Vite output into the frontend module's
`META-INF/resources`, and Spring Boot serves it off the classpath; unknown non-`/api` paths
fall back to `index.html` so deep links like `/projects/PROJ1` survive a refresh.

The build downloads its own Node/npm into `frontend/node/` so it works on a machine without
Node installed. To build the API alone (much faster, skips Node entirely):

```bash
./mvnw clean package -Dskip.frontend=true
```

### Opening in IntelliJ

**File → Open** and select the root `pom.xml`. IntelliJ imports both modules with the right
JDK 21 level and dependencies. Run the app from the `IssueTrackerApplication` class, or use a
Maven run configuration on the root project.

> **If `http://localhost:8080/` shows a Whitelabel Error Page**, the React bundle is not on the
> classpath — the API is fine, only the UI is missing. IntelliJ's own Java builder does not run
> npm, so it never produces `frontend/dist`. Either:
>
> - **Settings → Build, Execution, Deployment → Build Tools → Maven → Runner →
>   "Delegate IDE build/run actions to Maven"**, or
> - run `./mvnw compile` once from the root (the UI is copied into `frontend/target/classes`
>   during `process-resources`, so a plain `compile` is enough — `package` is not required).
>
> The app logs an explicit warning at startup when the bundle is missing, so check the console
> before hunting elsewhere.

The `frontend` module is a normal npm project — point IntelliJ's Node interpreter at it if you
want JS tooling. Day-to-day UI work does not need Maven at all; use `npm run dev`.

## Development (hot reload)

Running the jar is for deployment; day to day, run the two sides separately so both hot-reload:

```bash
# Terminal 1 — API on http://localhost:8080
cd backend
mvn spring-boot:run

# Terminal 2 — UI on http://localhost:5173
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. The first start seeds demo data:

| Login   | Password   | Role  |
|---------|------------|-------|
| `admin` | `admin123` | Admin |
| `alice` | `password` | User  |
| `bob`   | `password` | User  |

Two demo projects (`PROJ1`, `PROJ2`) come pre-populated with tickets. Set
`app.seed-demo-data=false` to skip seeding — then the **first account you register becomes the
system administrator**.

The Vite dev server proxies `/api` to `http://localhost:8080`, so the browser never makes a
cross-origin call in development. Point it elsewhere with `VITE_API_TARGET`.

## Switching the database

Persistence is selected by Spring profile. H2 runs in MySQL compatibility mode
(`MODE=MySQL`) so **one Flyway migration set is valid on both engines** — no duplicated DDL.

### H2 (default)

File-backed at `backend/data/issuetracker.mv.db`, so data survives restarts. The H2 console is
at http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/issuetracker`, user `sa`, no
password). Delete the `backend/data/` directory to start over.

### MySQL

```bash
docker compose up -d mysql          # or point at your own server

cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

Overridable via environment variables:

| Variable         | Default                                             |
|------------------|-----------------------------------------------------|
| `MYSQL_URL`      | `jdbc:mysql://localhost:3306/issuetracker?createDatabaseIfNotExist=true&...` |
| `MYSQL_USER`     | `root`                                              |
| `MYSQL_PASSWORD` | `root`                                              |

You can also select the profile without touching the command line by setting `DB_PROFILE=mysql`.

## Schema migrations (Flyway)

Migrations live in `backend/src/main/resources/db/migration` and run automatically on startup.
Hibernate is set to `ddl-auto: validate`, so the app **refuses to start if the entities and the
migrated schema disagree** — schema drift becomes a startup failure instead of a runtime
surprise.

To change the schema, add a new file — never edit an applied one:

```
V2__add_ticket_labels.sql
V3__add_sprint_table.sql
```

Keep the SQL portable (no engine-specific types or clauses) so it applies cleanly to both H2 and
MySQL.

## Ticket keys

Each project stores a `ticket_seq` counter. Creating a ticket re-reads the project row under a
`PESSIMISTIC_WRITE` lock, increments the counter, and stores the resulting key
(`PROJ1-1`, `PROJ1-2`, …) on the ticket. The row lock is what prevents two concurrent creates
from claiming the same number; a unique constraint on `(project_id, ticket_number)` backstops it
at the database level. Numbering is per project, so `PROJ2` starts again at 1.

## Epics

Any ticket can be typed `EPIC`. A ticket belongs to **at most one epic**; picking a different
epic moves it. There are three ways to put a ticket in an epic:

- **From the epic** — open it and use **Add existing** (search, tick several, add in one go) or
  **New ticket** to create one straight into the epic.
- **From the ticket** — the **Epic** dropdown in the sidebar.
- **At creation** — the Epic field in the create dialog.

An epic shows **Tickets in this epic** with a done/total progress bar; `✕` on a row removes it
from the epic without deleting the ticket. Children carry a chip linking back to their epic,
visible on board cards too.

**Tickets can only be added to an epic from the same project.** That is enforced in two places:
the candidate picker (`GET /api/tickets/{key}/candidates`) only ever returns tickets from the
epic's own project — also excluding epics themselves and anything already in this epic — and the
API rejects a cross-project attach with `409 Conflict` regardless of what the client sends.
Tickets currently held by *another* epic are offered (labelled with where they are), since moving
them between epics is legitimate.

Other rules, all server-side: the parent must be an `EPIC`-typed ticket, epics cannot nest
(turning a ticket into an epic detaches it from its own parent), and deleting an epic
**releases** its tickets rather than deleting them (`ON DELETE SET NULL`).

## Linking tickets

Tickets can be linked to each other — a bug to the task that caused it, work that blocks other
work, duplicates. Open a ticket, choose **Link a ticket**, pick a relationship, and search for
the other ticket by key or title. **Links may cross projects.**

Each link is stored **once**, as a directed row. The opposite ticket shows the inverse
relationship, so "PROJ1-3 is caused by PROJ1-1" appears on PROJ1-1 as "causes PROJ1-3" with no
mirror row that could drift out of step. Available types: relates to, blocks / is blocked by,
duplicates / is duplicated by, causes / is caused by.

Rules: a ticket cannot link to itself, the same relationship cannot be recorded twice (in either
direction), and deleting a ticket removes its links. Creating a link needs write access on the
ticket you start from and read access on the other one — and a link whose far end sits in a
project you cannot see is hidden from you entirely, so links never leak titles across the
visibility boundary.

## Permissions

- **Global roles** — `ADMIN` (sees and administers everything) and `USER`.
- **Per-project roles** — `LEAD` (rename the project, manage members, delete tickets),
  `MEMBER` (create/edit tickets and comments), `VIEWER` (read-only).

### Project leads

Leadership is a **membership role**, not a field on the project, so:

- a project can have **several leads**;
- a user can **lead several projects**;
- **any lead can add users to their own project** and change their roles, without needing a
  system administrator — a lead is often an ordinary `USER` globally.

A project must always keep at least one lead: removing or demoting the last one returns
`409 Conflict`, as does an admin trying to strip it via the assignment picker. Promote a
co-lead first.

> Earlier versions had a singular `projects.lead_id` column *and* a `LEAD` membership role,
> which meant two sources of truth that could disagree. `V3__multiple_project_leads.sql` folds
> the column into `project_members` (promoting or inserting rows for existing leads first), so
> there is now one place to look.

Users only see projects they lead or belong to. Authentication is a stateless JWT bearer token;
set `JWT_SECRET` (≥32 bytes) in any real deployment.

### Administering users

Admins get a **Users** entry in the top nav (`/admin/users`). Accounts are **disabled, never
deleted**, so the tickets and comments they authored keep pointing at a real person.

Disabling takes effect immediately: every request re-checks the account, so a token issued
before the account was disabled stops working rather than lasting until it expires.

Two guards prevent an admin locking everyone out — you cannot disable or demote **your own**
account, and you cannot disable or demote the **last enabled administrator**. Both return
`409 Conflict` with an explanatory message, and the UI disables the corresponding controls.

Usernames are immutable, since the username is the JWT subject.

### Assigning users to projects

**Users → Projects** on any row opens a picker: tick one or more projects and choose a role for
each. Saving replaces that user's assignments wholesale, so unticking a project revokes access.

A user sees **only the projects they are assigned to** — `GET /api/projects` returns just those,
and reaching any other project directly returns `403`, including its tickets. Admins are the
exception: they see every project, which is what makes the assignment picker usable.

Projects the user *leads* appear as locked rows. Leading grants access on its own, so it cannot
be revoked here — reassign the lead in that project's settings first.

### Passwords

Every signed-in user can change their own password from the **avatar menu in the header →
Change password**; it requires the current password, so an unlocked screen is not enough to take
an account over. Admins can additionally reset anyone's password without knowing the old one.

The same menu has **Your profile**: display name, username, email, account role, status, and the
projects you are assigned to with your role in each. It is read-only — display name, email and
role are an administrator's to change — so the password is the one thing you manage yourself.

One caveat: because tokens are stateless, changing a password does **not** invalidate sessions
already signed in elsewhere — those last until the token expires (12 hours by default). Disabling
an account *does* take effect immediately. If you need password changes to cut off other
sessions too, that needs a token version column on the user, which is not implemented.

## API

All routes require `Authorization: Bearer <token>` except `/api/auth/register` and
`/api/auth/login`.

| Method   | Path                                    | Purpose                          |
|----------|-----------------------------------------|----------------------------------|
| `POST`   | `/api/auth/register`                    | Register + receive a token       |
| `POST`   | `/api/auth/login`                       | Log in                           |
| `GET`    | `/api/auth/me`                          | Current user                     |
| `POST`   | `/api/auth/change-password`             | Change your own password         |
| `GET`    | `/api/auth/me/projects`                 | Your own project assignments     |
| `GET`    | `/api/users`                            | User directory (pickers)         |
| `GET`    | `/api/admin/users`                      | All accounts (**admin**)         |
| `POST`   | `/api/admin/users`                      | Create an account (**admin**)    |
| `PUT`    | `/api/admin/users/{id}`                 | Edit email/name/role/status (**admin**) |
| `PATCH`  | `/api/admin/users/{id}/enabled`         | Enable or disable (**admin**)    |
| `PUT`    | `/api/admin/users/{id}/password`        | Reset a password (**admin**)     |
| `GET`    | `/api/admin/users/{id}/projects`        | A user's project assignments (**admin**) |
| `PUT`    | `/api/admin/users/{id}/projects`        | Replace their assignments (**admin**) |
| `GET`    | `/api/projects`                         | Projects visible to you          |
| `POST`   | `/api/projects`                         | Create a project                 |
| `GET`    | `/api/projects/{key}`                   | Project detail                   |
| `PUT`    | `/api/projects/{key}`                   | Update a project                 |
| `DELETE` | `/api/projects/{key}`                   | Delete a project                 |
| `GET`    | `/api/projects/{key}/members`           | List members                     |
| `POST`   | `/api/projects/{key}/members`           | Add / change a member's role     |
| `DELETE` | `/api/projects/{key}/members/{userId}`  | Remove a member                  |
| `GET`    | `/api/projects/{key}/tickets`           | Search tickets (`status`, `assigneeId`, `q`, `page`, `size`) |
| `POST`   | `/api/projects/{key}/tickets`           | Create a ticket                  |
| `GET`    | `/api/projects/{key}/epics`             | Epics of a project (epic picker)  |
| `GET`    | `/api/tickets/{ticketKey}/children`     | Tickets filed under an epic      |
| `POST`   | `/api/tickets/{ticketKey}/children`     | Add existing tickets to an epic  |
| `DELETE` | `/api/tickets/{ticketKey}/children/{childKey}` | Remove a ticket from an epic |
| `GET`    | `/api/tickets/{ticketKey}/candidates`   | Tickets addable to this epic (same project) |
| `GET`    | `/api/tickets/search`                   | Cross-project lookup for the link picker |
| `GET`    | `/api/tickets/{ticketKey}`              | Ticket detail                    |
| `GET`    | `/api/tickets/{ticketKey}/links`        | Linked tickets                   |
| `POST`   | `/api/tickets/{ticketKey}/links`        | Link to another ticket           |
| `DELETE` | `/api/links/{linkId}`                   | Remove a link                    |
| `PATCH`  | `/api/tickets/{ticketKey}`              | Partial update                   |
| `PATCH`  | `/api/tickets/{ticketKey}/status`       | Transition status (board drag)   |
| `DELETE` | `/api/tickets/{ticketKey}`              | Delete a ticket                  |
| `GET`    | `/api/tickets/{ticketKey}/comments`     | List comments                    |
| `POST`   | `/api/tickets/{ticketKey}/comments`     | Add a comment                    |
| `PUT`    | `/api/comments/{id}`                    | Edit a comment                   |
| `DELETE` | `/api/comments/{id}`                    | Delete a comment                 |

Errors come back as RFC 7807 `ProblemDetail` documents.

Paged endpoints serialise through Spring Data's `PagedModel`
(`@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`), giving a stable shape:

```json
{ "content": [ ... ], "page": { "size": 50, "number": 0, "totalElements": 3, "totalPages": 1 } }
```

Returning `PageImpl` directly would serialise a Spring-internal class whose JSON carries no
stability guarantee, and logs a warning on every paged request.

## UI

- **Projects** — grid of the projects you can see, with a create dialog that suggests a key from
  the name.
- **Board** — five columns (Backlog → To Do → In Progress → In Review → Done) with drag-and-drop
  transitions that update optimistically and roll back if the server rejects the move. Toggle to
  a table view; filter by assignee or search by title/key.
- **Ticket** — inline title/description editing, status/type/priority/assignee/points/due date in
  the sidebar, linked tickets, and threaded comments.
- **Settings** — project details, lead, membership and roles, and project deletion.
- **Users** (admins only, in the top nav) — the admin dashboard: create accounts, edit
  name/email/role, reset passwords, and enable/disable accounts, with search and a
  "show disabled" filter.

Light and dark themes follow the OS preference.

## Tests and builds

```bash
./mvnw test                    # whole reactor
cd backend  && mvn test        # boots the app on in-memory H2 and exercises the REST API
cd frontend && npm run build   # type-check + production bundle
```

45 backend tests across seven suites: core API (ticket-key numbering, transitions, comments),
user administration (including immediate revocation on disable and the lockout guards),
self-service password change, project assignment and the visibility rule it enforces, project
leads (several per project, several projects per lead, leads staffing their own project, and
last-lead protection), ticket linking (inverse relationships, cross-project links,
duplicate/self-link rejection, visibility filtering), and epics (one epic per ticket, children
listing, adding/removing from the epic side, same-project enforcement on both the picker and the
API, and release-on-delete).

Suites that depend on "the first registered account becomes ADMIN" run against their own
in-memory database so they do not depend on execution order.
