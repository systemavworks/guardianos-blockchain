import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Plus, RefreshCw, AlertTriangle, ChevronRight } from 'lucide-react'
import { blockchainApi, type BlockchainStats } from '../services/api'
import { cn } from '../utils/cn'

const RISK_STYLES: Record<string, string> = {
  CRITICAL: 'bg-red-900/40 text-red-400 border border-red-700/50',
  HIGH:     'bg-orange-900/40 text-orange-400 border border-orange-700/50',
  MEDIUM:   'bg-yellow-900/40 text-yellow-400 border border-yellow-700/50',
  LOW:      'bg-blue-900/40 text-blue-400 border border-blue-700/50',
  INFO:     'bg-emerald-900/40 text-emerald-400 border border-emerald-700/50',
}

const SCORE_COLOR: Record<string, string> = {
  CRITICAL: 'text-red-400',
  HIGH:     'text-orange-400',
  MEDIUM:   'text-yellow-400',
  LOW:      'text-blue-400',
  INFO:     'text-emerald-400',
}

const STATUS_DOT: Record<string, string> = {
  pending:   'bg-gray-400',
  running:   'bg-yellow-400 animate-pulse',
  completed: 'bg-emerald-400',
  failed:    'bg-red-400',
}

function formatDate(iso: string) {  return new Date(iso).toLocaleDateString('es-ES', {
    year: 'numeric', month: 'short', day: '2-digit',
  })
}

function shortAddress(addr: string) {
  return `${addr.slice(0, 6)}…${addr.slice(-4)}`
}

function StatsBanner({ stats }: { stats: BlockchainStats }) {
  const items = [
    { label: 'Total',      value: stats.totalAudits,     color: 'text-cyber-text' },
    { label: 'Completadas',value: stats.completedAudits, color: 'text-emerald-400' },
    { label: 'En curso',   value: stats.runningAudits,   color: 'text-yellow-400' },
    { label: 'Fallidas',   value: stats.failedAudits,    color: 'text-red-400' },
    { label: 'Score medio',value: stats.avgScore != null ? stats.avgScore.toFixed(0) : '—', color: 'text-cyber-accent' },
  ]
  return (
    <div className="grid grid-cols-5 gap-3 mb-6">
      {items.map((item) => (
        <div key={item.label} className="rounded-lg bg-cyber-surface border border-cyber-border px-4 py-3 text-center">
          <p className={cn('text-2xl font-bold', item.color)}>{item.value}</p>
          <p className="text-xs text-cyber-text/40 mt-0.5">{item.label}</p>
        </div>
      ))}
    </div>
  )
}

