export type Role = 'ADMIN' | 'USER'
export type ProjectRole = 'LEAD' | 'MEMBER' | 'VIEWER'
export type TicketType = 'STORY' | 'TASK' | 'BUG' | 'EPIC'
export type TicketPriority = 'LOWEST' | 'LOW' | 'MEDIUM' | 'HIGH' | 'HIGHEST'
export type TicketStatus = 'BACKLOG' | 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE'

export const TICKET_STATUSES: TicketStatus[] = ['BACKLOG', 'TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE']
export const TICKET_TYPES: TicketType[] = ['STORY', 'TASK', 'BUG', 'EPIC']
export const TICKET_PRIORITIES: TicketPriority[] = ['LOWEST', 'LOW', 'MEDIUM', 'HIGH', 'HIGHEST']
export const PROJECT_ROLES: ProjectRole[] = ['LEAD', 'MEMBER', 'VIEWER']

export const STATUS_LABELS: Record<TicketStatus, string> = {
  BACKLOG: 'Backlog',
  TODO: 'To Do',
  IN_PROGRESS: 'In Progress',
  IN_REVIEW: 'In Review',
  DONE: 'Done',
}

export interface User {
  id: number
  username: string
  email: string
  displayName: string
  role: Role
  enabled: boolean
}

export const ROLES: Role[] = ['ADMIN', 'USER']

export interface Project {
  id: number
  projectKey: string
  name: string
  description: string | null
  /** A project can have several leads; leadership is a membership role. */
  leads: User[]
  ticketCount: number
  archived: boolean
  archivedAt: string | null
  archivedBy: User | null
  createdAt: string
}

export interface Member {
  user: User
  projectRole: ProjectRole
}

export interface Ticket {
  id: number
  ticketKey: string
  projectId: number
  projectKey: string
  title: string
  description: string | null
  type: TicketType
  status: TicketStatus
  priority: TicketPriority
  reporter: User
  assignee: User | null
  /** The epic this ticket sits under, if any. A ticket has at most one. */
  epic: EpicRef | null
  archived: boolean
  archivedAt: string | null
  archivedBy: User | null
  storyPoints: number | null
  dueDate: string | null
  createdAt: string
  updatedAt: string
}

export interface Comment {
  id: number
  author: User
  body: string
  createdAt: string
  updatedAt: string
}

export type LinkType =
  | 'RELATES_TO'
  | 'BLOCKS'
  | 'IS_BLOCKED_BY'
  | 'DUPLICATES'
  | 'IS_DUPLICATED_BY'
  | 'CAUSES'
  | 'IS_CAUSED_BY'

/** Label shown when picking a link type: "this ticket <label> …". */
export const LINK_TYPES: { value: LinkType; label: string }[] = [
  { value: 'RELATES_TO', label: 'relates to' },
  { value: 'BLOCKS', label: 'blocks' },
  { value: 'IS_BLOCKED_BY', label: 'is blocked by' },
  { value: 'DUPLICATES', label: 'duplicates' },
  { value: 'IS_DUPLICATED_BY', label: 'is duplicated by' },
  { value: 'CAUSES', label: 'causes' },
  { value: 'IS_CAUSED_BY', label: 'is caused by' },
]

export interface LinkedTicket {
  id: number
  ticketKey: string
  projectKey: string
  title: string
  type: TicketType
  status: TicketStatus
  priority: TicketPriority
  assignee: User | null
}

export interface TicketLink {
  id: number
  linkType: LinkType
  label: string
  ticket: LinkedTicket
}

export interface ProjectAssignment {
  projectId: number
  projectKey: string
  projectName: string
  projectRole: ProjectRole
}

/** One recorded move of a ticket between status buckets. */
export interface StatusChange {
  id: number
  fromStatus: TicketStatus
  toStatus: TicketStatus
  movedBy: User
  movedAt: string
  /** Server-rendered sentence, e.g. "moved from Backlog to To Do by Alice Nguyen". */
  summary: string
}

export interface Attachment {
  id: number
  filename: string
  contentType: string
  sizeBytes: number
  uploadedBy: User
  uploadedAt: string
}

export interface EpicRef {
  id: number
  ticketKey: string
  title: string
}

/** Matches Spring Data's PagedModel DTO (pageSerializationMode = VIA_DTO). */
export interface Page<T> {
  content: T[]
  page: {
    size: number
    number: number
    totalElements: number
    totalPages: number
  }
}

export interface AuthResponse {
  token: string
  expiresInSeconds: number
  user: User
}
