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
| `MYSQL_URL`      | `jdbc:mysql://localhost:3306/issuetracker?createDatabaseIfNotExist=true&nullCatalogMeansCurrent=true&...` |
| `MYSQL_USER`     | `root`                                              |
| `MYSQL_PASSWORD` | `admin`                                             |

You can also select the profile without touching the command line by setting `DB_PROFILE=mysql`.

> **Keep `nullCatalogMeansCurrent=true` if you override `MYSQL_URL`.** Connector/J 8 defaults it
> to `false`, which makes JDBC metadata lookups span **every database on the server** instead of
> the one you connected to. If any other database has a same-named table — a `users` table is
> almost guaranteed — Hibernate's schema validation can match that one and refuse to start:
>
> ```
> Schema-validation: wrong column type encountered in column [id] in table [users];
> found [varchar (Types#VARCHAR)], but expecting [bigint (Types#BIGINT)]
> ```
>
> The error names your own table but is describing someone else's. Confirm with:
>
> ```sql
> SELECT table_schema, column_type FROM information_schema.columns
> WHERE table_name = 'users' AND column_name = 'id';
> ```
>
> More than one row means the lookup is ambiguous and this flag is the fix.

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

## Archiving

Both **tickets** and **projects** can be archived: hidden from the day-to-day views, kept intact,
and restorable at any time. **Anything archived is read-only until it is restored.**

### Archived is read-only

An archived ticket, and everything inside an archived project, refuses content changes with
`409 Conflict`. That covers the obvious routes and the ones that skip the usual write guard:

| Action on archived content            | Result |
|---------------------------------------|--------|
| Edit or move the ticket               | `409`  |
| Create a ticket in the project        | `409`  |
| Add a comment                         | `409`  |
| Edit or delete **your own** comment   | `409`  |
| Attach or remove **your own** document | `409` |
| Add or remove a ticket link           | `409`  |
| File it under an epic / add children  | `409`  |
| Rename the project, change membership | `409`  |
| **Read** anything                     | `200`  |

Three of those needed explicit checks because they bypass the guard: a comment author edits via
an authorship shortcut, unlinking only tests permission with a boolean, and the admin assignment
endpoint never touches `AccessGuard` at all. The UI mirrors the rules — disabled fields, hidden
buttons, and a banner explaining what to do — but the server is what enforces them.

