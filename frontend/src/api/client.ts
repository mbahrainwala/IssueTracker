import type {
  AuthResponse,
  Comment,
  EpicRef,
  LinkType,
  LinkedTicket,
  Member,
  Page,
  Project,
  ProjectAssignment,
  ProjectRole,
  Role,
  StatusChange,
  Ticket,
  TicketLink,
  TicketPriority,
  TicketStatus,
  TicketType,
  User,
} from './types'

const TOKEN_KEY = 'issuetracker.token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly fieldErrors?: Record<string, string>,
  ) {
    super(message)
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getToken()
  const headers = new Headers(init.headers)
  if (init.body) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(`/api${path}`, { ...init, headers })

  if (response.status === 204) return undefined as T

  const text = await response.text()
  const payload = text ? JSON.parse(text) : null

  if (!response.ok) {
    // Spring returns RFC 7807 ProblemDetail; `detail` carries the human-readable reason.
    const message = payload?.detail ?? payload?.message ?? response.statusText
    throw new ApiError(response.status, message, payload?.errors)
  }
  return payload as T
}

function query(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  }
  const qs = search.toString()
  return qs ? `?${qs}` : ''
}

export interface TicketPatch {
  title?: string
  description?: string
  type?: TicketType
  status?: TicketStatus
  priority?: TicketPriority
  assigneeId?: number | null
  clearAssignee?: boolean
  storyPoints?: number | null
  dueDate?: string | null
  epicKey?: string | null
  clearEpic?: boolean
}