export default function BlockchainList() {
  const { data: audits, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: ['blockchain-audits'],
    queryFn: blockchainApi.listAudits,
    refetchInterval: (query) => {
      const hasRunning = query.state.data?.some((a) => a.status === 'running' || a.status === 'pending')
      return hasRunning ? 5000 : false
    },
  })

  const { data: stats } = useQuery({
    queryKey: ['blockchain-stats'],
    queryFn: blockchainApi.getStats,
    staleTime: 30_000,
  })

  return (
    <div className="max-w-5xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-cyber-text">Auditorías blockchain</h1>
          <p className="text-cyber-text/50 text-sm mt-1">Análisis de contratos EVM</p>
        </div>
        <div className="flex gap-3">
          <button
            onClick={() => refetch()}
            disabled={isFetching}
            className="flex items-center gap-2 px-3 py-2 rounded-md border border-cyber-border text-cyber-text/60 hover:text-cyber-text hover:border-cyber-accent/50 transition-colors text-sm"
          >
            <RefreshCw size={15} className={cn(isFetching && 'animate-spin')} />
            Actualizar
          </button>
          <Link
            to="/new"
            className="flex items-center gap-2 px-4 py-2 rounded-md bg-cyber-accent text-cyber-bg font-medium text-sm hover:bg-cyber-accent/80 transition-colors"
          >
            <Plus size={16} />
            Nueva auditoría
          </Link>
        </div>
      </div>

      {/* Stats banner */}
      {stats && stats.totalAudits > 0 && <StatsBanner stats={stats} />}

      {/* States */}
      {isLoading && (
        <div className="text-center py-20 text-cyber-text/40">Cargando auditorías…</div>
      )}

      {isError && (
        <div className="flex items-center gap-3 p-4 rounded-lg bg-red-900/20 border border-red-700/40 text-red-400">
          <AlertTriangle size={18} />
          No se pudieron cargar las auditorías. Verifica la conexión con el servidor.
        </div>
      )}

      {!isLoading && !isError && audits?.length === 0 && (
        <div className="text-center py-20">
          <p className="text-cyber-text/40 mb-4">Aún no has realizado ninguna auditoría</p>
          <Link
            to="/new"
            className="inline-flex items-center gap-2 px-4 py-2 rounded-md bg-cyber-accent/10 border border-cyber-accent/30 text-cyber-accent text-sm hover:bg-cyber-accent/20 transition-colors"
          >
            <Plus size={16} />
            Crear primera auditoría
          </Link>
        </div>
      )}

      {/* Table */}
      {audits && audits.length > 0 && (
        <div className="rounded-xl border border-cyber-border overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-cyber-surface border-b border-cyber-border">
                <th className="px-4 py-3 text-left text-cyber-text/50 font-medium">Contrato</th>
                <th className="px-4 py-3 text-left text-cyber-text/50 font-medium">Red</th>
                <th className="px-4 py-3 text-left text-cyber-text/50 font-medium">Token</th>
                <th className="px-4 py-3 text-center text-cyber-text/50 font-medium">Score</th>
                <th className="px-4 py-3 text-center text-cyber-text/50 font-medium">Riesgo</th>
                <th className="px-4 py-3 text-left text-cyber-text/50 font-medium">Fecha</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {audits.map((audit) => (
                <tr
                  key={audit.id}
                  className="border-b border-cyber-border/50 hover:bg-white/2 transition-colors"
                >
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <span className={cn('w-2 h-2 rounded-full flex-shrink-0', STATUS_DOT[audit.status])} />
                      <div>
                        {audit.label && (
                          <p className="text-cyber-text font-medium">{audit.label}</p>
                        )}
                        <p className={cn('font-mono text-xs', audit.label ? 'text-cyber-text/40' : 'text-cyber-text')}>
                          {shortAddress(audit.address)}
                        </p>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-cyber-text/70 capitalize">{audit.chainId}</td>
                  <td className="px-4 py-3">
                    {audit.tokenName ? (
                      <span className="text-cyber-text">
                        {audit.tokenName}
                        {audit.tokenSymbol && (
                          <span className="text-cyber-text/40 ml-1">({audit.tokenSymbol})</span>
                        )}
                      </span>
                    ) : (
                      <span className="text-cyber-text/30">—</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-center">
                    {audit.overallScore != null ? (
                      <span className={cn('font-bold text-base', audit.riskLevel ? SCORE_COLOR[audit.riskLevel] : 'text-cyber-text')}>
                        {audit.overallScore}
                      </span>
                    ) : (
                      <span className="text-cyber-text/30 text-xs">
                        {audit.status === 'running' ? '…' : '—'}
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-center">
                    {audit.riskLevel ? (
                      <span className={cn('px-2 py-0.5 rounded text-xs font-medium', RISK_STYLES[audit.riskLevel])}>
                        {audit.riskLevel}
                      </span>
                    ) : (
                      <span className="text-cyber-text/30 text-xs">
                        {audit.status === 'failed' ? 'Error' : '—'}
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-cyber-text/50 text-xs">{formatDate(audit.createdAt)}</td>
                  <td className="px-4 py-3">
                    <Link
                      to={`/audit/${audit.id}`}
                      className="flex items-center justify-end gap-1 text-cyber-accent/60 hover:text-cyber-accent transition-colors"
                    >
                      Ver <ChevronRight size={14} />
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
