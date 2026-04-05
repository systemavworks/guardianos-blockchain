const CHAIN_CONFIG: Record<string, { label: string; color: string; bg: string; symbol: string }> = {
  ethereum:  { label: 'Ethereum',  color: 'text-blue-300',   bg: 'bg-blue-900/30 border-blue-700/40',   symbol: 'Ξ' },
  bsc:       { label: 'BNB Chain', color: 'text-yellow-300', bg: 'bg-yellow-900/30 border-yellow-700/40', symbol: '⬡' },
  polygon:   { label: 'Polygon',   color: 'text-purple-300', bg: 'bg-purple-900/30 border-purple-700/40', symbol: '⬡' },
  arbitrum:  { label: 'Arbitrum',  color: 'text-sky-300',    bg: 'bg-sky-900/30 border-sky-700/40',       symbol: 'A' },
  optimism:  { label: 'Optimism',  color: 'text-red-300',    bg: 'bg-red-900/30 border-red-700/40',       symbol: 'O' },
  avalanche: { label: 'Avalanche', color: 'text-orange-300', bg: 'bg-orange-900/30 border-orange-700/40', symbol: 'A' },
}

interface Props {
  chainId: string
  size?: 'sm' | 'md'
}

export default function ChainBadge({ chainId, size = 'md' }: Props) {
  const cfg = CHAIN_CONFIG[chainId?.toLowerCase()] ?? {
    label: chainId ?? 'unknown',
    color: 'text-gray-300',
    bg: 'bg-gray-900/30 border-gray-700/40',
    symbol: '?',
  }

  const textSize = size === 'sm' ? 'text-xs' : 'text-xs'
  const padding  = size === 'sm' ? 'px-1.5 py-0.5' : 'px-2 py-1'

  return (
    <span
      className={`inline-flex items-center gap-1 rounded border font-medium ${textSize} ${padding} ${cfg.bg} ${cfg.color}`}
    >
      <span className="font-bold">{cfg.symbol}</span>
      {cfg.label}
    </span>
  )
}
