export type Role = 'ADMIN' | 'USER'
export type ProjectRole = 'LEAD' | 'MEMBER' | 'VIEWER'
export type TicketType = 'STORY' | 'TASK' | 'BUG' | 'EPIC'
export type TicketPriority = 'LOWEST' | 'LOW' | 'MEDIUM' | 'HIGH' | 'HIGHEST'
/**
 * A swim lane's name, which is also the value a ticket carries. Lanes are configured per
 * project from a template, so this is an open string rather than a fixed union - the set of
 * valid values lives on the project, in `Project.lanes`.
 */
export type TicketStatus = string

export const TICKET_TYPES: TicketType[] = ['STORY', 'TASK', 'BUG', 'EPIC']
export const TICKET_PRIORITIES: TicketPriority[] = ['LOWEST', 'LOW', 'MEDIUM', 'HIGH', 'HIGHEST']
export const PROJECT_ROLES: ProjectRole[] = ['LEAD', 'MEMBER', 'VIEWER']


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
  /** The project's swim lanes, left to right. */
  lanes: Lane[]
  /** The template this board started from, for display. */
  templateName: string | null
  /** Optional project picture; false when none has been uploaded. */
  hasImage: boolean
  /** Last-updated stamp, appended to the image URL so a replacement is not served stale. */
  imageVersion: number | null
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

/** One swim lane on a project's board, or in a template. */
export interface Lane {
  id: number
  name: string
  order: number
  /** Where newly created tickets land. Exactly one lane per board. */
  initial: boolean
  /** Finished work: the only lane tickets can be archived from. Exactly one per board. */
  done: boolean
}

/** A ticket every project made from a template starts with. */
export interface StarterTicket {
  id: number
  title: string
  description: string | null
  type: TicketType
  priority: TicketPriority
  /** Which lane it lands in; null means the board's starting lane. */
  lane: string | null
}

/** A reusable board blueprint. Admins define them; anyone can start a project from one. */
export interface Template {
  id: number
  name: string
  description: string | null
  /** Ships with the app: editable, but not deletable. */
  builtIn: boolean
  lanes: Lane[]
  /** The work every project of this kind begins with. May be empty. */
  starterTickets: StarterTicket[]
  createdAt: string
}

/** Somebody named you with @username and is waiting for you to acknowledge it. */
export interface Mention {
  id: number
  ticketKey: string
  projectKey: string
  ticketTitle: string
  mentionedBy: User
  /** The comment (or description) you were named in, truncated. */
  excerpt: string | null
  mentionedAt: string
}

export interface Branding {
  /** Null when no company name has been set; the app falls back to its own title. */
  companyName: string | null
  hasLogo: boolean
  /** Last-updated stamp, appended to the logo URL so a replacement is not served from cache. */
  logoVersion: number | null
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
