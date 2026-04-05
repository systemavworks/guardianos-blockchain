import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from 'recharts'
import type { BlockchainFinding } from '../services/api'

const RISK_COLORS: Record<string, string> = {
  CRITICAL: '#ef4444',
  HIGH:     '#f97316',
  MEDIUM:   '#eab308',
  LOW:      '#3b82f6',
  INFO:     '#10b981',
}

const RISK_LABEL: Record<string, string> = {
  CRITICAL: 'Crítico',
  HIGH:     'Alto',
  MEDIUM:   'Medio',
  LOW:      'Bajo',
  INFO:     'Info',
}

const RISK_ORDER = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO']

interface Props {
  findings: BlockchainFinding[]
}

export default function RiskDonutChart({ findings }: Props) {
  const counts = RISK_ORDER.reduce<Record<string, number>>((acc, r) => {
    const n = findings.filter(f => f.risk === r).length
    if (n > 0) acc[r] = n
    return acc
  }, {})

  const data = Object.entries(counts).map(([risk, value]) => ({ risk, value }))

  if (data.length === 0) return null

  return (
    <div className="rounded-xl bg-cyber-surface border border-cyber-border p-5">
      <h3 className="text-sm font-semibold text-cyber-text/60 uppercase tracking-wider mb-4">
        Distribución de hallazgos
      </h3>
      <div className="flex items-center gap-6">
        <ResponsiveContainer width={140} height={140}>
          <PieChart>
            <Pie
              data={data}
              cx="50%"
              cy="50%"
              innerRadius={42}
              outerRadius={62}
              paddingAngle={3}
              dataKey="value"
              strokeWidth={0}
            >
              {data.map(({ risk }) => (
                <Cell key={risk} fill={RISK_COLORS[risk]} />
              ))}
            </Pie>
            <Tooltip
              contentStyle={{
                background: '#0d0d14',
                border: '1px solid #1e1e2e',
                borderRadius: '8px',
                fontSize: '12px',
                color: '#e2e8f0',
              }}
              formatter={(value, _name, entry) => {
                const v = Number(value)
                const risk = (entry as { payload?: { risk?: string } }).payload?.risk ?? ''
                return [`${v} hallazgo${v !== 1 ? 's' : ''}`, RISK_LABEL[risk] ?? risk]
              }}
            />
          </PieChart>
        </ResponsiveContainer>

        {/* Leyenda */}
        <div className="flex flex-col gap-2">
          {data.map(({ risk, value }) => (
            <div key={risk} className="flex items-center gap-2 text-sm">
              <span
                className="w-3 h-3 rounded-full flex-shrink-0"
                style={{ background: RISK_COLORS[risk] }}
              />
              <span className="text-cyber-text/70">{RISK_LABEL[risk]}</span>
              <span
                className="ml-auto font-bold tabular-nums"
                style={{ color: RISK_COLORS[risk] }}
              >
                {value}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
