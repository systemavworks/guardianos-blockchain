import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { ArrowLeft, Search, AlertCircle, Loader2 } from 'lucide-react'
import { blockchainApi } from '../services/api'
import { cn } from '../utils/cn'

const CHAINS = [
  { id: 'ethereum', label: 'Ethereum',  chainId: '1'     },
  { id: 'bsc',      label: 'BNB Chain', chainId: '56'    },
  { id: 'polygon',  label: 'Polygon',   chainId: '137'   },
  { id: 'arbitrum', label: 'Arbitrum',  chainId: '42161' },
  { id: 'optimism', label: 'Optimism',  chainId: '10'    },
  { id: 'avalanche',label: 'Avalanche', chainId: '43114' },
]

const ADDRESS_REGEX = /^0x[0-9a-fA-F]{40}$/

export default function BlockchainNew() {
  const navigate = useNavigate()
  const [address, setAddress] = useState('')
  const [selectedChain, setSelectedChain] = useState('ethereum')
  const [label, setLabel] = useState('')

  const addressError = address.length > 0 && !ADDRESS_REGEX.test(address)
    ? 'Dirección inválida. Debe comenzar con 0x y tener 42 caracteres.'
    : null

  const mutation = useMutation({
    mutationFn: blockchainApi.createAudit,
    onSuccess: (data) => {
      navigate(`/audit/${data.id}`)
    },
  })

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!ADDRESS_REGEX.test(address)) return

    const chain = CHAINS.find((c) => c.id === selectedChain)!
    mutation.mutate({ address, chainId: chain.chainId, label: label.trim() || undefined })
  }

  return (
    <div className="max-w-xl mx-auto">
      {/* Header */}
      <Link
        to="/"
        className="inline-flex items-center gap-2 text-sm text-cyber-text/50 hover:text-cyber-text mb-6 transition-colors"
      >
        <ArrowLeft size={16} /> Volver a auditorías
      </Link>

      <h1 className="text-2xl font-bold text-cyber-text mb-1">Nueva auditoría</h1>
      <p className="text-cyber-text/50 text-sm mb-8">
        Análisis de seguridad de contrato EVM usando GoPlus Security
      </p>

      <form onSubmit={handleSubmit} className="space-y-5">
        {/* Dirección del contrato */}
        <div>
          <label className="block text-sm font-medium text-cyber-text mb-2">
            Dirección del contrato <span className="text-red-400">*</span>
          </label>
          <input
            type="text"
            value={address}
            onChange={(e) => setAddress(e.target.value.trim())}
            placeholder="0x..."
            className={cn(
              'w-full px-4 py-2.5 rounded-lg bg-cyber-surface border text-cyber-text font-mono text-sm outline-none transition-colors placeholder:text-cyber-text/20',
              addressError
                ? 'border-red-500/60 focus:border-red-500'
                : 'border-cyber-border focus:border-cyber-accent/60'
            )}
          />
          {addressError && (
            <p className="mt-1.5 text-xs text-red-400 flex items-center gap-1">
              <AlertCircle size={12} /> {addressError}
            </p>
          )}
        </div>

        {/* Red blockchain */}
        <div>
          <label className="block text-sm font-medium text-cyber-text mb-2">Red blockchain</label>
          <div className="grid grid-cols-3 gap-2">
            {CHAINS.map((chain) => (
              <button
                key={chain.id}
                type="button"
                onClick={() => setSelectedChain(chain.id)}
                className={cn(
                  'px-3 py-2 rounded-lg border text-sm transition-colors text-center',
                  selectedChain === chain.id
                    ? 'bg-cyber-accent/10 border-cyber-accent/60 text-cyber-accent font-medium'
                    : 'bg-cyber-surface border-cyber-border text-cyber-text/60 hover:border-cyber-border/80 hover:text-cyber-text'
                )}
              >
                {chain.label}
              </button>
            ))}
          </div>
        </div>

        {/* Etiqueta opcional */}
        <div>
          <label className="block text-sm font-medium text-cyber-text mb-2">
            Etiqueta <span className="text-cyber-text/30">(opcional)</span>
          </label>
          <input
            type="text"
            value={label}
            onChange={(e) => setLabel(e.target.value)}
            placeholder="Ej: Token MyProject, Contrato staking…"
            maxLength={80}
            className="w-full px-4 py-2.5 rounded-lg bg-cyber-surface border border-cyber-border text-cyber-text text-sm outline-none focus:border-cyber-accent/60 transition-colors placeholder:text-cyber-text/20"
          />
        </div>

        {/* Error del servidor */}
        {mutation.isError && (
          <div className="flex items-start gap-2 p-3 rounded-lg bg-red-900/20 border border-red-700/30 text-red-400 text-sm">
            <AlertCircle size={16} className="mt-0.5 flex-shrink-0" />
            {(mutation.error as Error)?.message ?? 'Error al crear la auditoría. Inténtalo de nuevo.'}
          </div>
        )}

        {/* Submit */}
        <button
          type="submit"
          disabled={mutation.isPending || !!addressError || address.length === 0}
          className="w-full flex items-center justify-center gap-2 px-4 py-3 rounded-lg bg-cyber-accent text-cyber-bg font-semibold text-sm hover:bg-cyber-accent/80 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          {mutation.isPending ? (
            <>
              <Loader2 size={16} className="animate-spin" />
              Iniciando auditoría…
            </>
          ) : (
            <>
              <Search size={16} />
              Iniciar análisis
            </>
          )}
        </button>
      </form>

      <p className="mt-6 text-xs text-cyber-text/30 text-center">
        El análisis tarda ~15 segundos. Usa la API gratuita de GoPlus Security.
      </p>
    </div>
  )
}
