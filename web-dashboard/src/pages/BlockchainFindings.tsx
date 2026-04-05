import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Loader2, AlertCircle, Filter } from 'lucide-react'
import { blockchainApi, type BlockchainFinding, type RiskLevel } from '../services/api'
import { cn } from '../utils/cn'

const RISK_ORDER: RiskLevel[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO']

const RISK_LABEL: Record<string, string> = {
  CRITICAL: 'CRÍTICO',
  HIGH:     'ALTO',
  MEDIUM:   'MEDIO',
  LOW:      'BAJO',
  INFO:     'INFO',
}

const RISK_STYLES: Record<string, string> = {
  CRITICAL: 'bg-red-900/40 text-red-400 border border-red-700/50',
  HIGH:     'bg-orange-900/40 text-orange-400 border border-orange-700/50',
  MEDIUM:   'bg-yellow-900/40 text-yellow-400 border border-yellow-700/50',
  LOW:      'bg-blue-900/40 text-blue-400 border border-blue-700/50',
  INFO:     'bg-emerald-900/40 text-emerald-400 border border-emerald-700/50',
}

const CATEGORY_LABELS: Record<string, string> = {
  'HONEYPOT':     'Honeypot',
  'TAXATION':     'Impuestos',
  'OWNERSHIP':    'Propiedad',
  'TRADING':      'Restricciones',
  'CODE_QUALITY': 'Código',
  'VERIFICATION': 'Verificación',
  'PROXY':        'Proxy',
  'CONCENTRATION':'Concentración',
  'LIQUIDITY':    'Liquidez',
  'OTHER':        'Otros',
  'AGE':          'Antigüedad',
}

function FindingCard({ finding }: { finding: BlockchainFinding }) {
  const [expanded, setExpanded] = useState(false)

  return (
    <div className={cn(
      'rounded-lg border transition-colors overflow-hidden',
      finding.risk === 'CRITICAL' ? 'border-red-700/40 bg-red-950/20' :
      finding.risk === 'HIGH'     ? 'border-orange-700/40 bg-orange-950/20' :
      finding.risk === 'MEDIUM'   ? 'border-yellow-700/40 bg-yellow-950/20' :
      finding.risk === 'LOW'      ? 'border-blue-700/40 bg-blue-950/20' :
                                    'border-cyber-border bg-cyber-surface/50'
    )}>
      <button
        type="button"
        className="w-full text-left px-4 py-3 flex items-start gap-3"
        onClick={() => setExpanded((v) => !v)}
      >
        <span className={cn('px-2 py-0.5 rounded text-xs font-bold flex-shrink-0 mt-0.5', RISK_STYLES[finding.risk])}>
          {RISK_LABEL[finding.risk]}
        </span>
        <div className="flex-1 min-w-0">
          <p className="text-cyber-text font-medium text-sm">{finding.title}</p>
          <div className="flex items-center gap-2 mt-0.5">
            <span className="text-xs text-cyber-text/30 font-mono">{finding.id}</span>
            <span className="text-xs text-cyber-text/30">
              {CATEGORY_LABELS[finding.category] ?? finding.category}
            </span>
          </div>
        </div>
        <span className="text-cyber-text/30 text-xs mt-1">{expanded ? '▲' : '▼'}</span>
      </button>

      {expanded && (
        <div className="px-4 pb-4 border-t border-cyber-border/30 pt-3 space-y-3">
          <p className="text-cyber-text/70 text-sm leading-relaxed">{finding.description}</p>

          {finding.evidence && (
            <div>
              <p className="text-xs text-cyber-text/30 font-medium mb-1">Evidencia</p>
              <code className="block text-xs font-mono text-cyber-text/60 bg-black/30 rounded px-3 py-2 break-all">
                {finding.evidence}
              </code>
            </div>
          )}

          <div className="p-3 rounded-lg bg-cyber-accent/5 border border-cyber-accent/20">
            <p className="text-xs text-cyber-text/40 font-medium mb-1">Recomendación</p>
            <p className="text-sm text-cyber-accent/80">{finding.recommendation}</p>
          </div>
        </div>
      )}
    </div>
  )
}

export default function BlockchainFindings() {
  const { id } = useParams<{ id: string }>()
  const [activeFilter, setActiveFilter] = useState<RiskLevel | 'ALL'>('ALL')

  const { data: report, isLoading, isError } = useQuery({
    queryKey: ['blockchain-report', id],
    queryFn: () => blockchainApi.getReport(id!),
    enabled: !!id,
  })

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64 gap-3 text-cyber-text/40">
        <Loader2 size={20} className="animate-spin" />
        Cargando hallazgos…
      </div>
    )
  }

  if (isError || !report) {
    return (
      <div className="flex items-center gap-3 p-4 rounded-lg bg-red-900/20 border border-red-700/40 text-red-400">
        <AlertCircle size={18} />
        No se pudo cargar el reporte.
      </div>
    )
  }

  const filteredFindings = activeFilter === 'ALL'
    ? report.findings
    : report.findings.filter((f) => f.risk === activeFilter)

  const countByRisk = RISK_ORDER.reduce((acc, r) => {
    acc[r] = report.findings.filter((f) => f.risk === r).length
    return acc
  }, {} as Record<RiskLevel, number>)

  return (
    <div className="max-w-3xl mx-auto">
      {/* Header */}
      <Link
        to={`/audit/${id}`}
        className="inline-flex items-center gap-2 text-sm text-cyber-text/50 hover:text-cyber-text mb-6 transition-colors"
      >
        <ArrowLeft size={16} /> Volver al reporte
      </Link>

      <div className="flex items-center justify-between mb-1">
        <h1 className="text-2xl font-bold text-cyber-text">Hallazgos</h1>
        <span className="text-cyber-text/40 text-sm">{report.findings.length} total</span>
      </div>
      {report.label && (
        <p className="text-cyber-text/50 text-sm mb-6">{report.label} — {report.address.slice(0, 10)}…</p>
      )}

      {/* Filters */}
      <div className="flex items-center gap-2 mb-6 flex-wrap">
        <Filter size={14} className="text-cyber-text/30" />
        <button
          onClick={() => setActiveFilter('ALL')}
          className={cn(
            'px-3 py-1 rounded-md text-xs transition-colors border',
            activeFilter === 'ALL'
              ? 'bg-cyber-accent/10 border-cyber-accent/50 text-cyber-accent'
              : 'border-cyber-border text-cyber-text/50 hover:text-cyber-text'
          )}
        >
          Todos ({report.findings.length})
        </button>
        {RISK_ORDER.map((risk) => {
          const count = countByRisk[risk]
          if (!count) return null
          return (
            <button
              key={risk}
              onClick={() => setActiveFilter(risk)}
              className={cn(
                'px-3 py-1 rounded-md text-xs transition-colors',
                activeFilter === risk
                  ? RISK_STYLES[risk]
                  : 'border border-cyber-border text-cyber-text/40 hover:text-cyber-text'
              )}
            >
              {RISK_LABEL[risk]} ({count})
            </button>
          )
        })}
      </div>

      {/* List */}
      {filteredFindings.length === 0 ? (
        <div className="text-center py-12 text-cyber-text/30">
          No hay hallazgos con este filtro
        </div>
      ) : (
        <div className="space-y-2">
          {filteredFindings.map((finding) => (
            <FindingCard key={finding.id + finding.title} finding={finding} />
          ))}
        </div>
      )}
    </div>
  )
}
