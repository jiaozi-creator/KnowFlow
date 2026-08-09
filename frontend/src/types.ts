export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface UserView {
  id: number
  email: string
  displayName: string
  tenantId: number
  organizationRole: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: UserView
}

export interface KnowledgeBase {
  id: number
  name: string
  description?: string
  visibility:
    | 'TENANT'
    | 'DEPARTMENT'
    | 'MEMBER'
    | 'PRIVATE'
  createdBy: number
  createdAt: string
  updatedAt: string
  departmentIds: number[]
  memberIds: number[]
}

export interface DocumentItem {
  id: number
  knowledgeBaseId: number
  name: string
  fileType: string
  status:
    | 'PROCESSING'
    | 'READY'
    | 'NEEDS_REINDEX'
    | 'FAILED'
  currentVersionId: number
  createdAt: string
  updatedAt: string
}

export interface IngestionTask {
  id: number
  status: string
  progress: number
  errorMessage?: string
  startedAt?: string
  finishedAt?: string
}

export interface IndexStatus {
  currentSignature: string
  total: number
  ready: number
  needsReindex: number
  processing: number
  failed: number
  repairable: number
}

export interface BatchReindexResponse {
  currentSignature: string
  queuedCount: number
  documentIds: number[]
  taskIds: number[]
}

export interface Conversation {
  id: number
  title: string
  createdAt: string
  updatedAt: string
}

export interface Citation {
  documentId: number
  chunkId?: number
  documentName?: string
  pageNumber?: number
  citationIndex: number
  excerpt: string
  similarity: number
}

export interface ChatMessage {
  id?: number
  role: 'USER' | 'ASSISTANT'
  content: string
  status?: string
  createdAt?: string
  citations?: Citation[]
}