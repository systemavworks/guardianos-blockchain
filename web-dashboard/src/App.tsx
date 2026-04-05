import { useEffect } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import BlockchainList from './pages/BlockchainList'
import BlockchainNew from './pages/BlockchainNew'
import BlockchainReport from './pages/BlockchainReport'
import BlockchainFindings from './pages/BlockchainFindings'
import Sidebar from './components/Sidebar'
import { useAuth } from './services/auth'

const AUDIT_URL = import.meta.env.VITE_AUDIT_URL ?? 'https://audit.guardianos.es'

function App() {
  const { isAuthenticated } = useAuth()
  const hasHandoff = new URLSearchParams(window.location.search).has('handoff')

  useEffect(() => {
    // Sin token y sin handoff en curso → redirigir a guardianos-audit para autenticar
    if (!isAuthenticated && !hasHandoff) {
      window.location.href = AUDIT_URL
    }
  }, [isAuthenticated, hasHandoff])

  if (!isAuthenticated) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-cyber-bg">
        <div className="text-center space-y-3">
          <div className="w-8 h-8 border-2 border-cyber-accent border-t-transparent rounded-full animate-spin mx-auto" />
          <p className="text-cyber-text/50 text-sm">
            {hasHandoff ? 'Iniciando sesión…' : 'Redirigiendo a autenticación…'}
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen bg-cyber-bg">
      <Sidebar />
      <main className="flex-1 p-6">
        <Routes>
          <Route path="/" element={<BlockchainList />} />
          <Route path="/new" element={<BlockchainNew />} />
          <Route path="/audit/:id" element={<BlockchainReport />} />
          <Route path="/audit/:id/findings" element={<BlockchainFindings />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  )
}

export default App
