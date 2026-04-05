import { useParams, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, List, Loader2, AlertCircle, CheckCircle, Copy, ExternalLink, RefreshCw, Download } from 'lucide-react'
import { blockchainApi, type BlockchainFinding, type RiskLevel } from '../services/api'
import { cn } from '../utils/cn'

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

const SCORE_BAR: Record<string, string> = {
  CRITICAL: 'bg-red-500',
  HIGH:     'bg-orange-500',
  MEDIUM:   'bg-yellow-500',
  LOW:      'bg-blue-500',
  INFO:     'bg-emerald-500',
}

const RISK_ORDER: RiskLevel[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO']

const EXPLORER_BASE: Record<string, string> = {
  ethereum: 'https://etherscan.io/address',
  bsc:      'https://bscscan.com/address',
  polygon:  'https://polygonscan.com/address',
  arbitrum: 'https://arbiscan.io/address',
  optimism: 'https://optimistic.etherscan.io/address',
  avalanche:'https://snowtrace.io/address',
}

function ScoreCircle({ score, risk }: { score: number; risk: string }) {
  const circumference = 2 * Math.PI * 36
  const progress = (score / 100) * circumference

  return (
    <div className="relative w-28 h-28 flex-shrink-0">
      <svg className="w-28 h-28 -rotate-90" viewBox="0 0 80 80">
        <circle cx="40" cy="40" r="36" fill="none" strokeWidth="7" className="stroke-cyber-border" />
        <circle
          cx="40" cy="40" r="36" fill="none" strokeWidth="7"
          strokeDasharray={`${progress} ${circumference}`}
          strokeLinecap="round"
          className={cn(
            risk === 'CRITICAL' ? 'stroke-red-500' :
            risk === 'HIGH'     ? 'stroke-orange-500' :
            risk === 'MEDIUM'   ? 'stroke-yellow-500' :
            risk === 'LOW'      ? 'stroke-blue-500' : 'stroke-emerald-500'
          )}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className={cn('text-3xl font-bold',
          risk === 'CRITICAL' ? 'text-red-400' :
          risk === 'HIGH'     ? 'text-orange-400' :
          risk === 'MEDIUM'   ? 'text-yellow-400' :
          risk === 'LOW'      ? 'text-blue-400' : 'text-emerald-400'
        )}>
          {score}
        </span>
        <span className="text-xs text-cyber-text/40">/ 100</span>
      </div>
    </div>
  )
}

function FindingRow({ finding }: { finding: BlockchainFinding }) {
  return (
    <div className="p-4 rounded-lg bg-cyber-surface border border-cyber-border/60">
      <div className="flex items-start justify-between gap-3 mb-2">
        <div className="flex items-center gap-2 flex-wrap">
          <span className={cn('px-2 py-0.5 rounded text-xs font-bold', RISK_STYLES[finding.risk])}>
            {RISK_LABEL[finding.risk]}
          </span>
          <span className="text-xs text-cyber-text/30 font-mono">{finding.id}</span>
          <span className="text-xs text-cyber-text/30">{finding.category}</span>
        </div>
      </div>
      <p className="text-cyber-text font-medium text-sm mb-1">{finding.title}</p>
      <p className="text-cyber-text/60 text-xs mb-2">{finding.description}</p>
      {finding.evidence && (
        <p className="text-cyber-text/40 text-xs font-mono bg-black/30 rounded px-2 py-1 mb-2 break-all">
          {finding.evidence}
        </p>
      )}
      <p className="text-xs text-cyber-accent/70">
        💡 {finding.recommendation}
      </p>
    </div>
  )
}

export default function BlockchainReport() {
  const { id } = useParams<{ id: string }>()
  const queryClient = useQueryClient()

  const { data: report, isLoading, isError } = useQuery({
    queryKey: ['blockchain-report', id],
    queryFn: () => blockchainApi.getReport(id!),
    enabled: !!id,
    refetchInterval: (query) => {
      const s = query.state.data?.status
      return s === 'running' || s === 'pending' ? 3000 : false
    },
  })

  const reauditMutation = useMutation({
    mutationFn: () => blockchainApi.reaudit(id!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['blockchain-report', id] })
      queryClient.invalidateQueries({ queryKey: ['blockchain-audits'] })
      queryClient.invalidateQueries({ queryKey: ['blockchain-stats'] })
    },
  })

  function copyAddress() {
    if (report?.address) navigator.clipboard.writeText(report.address)
  }

  function exportJson() {
    if (!report) return
    const blob = new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' })
    const url  = URL.createObjectURL(blob)
    const a    = document.createElement('a')
    a.href     = url
    a.download = `blockchain-audit-${report.address.slice(0, 10)}.json`
    a.click()
    URL.revokeObjectURL(url)
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64 gap-3 text-cyber-text/40">
        <Loader2 size={20} className="animate-spin" />
        Cargando reporte…
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

  // En progreso
  if (report.status === 'running' || report.status === 'pending') {
    return (
      <div className="max-w-xl mx-auto text-center py-24">
        <Loader2 size={36} className="animate-spin text-cyber-accent mx-auto mb-4" />
        <h2 className="text-xl font-bold text-cyber-text mb-2">Analizando contrato…</h2>
          <p className="text-cyber-text/40 text-sm font-mono mb-1">{report.address}</p>
        <p className="text-cyber-text/30 text-xs">
          Consultando GoPlus Security y Etherscan. Esto tarda ~15 segundos.
        </p>
      </div>
    )
  }

  // Fallido
  if (report.status === 'failed') {
    return (
      <div className="max-w-xl mx-auto">
        <Link to="/" className="inline-flex items-center gap-2 text-sm text-cyber-text/50 hover:text-cyber-text mb-6 transition-colors">
          <ArrowLeft size={16} /> Volver
        </Link>
        <div className="p-6 rounded-xl bg-red-900/10 border border-red-700/30">
          <AlertCircle size={24} className="text-red-400 mb-3" />
          <h2 className="text-lg font-bold text-red-400 mb-2">Auditoría fallida</h2>
          <p className="text-cyber-text/50 text-sm">{report.completedAt ? 'Análisis completado con errores.' : 'Error desconocido al analizar el contrato.'}</p>
        </div>
      </div>
    )
  }

  // Completado
  const findingsByRisk = RISK_ORDER.reduce((acc, risk) => {
    const items = report.findings.filter((f) => f.risk === risk)
    if (items.length) acc[risk] = items
    return acc
  }, {} as Partial<Record<RiskLevel, BlockchainFinding[]>>)

  const explorerUrl = EXPLORER_BASE[report.chainId]
    ? `${EXPLORER_BASE[report.chainId]}/${report.address}`
    : null

  return (
    <div className="max-w-3xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-6 flex-wrap gap-3">
        <Link to="/" className="inline-flex items-center gap-2 text-sm text-cyber-text/50 hover:text-cyber-text transition-colors">
          <ArrowLeft size={16} /> Auditorías
        </Link>
        <div className="flex gap-2">
          <button
            onClick={exportJson}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-md border border-cyber-border text-cyber-text/60 hover:text-cyber-text text-xs transition-colors"
          >
            <Download size={14} /> Exportar JSON
          </button>
          <button
            onClick={() => reauditMutation.mutate()}
            disabled={reauditMutation.isPending}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-md border border-cyber-accent/40 text-cyber-accent text-xs hover:bg-cyber-accent/10 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
          >
            <RefreshCw size={14} className={cn(reauditMutation.isPending && 'animate-spin')} />
            Re-auditar
          </button>
        </div>
      </div>

      {/* Hero card */}
      <div className="p-6 rounded-xl bg-cyber-surface border border-cyber-border mb-6">
        <div className="flex items-center gap-6 flex-wrap">
          {report.overallScore != null && report.riskLevel && (
            <ScoreCircle score={report.overallScore} risk={report.riskLevel} />
          )}
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-1 flex-wrap">
              {report.label && <h2 className="text-xl font-bold text-cyber-text">{report.label}</h2>}
              {report.riskLevel && (
                <span className={cn('px-2 py-0.5 rounded text-xs font-bold', RISK_STYLES[report.riskLevel])}>
                  {RISK_LABEL[report.riskLevel]}
                </span>
              )}
            </div>
            {report.tokenName && (
              <p className="text-cyber-accent text-sm mb-1">
                {report.tokenName}
                {report.tokenSymbol && <span className="text-cyber-text/40 ml-1">({report.tokenSymbol})</span>}
              </p>
            )}
            <div className="flex items-center gap-2 mt-1">
              <span className="font-mono text-xs text-cyber-text/50 truncate max-w-xs">{report.address}</span>
              <button onClick={copyAddress} className="text-cyber-text/30 hover:text-cyber-text/70 transition-colors flex-shrink-0">
                <Copy size={13} />
              </button>
              {explorerUrl && (
                <a href={explorerUrl} target="_blank" rel="noopener noreferrer"
                  className="text-cyber-text/30 hover:text-cyber-accent transition-colors flex-shrink-0">
                  <ExternalLink size={13} />
                </a>
              )}
            </div>
            <div className="flex items-center gap-4 mt-3 text-xs text-cyber-text/40">
              <span className="capitalize">{report.chainId}</span>
              {report.totalSupply && <span>{Number(report.totalSupply).toLocaleString()} supply</span>}
              {report.holderCount != null && <span>{report.holderCount.toLocaleString()} holders</span>}
              {report.completedAt && (
                <span className="flex items-center gap-1">
                  <CheckCircle size={11} className="text-emerald-500" />
                  {new Date(report.completedAt).toLocaleDateString('es-ES')}
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Score bar */}
        {report.overallScore != null && report.riskLevel && (
          <div className="mt-5">
            <div className="flex justify-between text-xs text-cyber-text/40 mb-1">
              <span>Puntuación de seguridad</span>
              <span>{report.overallScore}/100</span>
            </div>
            <div className="h-2 rounded-full bg-cyber-border overflow-hidden">
              <div
                className={cn('h-full rounded-full transition-all', SCORE_BAR[report.riskLevel])}
                style={{ width: `${report.overallScore}%` }}
              />
            </div>
          </div>
        )}
      </div>


      {/* Findings */}
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-bold text-cyber-text">
          Hallazgos <span className="text-cyber-text/30 font-normal text-base">({report.findings.length})</span>
        </h3>
        {report.findings.length > 0 && (
          <Link
            to={`/audit/${report.id}/findings`}
            className="flex items-center gap-1.5 text-sm text-cyber-accent/70 hover:text-cyber-accent transition-colors"
          >
            <List size={15} /> Ver todos
          </Link>
        )}
      </div>

      {report.findings.length === 0 ? (
        <div className="text-center py-12 rounded-xl border border-cyber-border bg-cyber-surface/50">
          <CheckCircle size={32} className="text-emerald-400 mx-auto mb-3" />
          <p className="text-cyber-text font-medium">Sin hallazgos de seguridad</p>
          <p className="text-cyber-text/40 text-sm mt-1">Este contrato pasó todos los controles analizados.</p>
        </div>
      ) : (
        <div className="space-y-6">
          {RISK_ORDER.map((risk) => {
            const items = findingsByRisk[risk]
            if (!items?.length) return null
            return (
              <div key={risk}>
                <h4 className={cn('text-xs font-bold mb-3 flex items-center gap-2',
                  RISK_STYLES[risk].split(' ')[1]
                )}>
                  <span className={cn('px-2 py-0.5 rounded', RISK_STYLES[risk])}>
                    {RISK_LABEL[risk]}
                  </span>
                  <span className="text-cyber-text/30 font-normal">{items.length} hallazgo{items.length !== 1 ? 's' : ''}</span>
                </h4>
                <div className="space-y-2">
                  {items.map((f) => <FindingRow key={f.id} finding={f} />)}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
