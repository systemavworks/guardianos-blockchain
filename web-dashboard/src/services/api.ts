import axios from 'axios'

const AUDIT_DOMAIN = import.meta.env.VITE_AUDIT_URL ?? 'https://audit.guardianos.es'
const API_BASE     = import.meta.env.VITE_API_URL   ?? '/api/v1'

export const api = axios.create({
  baseURL: API_BASE,
  headers: { 'Content-Type': 'application/json' },
})

// Inyectar JWT en cada request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('bc_jwt')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 401 → redirigir a guardianos-audit para re-autenticar
api.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('bc_jwt')
      window.location.href = `${AUDIT_DOMAIN}/login`
    }
    return Promise.reject(error)
  }
)

// ── Tipos ──────────────────────────────────────────────────────────────────

export type RiskLevel = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO'

export interface AuditListItem {
  id: string
  targetId: string
  address: string
  chainId: string
  label: string | null
  status: 'pending' | 'running' | 'completed' | 'failed'
  overallScore: number
  riskLevel: string
  tokenName: string | null
  tokenSymbol: string | null
  findingsCount: number
  createdAt: string
  completedAt: string | null
}

export interface BlockchainFinding {
  id: string
  risk: string
  title: string
  description: string
  recommendation: string
  evidence: string | null
  category: string
}

export interface BlockchainReport {
  id: string
  tenantId: string
  targetId: string
  address: string
  chainId: string
  label: string | null
  status: 'pending' | 'running' | 'completed' | 'failed'
  overallScore: number
  riskLevel: string
  tokenName: string | null
  tokenSymbol: string | null
  totalSupply: string | null
  holderCount: string | null
  findings: BlockchainFinding[]
  findingsCount: number
  startedAt: string | null
  completedAt: string | null
  createdAt: string
}

// ── Endpoints ──────────────────────────────────────────────────────────────

export interface RiskDistribution {
  CRITICAL: number
  HIGH: number
  MEDIUM: number
  LOW: number
  INFO: number
}

export interface BlockchainStats {
  totalAudits: number
  completedAudits: number
  failedAudits: number
  runningAudits: number
  avgScore: number | null
  riskDistribution: RiskDistribution
  chainDistribution: Record<string, number>
}

// ── Llamadas API ───────────────────────────────────────────────────────────

export const blockchainApi = {
  listAudits: () =>
    api.get<AuditListItem[]>('/blockchain/audits').then((r) => r.data),

  createAudit: (body: { address: string; chainId: string; label?: string }) =>
    api.post<{ id: string; status: string }>('/blockchain/audits', body).then((r) => r.data),

  getReport: (reportId: string) =>
    api.get<BlockchainReport>(`/blockchain/audits/${reportId}`).then((r) => r.data),

  reaudit: (reportId: string) =>
    api.post<{ reportId: string; status: string }>(`/blockchain/audits/${reportId}/reaudit`).then((r) => r.data),

  getStats: () =>
    api.get<BlockchainStats>('/blockchain/stats').then((r) => r.data),
}
