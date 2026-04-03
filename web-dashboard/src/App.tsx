import { Routes, Route, Navigate } from 'react-router-dom'
import BlockchainList from './pages/BlockchainList'
import BlockchainNew from './pages/BlockchainNew'
import BlockchainReport from './pages/BlockchainReport'
import BlockchainFindings from './pages/BlockchainFindings'
import Sidebar from './components/Sidebar'
import { useAuth } from './services/auth'

function App() {
  const { isAuthenticated } = useAuth()
  
  if (!isAuthenticated) {
    return <div className="min-h-screen flex items-center justify-center">
      🔐 Redirigiendo a autenticación...
    </div>
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
          <Route path="/audit/:id/pdf" element={<div>📄 Generando PDF...</div>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  )
}

export default App