Deleting is deliberately still allowed on archived items: archiving is the reversible option, and
having to restore something merely to throw it away would be perverse. An archived ticket still
counts against its project, though — see [Permissions](#permissions) for the rule that a project
must be empty before it can be deleted.

### Tickets

Archive from the ticket page; the button is disabled unless the ticket sits in the board's
**finished lane** — which lane that is comes from the project's own board, see
[Boards, swim lanes and templates](#boards-swim-lanes-and-templates).

Each project's board has a third tab, **Archived**, listing what has been archived with when and
by whom, and a **Restore** action. The board and list tabs never show archived tickets, and the
project card's ticket count excludes them — that's the whole point of the feature.

**An epic can only be archived once every one of its tickets is archived.** Archiving an epic
with live work under it would hide that work behind a closed parent, so it is refused with a
count of what is left:

```
PROJ1-6 still has 1 ticket that is not archived - archive them first
```

The epic's ticket list shows how many remain (`2 of 5 not archived`), with archived children
dimmed and badged so you can see the whole picture.

Other rules:

- **Only `DONE` tickets can be archived** — including epics, which need `DONE` *and* all children
  archived.
- **Restore respects the hierarchy**: a child under an archived epic cannot be restored on its
  own (`Its epic PROJ1-6 is archived - restore the epic first`), otherwise live work would sit
  under an archived parent. Equally, live work cannot be filed *into* an archived epic.
- Archived tickets are not offered as epic candidates.

### Projects

A project lead or an administrator can archive a project from **Settings**. Archived projects
leave the project list and appear under its **Archived** tab, showing when and by whom, with a
**Restore** action. There is no "everything must be done first" rule here — a project can be
shelved mid-flight, and comes back exactly as it was.

The project card's ticket count always excludes archived tickets.

## Status history

Every move between lanes is logged with **who** moved it, **from** which lane, **to** which, and
**when** — whether it came from a board drag, the sidebar dropdown, or a `PATCH`. Lane names are
stored as a **snapshot**, so a lane that is later renamed or removed does not rewrite history:

```
moved from Backlog to To Do by Alice Nguyen on 20 Aug 2026 at 23:14
moved from To Do to In Progress by Bob Carter on 20 Aug 2026 at 23:14
moved from In Progress to Done by Alice Nguyen on 20 Aug 2026 at 23:14
```

The ticket page shows the most recent move by default, with **Show all** for the full trail
(newest first, the latest badged). The last entry is the last person who moved it.

Setting a ticket to the bucket it is already in is **not** a move and leaves no entry, so
re-saving a form does not pollute the trail. Entries are ordered by id rather than timestamp:
MySQL `TIMESTAMP` is second-precision, so two moves in the same second would otherwise sort
arbitrarily. History follows project visibility like everything else, and is kept as a full log
rather than a "last mover" column so the trail survives later moves.

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

## Attachments

Any number of documents can be attached to a ticket (default cap: 20 per ticket, 10 MB each).
Attachments follow the ticket's own access rules — read them if you can view the project, add
them if you can write to it — and an archived ticket accepts no new ones.

**Only the metadata is in the database.** The bytes are written to `app.attachments.directory`
under a freshly generated UUID with no extension; the uploaded filename is stored as display
text and never used to build a path. There is consequently nothing to traverse, and nothing in
that directory is reachable except through `GET /api/attachments/{id}`, which re-checks project
membership on every request. Knowing an id is not access.

### What may be uploaded

An **allow-list**, not a list of banned executables — a deny-list is never finished, and the
format nobody thought of would be allowed by default:

```
pdf doc docx xls xlsx ppt pptx  txt md csv json  png jpg jpeg gif webp svg  zip
```

Three independent gates run in order, and no bytes reach disk until all three pass:

1. **Extension** must be on the list above. The declared MIME type from the browser is ignored
   entirely — a client can claim anything — and the type served back is the one mapped from the
   extension.
2. **Content** must not look executable. The leading bytes are checked for `MZ` (Windows), ELF,
   Mach-O, `#!` and the Java class magic **whatever the file is called**, so `payload.exe`
   renamed to `invoice.pdf` is still refused. Zip-based formats (`docx`, `xlsx`, `pptx`, `zip`)
   must genuinely start with `PK`.
3. **Filename** must survive sanitising: directory separators, control characters and leading
   dots are stripped, so `../../../etc/passwd.txt` is stored as `passwd.txt`.

### Downloads are inert

Every download is served as `application/octet-stream` with `Content-Disposition: attachment`,
`X-Content-Type-Options: nosniff` and a locked-down CSP — regardless of what the file actually
is. An uploaded SVG or HTML page handed back with its real type would run script on this origin
with the viewer's session, so nothing is ever rendered in place; it is only ever saved. That is
also why SVG can stay on the allow-list.

Because the endpoint is authorised by the bearer token, a plain `href` cannot download it — the
UI fetches the file and clicks it through a temporary object URL.

A document can be removed by whoever uploaded it or by a global administrator, which deletes the
row and the file together. See [Owning what you wrote](#owning-what-you-wrote). Deleting the
**ticket** clears its documents from disk too.

### Keeping the store tidy

File deletions happen **after the surrounding transaction commits**, never inside it. Deleting
inline would destroy the bytes for good if the transaction then rolled back, leaving rows
pointing at nothing; waiting for the commit inverts the failure, so a failed unlink merely
orphans a file — wasted disk, nothing lost.

Orphans can still appear: a crash between writing a file and committing its row, or an unlink
the OS refuses. A **nightly sweep** removes files no database row points at:

| Setting | Default | |
|---|---|---|
| `ATTACHMENT_SWEEP_CRON` | `0 30 3 * * *` | `-` disables the schedule |
| `ATTACHMENT_SWEEP_ZONE` | `UTC` | |
| `ATTACHMENT_ORPHAN_GRACE` | `6h` | how old a file must be before it is judged |

The grace period is the load-bearing part. An upload writes its bytes before its row commits,
so for a moment a perfectly good file is indistinguishable from an orphan — waiting a few hours
makes that window irrelevant, and an orphan is in no hurry. The sweep only ever deletes files,
never rows, so the worst a bug here could do is remove a file that was about to be claimed.

> If you run more than one instance against the same store, they will all sweep. That is
> harmless — the work is idempotent and each file is judged against the same shared database —
> but set `ATTACHMENT_SWEEP_CRON=-` on all but one if you would rather it ran once.

## Boards, swim lanes and templates

**A board belongs to its project.** The lanes tickets move between are per-project data, copied
from a template when the project is created — along with any [starter tickets](#starter-tickets)
that template prescribes — not a fixed set baked into the app. A law firm
tracking matters, a team shipping software and somebody planning a holiday get boards that have
nothing in common:

| Template | Lanes |
|---|---|
| **Kanban** *(default)* | To Do → In Progress → Done |
| **Software Development** | Backlog → To Do → In Progress → In Review → Done |
| **Legal Case** | Intake → Discovery → Filings Due → Awaiting Hearing → Closed |
| **Trip Planning** | Ideas → Researching → Booked → Packed → Done |
| **Recruitment** | Applied → Screening → Interviewing → Offer → Decided |

Those five ship with the app. **Only an administrator can define new ones**, from **Templates**
in the top nav (`/admin/templates`) — a template shapes how everybody else's projects begin, so
it is an installation-wide decision rather than a per-project one. Reading them is open to any
signed-in user, because the create-project dialog offers them and previews their lanes.

### The lane's name *is* the ticket's status

There is no hidden code behind a display label: a lane called `Awaiting Hearing` means its
tickets literally hold `"Awaiting Hearing"`. That keeps the data legible, lets a template invent
any lane it likes, and means the status-history table — which already stored text — stays
readable years later without a lookup table.

It has one consequence worth stating: **renaming a lane rewrites its tickets**, in the same
transaction. That is handled for you, but it is why a rename is not a free operation.

### Every board needs a start and a finish

Exactly one lane is the **starting lane** (where new tickets appear) and exactly one is the
**finished lane**. Without them, "where does a new ticket go?" and "what may be archived?" have
no answer. Submitting a board with none or two of either is refused with `400`.

This is what replaced the old hardcoded `DONE`: **archiving now keys off whichever lane the
board marks as finished**. On a Legal Case project you archive from `Closed`, and the refusal
message says so.

### Editing a board in use

A lead or administrator can reshape a project's board from **Settings → Board**: rename, reorder,
add and remove lanes, and move the start/finish markers. The whole board is submitted at once,
because a board is edited as a shape — reordering is just the same lanes in a different sequence.

- **Renaming** a lane carries its tickets with it.
- **Removing** a lane requires it to be empty first (`409` naming how many tickets are in the
  way). Silently moving somebody's tickets elsewhere would be worse than refusing.
- **Adding** a lane is just a new entry with no id.

> **Why lanes are matched by id, not by position**
>
> The submission carries each existing lane's id. Without it the server could only match by
> position, and dropping a lane from the middle of the board would read as a chain of renames —
> quietly dragging every ticket one lane to the left. A lane whose id is absent is being
> removed; a lane that keeps its id and changes its name is a rename. This was caught by a test
> that expected `409` and got `400`.

### Starter tickets

A template can also carry the **work this kind of project always begins with**, created in
every project made from it. A blank board is a poor start for repeatable work: a legal matter
always opens with a conflict check, a trip always needs somewhere to keep the booking
confirmations. Those are properties of the *kind* of project — which is what a template is.

| Template | Starts every project with |
|---|---|
| **Kanban** | *(nothing — it is the "nothing more specific fits" board)* |
| **Software Development** | Set up the repository and CI · Agree the definition of done · Write the first release notes |
| **Legal Case** | Run the conflict check · Engagement letter signed and filed · Client documents · Key dates and limitation period |
| **Trip Planning** | **Travel documents** · Check passports and visas · Book transport · Book accommodation · Packing list |
| **Recruitment** | Write the job description · Agree the interview loop · Agree the scorecard |

Each starter carries a title, an optional description, a type, a priority, and **which lane it
lands in** — so a legal matter's tickets appear in `Intake` and a trip's in `Ideas`. A starter
naming no lane goes to the board's starting lane.

Creating `JAPAN` from **Trip Planning** produces `JAPAN-1 Travel documents` in `Ideas`, and
booking confirmations get [attached](#attachments) to it as they arrive — one place for the
whole trip's paperwork.

**They are ordinary tickets from the moment they exist**: numbered in the project's own
sequence, reported by whoever created the project, and edited, moved, archived or deleted like
any other. Nothing marks them as special afterwards.

> A starter ticket that names a lane the *template* does not have is refused when the template
> is saved — it almost always means a lane was renamed and the ticket was left pointing at the
> old name. At project-creation time the same mismatch is treated more gently: the ticket falls
> back to the starting lane rather than failing the whole creation, because a half-made project
> would be worse than a ticket one column to the left.

Starter tickets do not currently nest — a template can create an `EPIC`, but not children
filed under it.

### Templates are a starting point, not a binding

Creating a project **copies** the template's lanes and creates its starter tickets. Editing the
template afterwards never reaches back into projects already made from it, so improving a
template cannot rearrange a board somebody is working on, nor add tickets to it weeks later. A project remembers which template it came from for display only;
deleting that template forgets the label rather than blocking on projects that have long since
diverged. Built-in templates can be edited but not deleted, so the list is never empty.

### Upgrading an existing installation

`V12` migrates in place. Every project that already exists keeps **exactly** the board it had —
the old five buckets become five lanes on that project — and existing tickets and their history
are rewritten from enum spellings to lane names (`IN_PROGRESS` → `In Progress`). Nothing is lost
and no board changes shape. New projects created without naming a template get **Kanban**.

## Project images

Every project can have an optional picture, set by a **lead or an administrator** from
**Settings → Project image**. It appears on the project tile between the name and the
description, and as a small mark beside the project name on the board. Projects without one look
exactly as they did before — nothing is reserved for it.

| | |
|---|---|
| `GET /api/projects/{key}/image` | any member — the image itself |
| `PUT /api/projects/{key}/image` | `LEAD` or `ADMIN` — set or replace |
| `DELETE /api/projects/{key}/image` | `LEAD` or `ADMIN` — remove |

**Unlike the company logo, this one is not public.** A project picture is project data, so the
endpoint runs the same membership check as reading the project: a non-member gets `403` and an
anonymous caller `401`. That has a consequence in the UI — a plain `<img src>` carries no bearer
token, so the `AuthImage` component fetches the bytes and hands the tag an object URL, revoking
it on unmount so a long list of tiles does not leak one blob per card.

Setting an image needs the same rights as renaming the project, and an archived project refuses
it with `409` like any other change. `hasImage` and `imageVersion` ride along on the ordinary
project payloads, so a list of tiles needs no extra round trip to know what to draw.

The file lives under `PROJECT_IMAGE_DIR` (default `data/project-images`), **named after the
project id**. That is deliberate: a project has at most one picture, so replacing it overwrites
in place — there is no key to store, no second file to clean up, and no way to leave an orphan
behind. Deleting the project deletes the file with it. The same directory rule as the logo
applies, and is enforced the same way: **the app refuses to start** if `app.projects.image-directory`
sits inside the swept attachment directory.

Uploads go through the same `ImagePolicy` as the company logo — PNG, JPEG, GIF, WebP or SVG up
to 1 MB (`PROJECT_MAX_IMAGE_BYTES`), screened for executable content by magic bytes — and are
served with `nosniff` and `default-src 'none'; sandbox` for the same reason.

## Branding

An administrator can put the company name and logo in the title bar from **Branding** in the top
nav (`/admin/branding`). Both are optional: with no name the app calls itself Issue Tracker, and
with no logo it draws its own mark. They appear in the app header, on the sign-in screen, and in
the browser tab (`Northwind Ltd · Issue Tracker`).

| | |
|---|---|
| `GET /api/branding` | **public** — name and whether a logo is set |
| `GET /api/branding/logo` | **public** — the image itself |
| `PUT /api/branding` | `ADMIN` — set or clear the name |
| `PUT /api/branding/logo` | `ADMIN` — upload an image |
| `DELETE /api/branding/logo` | `ADMIN` — remove it |

**The two reads are deliberately anonymous.** The sign-in page is branded, so the name and logo
have to be fetchable before anyone has a session — which means an anonymous visitor who can reach
the login page learns which company runs the tracker. That is what branding is for, but it is a
real disclosure and worth knowing. Only `GET` is opened; the writes are `@PreAuthorize`'d.

A logo must be a PNG, JPEG, GIF, WebP or SVG of at most 512 KB (`BRANDING_MAX_LOGO_BYTES`, with
`BRANDING_DIR` for where it lands), and
is screened for executable content by the same magic-byte check that guards attachments — an
`.exe` renamed `logo.png` is refused. It is served with its real type, since it has to render in
an `<img>`, but with `nosniff` and `default-src 'none'; sandbox`: an SVG opened directly in a tab
would otherwise be a scripting context, and sandboxed it is only ever a picture.

### Where the logo is kept

The image is a **file on disk** under `BRANDING_DIR` (default `data/branding`); the database row
beside it holds only the name, content type and timestamp. There is one logo, so it needs no
key — it is a single fixed file, written to a temporary name and moved into place so a failed
write never leaves a truncated image where the whole one used to be. Mount that directory as a
volume, or the logo does not survive a redeploy.

> **It must not sit inside the attachment directory.** The
> [nightly orphan sweep](#keeping-the-store-tidy) deletes files in the attachment store that no
> attachment row points at, and no attachment row will ever point at the logo — it would look
> exactly like an orphan and vanish six hours later. That is a configuration mistake which
> looks fine until it silently eats the logo, so **the app refuses to start** if
> `app.branding.directory` is the same as, or nested inside, `app.attachments.directory`.

> **Why the `logo` column was dropped rather than kept as a blob** (`V10`)
>
> It was briefly a `LONGBLOB`, and MySQL would not start: H2 and MySQL disagree about what a
> large binary column *is*. The same declaration is reported by MySQL as `LONGVARBINARY` and by
> H2 as `BLOB`, so under `ddl-auto: validate` no single mapping satisfies both — `@Lob byte[]`
> expects `BLOB` and MySQL refuses with *"found [longblob (Types#LONGVARBINARY)], but expecting
> [tinyblob (Types#BLOB)]"*, while `@JdbcTypeCode(LONGVARBINARY)` fixes MySQL and breaks H2 in
> the mirror image. On disk the question does not arise.
>
> `V10` also clears `logo_content_type` and `logo_updated_at`, because those are what `hasLogo`
> is computed from: leaving them set would advertise a logo with no bytes behind it and hand
> every caller a 404. An installation that had already uploaded one re-uploads it.
>
> **This class of bug is invisible to the test suite**, which runs on H2. After changing
> anything about the schema, start the app against MySQL — `docker compose up -d mysql`, then
> `./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql` — and confirm it boots.

The client appends the logo's last-updated stamp to the image URL (`/api/branding/logo?v=…`), so
a replacement arrives under a new address instead of waiting out the hour-long cache.

## Formatting text

Ticket descriptions, project descriptions and comments support **bold**, *italic* and
underline, with the shortcuts you already know:

| | Shortcut | Typed as |
|---|---|---|
| **Bold** | `Ctrl`/`Cmd` + `B` | `**bold**` |
| *Italic* | `Ctrl`/`Cmd` + `I` | `*italic*` |
| Underline | `Ctrl`/`Cmd` + `U` | `__underline__` |

Each shortcut **toggles**: pressing it again on a marked selection removes the markers. With
nothing selected it inserts the pair and parks the caret between them. Every editor also carries
a small **B / I / U** toolbar, so the feature is discoverable without knowing the syntax.

**Marks combine.** Bold, italic and underline can all sit on the same run of text, in any order,
and each toggles off independently: `***both***` is bold *and* italic, `__**text**__` adds
underline over bold. Applying all three and removing all three returns the text to plain.

> Bold and italic share the asterisk and differ only in how many, so the editor counts the
> **run** of markers around the selection rather than matching them as strings — one asterisk
> is italic, two bold, three both. A literal "does it already end with `*`?" test sees the
> second asterisk of a bold pair and strips it, which makes bold-plus-italic impossible. The
> editor also steps outward over a *different* mark sitting between the selection and its own
> markers, so un-bolding the text of `__**text**__` finds the bold it should remove.

A marked run must begin and end with a non-space, the same rule markdown uses, so `2 * 3 * 4`
stays multiplication rather than becoming an italic ` 3 `. An unclosed `**` is shown literally.

> **Why markers in a plain text column, and not HTML?**
>
> The markers are ordinary characters in the ordinary `description`/`body` columns — no
> migration, and text written before this feature renders unchanged. Editing stays a real
> `<textarea>`, so the string that is typed, stored and re-rendered is the same string
> throughout; a `contenteditable` would introduce a second representation that can drift.
>
> Most importantly it keeps the invariant from the section below: the renderer emits React
> elements and **never** hands a string to the DOM as HTML, so there is no sanitiser to get
> right and no stored-XSS surface. Accepting HTML would have created both.

## Links in descriptions and comments

URLs typed into a ticket description, a project description or a comment are turned into
clickable links that **open in a new tab**, with `rel="noopener noreferrer"` so the opened page
cannot reach back through `window.opener` and navigate the tab it came from.

Only `http` and `https` become links. Anything else someone types — `javascript:`, `data:` — is
left as inert text. User-written text is rendered as React children throughout and never as
HTML, so markup pasted into a comment is displayed, not executed.

Links and formatting are parsed in one pass, so a URL inside `**bold**` still becomes a link and
underscores inside a URL are not read as underline markers.

## Permissions

- **Global roles** — `ADMIN` (sees and administers everything) and `USER`.
- **Per-project roles** — `LEAD` (rename the project, manage members, archive it),
  `MEMBER` (create/edit tickets and comments), `VIEWER` (read-only).

**Deleting a ticket is reserved for a global `ADMIN`** — a project lead gets `403` and archives
instead. Deleting takes the ticket's comments, attachments, links and status history with it and
cannot be undone, whereas archiving gets it out of the way reversibly. The **Delete** button on a
ticket is shown only to administrators.

**A project can only be deleted once it is empty.** Deleting one that still has tickets returns
`409 Conflict` naming how many are left; archived tickets count, since they are still work the
delete would destroy. A lead may delete a project, but only after someone has removed its
tickets one by one — which, given the rule above, means an administrator. An empty project is a
bookkeeping mistake and goes without ceremony; a full one is a body of work and should not
vanish on a single click.

### Project leads

Leadership is a **membership role**, not a field on the project, so:

- a project can have **several leads**;
- a user can **lead several projects**;
- **any lead can add users to their own project** and change their roles, without needing a
  system administrator — a lead is often an ordinary `USER` globally.

A project must always keep at least one lead: removing or demoting the last one returns
`409 Conflict`, as does an admin trying to strip it via the assignment picker. Promote a
co-lead first.

### Owning what you wrote

**Comments and attachments belong to whoever added them.** Only their author/uploader or a
**global `ADMIN`** may edit or remove one:

| Who                                | Remove someone else's comment / attachment |
|------------------------------------|--------------------------------------------|
| The author / uploader              | allowed                                    |
| Global `ADMIN`                     | allowed                                    |
| Project `LEAD`                     | `403`                                      |
| Project `MEMBER` / `VIEWER`        | `403`                                      |
| Anyone, on an **archived** ticket  | `409`                                      |

Deleting the whole **ticket** is stricter still — global `ADMIN` only, its author included; see
[Permissions](#permissions).

A project lead is deliberately *not* on that list. Leading a project is not the same as owning
what other people wrote in it — a lead who needs a comment gone escalates to an administrator.
This is the one place where `LEAD` is not a superset of `MEMBER`, which is why it goes through
`AccessGuard.requireOwnerOrAdmin` rather than the usual `canAdminister`.

The archive rule wins over both: once a ticket is archived nobody edits or deletes its comments
and attachments, not even their author or an administrator. Restore it first. The UI matches —
on an archived ticket the comment box, the **Delete** links and **Attach files** are all gone,
not merely disabled.

> Earlier versions had a singular `projects.lead_id` column *and* a `LEAD` membership role,
> which meant two sources of truth that could disagree. `V3__multiple_project_leads.sql` folds
> the column into `project_members` (promoting or inserting rows for existing leads first), so
> there is now one place to look.

Users only see projects they lead or belong to. Authentication is a stateless JWT bearer token;
set `JWT_SECRET` (≥32 bytes) in any real deployment.

Attachment storage is configured with `ATTACHMENT_DIR` (default `data/attachments`),
`ATTACHMENT_MAX_BYTES` (default 10 MB) and `ATTACHMENT_MAX_PER_TICKET` (default 20). **The
directory must not sit under anything served statically** — files are handed out only through
the API, after the project check. In a container, mount it as a volume so attachments survive a
redeploy; `data/` is gitignored.

### Administering users

Admins get **Users** and **Branding** entries in the top nav. Accounts are **disabled, never
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
| `GET`    | `/api/projects`                         | Projects visible to you (`archived`) |
| `POST`   | `/api/projects/{key}/archive`           | Archive a project                |
| `POST`   | `/api/projects/{key}/restore`           | Restore an archived project      |
| `POST`   | `/api/projects`                         | Create a project                 |
| `GET`    | `/api/projects/{key}`                   | Project detail                   |
| `PUT`    | `/api/projects/{key}`                   | Update a project                 |
| `DELETE` | `/api/projects/{key}`                   | Delete a project (must be empty) |
| `GET`    | `/api/templates`                        | Project templates (any user)     |
| `POST`   | `/api/templates`                        | Define a template (`ADMIN`)      |
| `PUT`    | `/api/templates/{id}`                   | Edit a template (`ADMIN`)        |
| `DELETE` | `/api/templates/{id}`                   | Delete a template (`ADMIN`, not built-in) |
| `GET`    | `/api/projects/{key}/lanes`             | The project's swim lanes         |
| `PUT`    | `/api/projects/{key}/lanes`             | Replace the board (`LEAD`/`ADMIN`) |
| `GET`    | `/api/projects/{key}/image`             | Project image (members only)     |
| `PUT`    | `/api/projects/{key}/image`             | Set the image (`LEAD`/`ADMIN`)   |
| `DELETE` | `/api/projects/{key}/image`             | Remove the image                 |
| `GET`    | `/api/projects/{key}/members`           | List members                     |
| `POST`   | `/api/projects/{key}/members`           | Add / change a member's role     |
| `DELETE` | `/api/projects/{key}/members/{userId}`  | Remove a member                  |
| `GET`    | `/api/projects/{key}/tickets`           | Search tickets (`status`, `assigneeId`, `q`, `archived`, `page`, `size`) |
| `POST`   | `/api/projects/{key}/tickets`           | Create a ticket                  |
| `GET`    | `/api/projects/{key}/epics`             | Epics of a project (epic picker)  |
| `GET`    | `/api/tickets/{ticketKey}/history`      | Who moved it between buckets, and when |
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
| `POST`   | `/api/tickets/{ticketKey}/archive`      | Archive a finished ticket        |
| `POST`   | `/api/tickets/{ticketKey}/restore`      | Bring it back from the archive   |
| `DELETE` | `/api/tickets/{ticketKey}`              | Delete a ticket (`ADMIN` only)   |
| `GET`    | `/api/tickets/{ticketKey}/comments`     | List comments                    |
| `POST`   | `/api/tickets/{ticketKey}/comments`     | Add a comment                    |
| `PUT`    | `/api/comments/{id}`                    | Edit a comment                   |
| `DELETE` | `/api/comments/{id}`                    | Delete a comment                 |
| `GET`    | `/api/tickets/{ticketKey}/attachments`  | List attached documents          |
| `POST`   | `/api/tickets/{ticketKey}/attachments`  | Attach a document (multipart `file`) |
| `GET`    | `/api/attachments/{id}`                 | Download one (forced `attachment`) |
| `DELETE` | `/api/attachments/{id}`                 | Remove an attachment             |

Errors come back as RFC 7807 `ProblemDetail` documents.

Paged endpoints serialise through Spring Data's `PagedModel`
(`@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`), giving a stable shape:

```json
{ "content": [ ... ], "page": { "size": 50, "number": 0, "totalElements": 3, "totalPages": 1 } }
```

Returning `PageImpl` directly would serialise a Spring-internal class whose JSON carries no
stability guarantee, and logs a warning on every paged request.

## UI

- **Projects** — grid of the projects you can see, with **Active** and **Archived** tabs and a
  create dialog that suggests a key from the name.
- **Board** — one column per swim lane, in the project's own order, with drag-and-drop
  transitions that update optimistically and roll back if the server rejects the move. Toggle to
  a table view or the **Archived** tab; filter by assignee or search by title/key.
- **Ticket** — inline title/description editing, status/type/priority/assignee/epic/points/due
  date in the sidebar, status history, linked tickets, and threaded comments.
- **Settings** — project details, the **board** (rename, reorder, add and remove lanes),
  the project image, membership and roles, and project deletion.
- **Users** (admins only, in the top nav) — the admin dashboard: create accounts, edit
  name/email/role, reset passwords, and enable/disable accounts, with search and a
  "show disabled" filter.
- **Templates** (admins only) — define the boards new projects start from, and the tickets
  they start with.
- **Branding** (admins only) — company name and logo for the title bar.

Light and dark themes follow the OS preference.

## Tests and builds

```bash
./mvnw test                    # whole reactor
cd backend  && mvn test        # boots the app on in-memory H2 and exercises the REST API
cd frontend && npm run build   # type-check + production bundle
```

69 backend tests across eleven suites: core API (ticket-key numbering, transitions, comments),
user administration (including immediate revocation on disable and the lockout guards),
self-service password change, project assignment and the visibility rule it enforces, project
leads (several per project, several projects per lead, leads staffing their own project, and
last-lead protection), ticket linking (inverse relationships, cross-project links,
duplicate/self-link rejection, visibility filtering), and epics (one epic per ticket, children
listing, adding/removing from the epic side, same-project enforcement on both the picker and the
API, and release-on-delete), and status history (who/from/to/when, both entry points, no entry
for a non-move, and visibility), and archiving (the DONE gate, the all-children rule for epics,
tab separation, frozen edits, and restore ordering), project archiving (tab separation,
freezing, read-after-archive, restore, and delete-while-archived), and a suite dedicated to the
read-only rule on the routes that bypass the write guard.

Suites that depend on "the first registered account becomes ADMIN" run against their own
in-memory database so they do not depend on execution order.

The automated suite runs on H2. The MySQL profile has been exercised by hand against a live
server: all four migrations validate, and login, per-project ticket numbering, epics, links and
paged responses all behave the same as on H2.