export const api = {
  register: (body: { username: string; email: string; password: string; displayName: string }) =>
    request<AuthResponse>('/auth/register', { method: 'POST', body: JSON.stringify(body) }),

  login: (body: { usernameOrEmail: string; password: string }) =>
    request<AuthResponse>('/auth/login', { method: 'POST', body: JSON.stringify(body) }),

  me: () => request<User>('/auth/me'),

  /** The caller's own project assignments — no admin rights needed. */
  myProjects: () => request<ProjectAssignment[]>('/auth/me/projects'),

  /** Self-service: any signed-in user, own account only. */
  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>('/auth/change-password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword }),
    }),

  listUsers: () => request<User[]>('/users'),

  // --- admin dashboard (ADMIN only; the API returns 403 for everyone else) ---
  adminListUsers: () => request<User[]>('/admin/users'),

  adminCreateUser: (body: {
    username: string
    email: string
    password: string
    displayName: string
    role: Role
  }) => request<User>('/admin/users', { method: 'POST', body: JSON.stringify(body) }),

  adminUpdateUser: (
    id: number,
    body: { email: string; displayName: string; role: Role; enabled: boolean },
  ) => request<User>(`/admin/users/${id}`, { method: 'PUT', body: JSON.stringify(body) }),

  adminSetUserEnabled: (id: number, enabled: boolean) =>
    request<User>(`/admin/users/${id}/enabled${query({ enabled: String(enabled) })}`, { method: 'PATCH' }),

  adminResetPassword: (id: number, password: string) =>
    request<void>(`/admin/users/${id}/password`, { method: 'PUT', body: JSON.stringify({ password }) }),

  adminUserProjects: (id: number) => request<ProjectAssignment[]>(`/admin/users/${id}/projects`),

  adminSetUserProjects: (id: number, assignments: { projectKey: string; projectRole: ProjectRole }[]) =>
    request<ProjectAssignment[]>(`/admin/users/${id}/projects`, {
      method: 'PUT',
      body: JSON.stringify({ assignments }),
    }),

  // --- ticket links ---
  searchTickets: (q: string, exclude?: string) =>
    request<LinkedTicket[]>(`/tickets/search${query({ q, exclude })}`),

  listLinks: (ticketKey: string) => request<TicketLink[]>(`/tickets/${ticketKey}/links`),

  addLink: (ticketKey: string, linkType: LinkType, targetTicketKey: string) =>
    request<TicketLink[]>(`/tickets/${ticketKey}/links`, {
      method: 'POST',
      body: JSON.stringify({ linkType, targetTicketKey }),
    }),

  deleteLink: (linkId: number) => request<void>(`/links/${linkId}`, { method: 'DELETE' }),

  listProjects: () => request<Project[]>('/projects'),

  getProject: (key: string) => request<Project>(`/projects/${key}`),

  createProject: (body: {
    projectKey: string
    name: string
    description?: string
    additionalLeadIds?: number[]
  }) => request<Project>('/projects', { method: 'POST', body: JSON.stringify(body) }),

  /** Leadership is managed through the members endpoints, not here. */
  updateProject: (key: string, body: { name: string; description?: string }) =>
    request<Project>(`/projects/${key}`, { method: 'PUT', body: JSON.stringify(body) }),

  deleteProject: (key: string) => request<void>(`/projects/${key}`, { method: 'DELETE' }),

  listMembers: (key: string) => request<Member[]>(`/projects/${key}/members`),

  addMember: (key: string, body: { userId: number; projectRole: ProjectRole }) =>
    request<Member>(`/projects/${key}/members`, { method: 'POST', body: JSON.stringify(body) }),

  removeMember: (key: string, userId: number) =>
    request<void>(`/projects/${key}/members/${userId}`, { method: 'DELETE' }),

  listTickets: (
    key: string,
    filters: { status?: TicketStatus | ''; assigneeId?: number | ''; q?: string; page?: number; size?: number } = {},
  ) => request<Page<Ticket>>(`/projects/${key}/tickets${query(filters)}`),

  createTicket: (
    key: string,
    body: {
      title: string
      description?: string
      type?: TicketType
      status?: TicketStatus
      priority?: TicketPriority
      assigneeId?: number | null
      storyPoints?: number | null
      dueDate?: string | null
      epicKey?: string | null
    },
  ) => request<Ticket>(`/projects/${key}/tickets`, { method: 'POST', body: JSON.stringify(body) }),

  listEpics: (projectKey: string) => request<EpicRef[]>(`/projects/${projectKey}/epics`),

  listEpicChildren: (ticketKey: string) => request<Ticket[]>(`/tickets/${ticketKey}/children`),

  /** Tickets that may be added to this epic — same project only, epics excluded. */
  listEpicCandidates: (ticketKey: string, q?: string) =>
    request<Ticket[]>(`/tickets/${ticketKey}/candidates${query({ q })}`),

  addEpicChildren: (ticketKey: string, ticketKeys: string[]) =>
    request<Ticket[]>(`/tickets/${ticketKey}/children`, {
      method: 'POST',
      body: JSON.stringify({ ticketKeys }),
    }),

  removeEpicChild: (ticketKey: string, childKey: string) =>
    request<Ticket[]>(`/tickets/${ticketKey}/children/${childKey}`, { method: 'DELETE' }),

  getTicket: (ticketKey: string) => request<Ticket>(`/tickets/${ticketKey}`),

  updateTicket: (ticketKey: string, body: TicketPatch) =>
    request<Ticket>(`/tickets/${ticketKey}`, { method: 'PATCH', body: JSON.stringify(body) }),

  setTicketStatus: (ticketKey: string, status: TicketStatus) =>
    request<Ticket>(`/tickets/${ticketKey}/status${query({ status })}`, { method: 'PATCH' }),

  deleteTicket: (ticketKey: string) => request<void>(`/tickets/${ticketKey}`, { method: 'DELETE' }),

  /** Who moved this ticket between buckets, oldest first. */
  listStatusHistory: (ticketKey: string) => request<StatusChange[]>(`/tickets/${ticketKey}/history`),

  listComments: (ticketKey: string) => request<Comment[]>(`/tickets/${ticketKey}/comments`),

  addComment: (ticketKey: string, body: string) =>
    request<Comment>(`/tickets/${ticketKey}/comments`, { method: 'POST', body: JSON.stringify({ body }) }),

  updateComment: (commentId: number, body: string) =>
    request<Comment>(`/comments/${commentId}`, { method: 'PUT', body: JSON.stringify({ body }) }),

  deleteComment: (commentId: number) => request<void>(`/comments/${commentId}`, { method: 'DELETE' }),
}
