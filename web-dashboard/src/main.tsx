import React, { useEffect } from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'
import './index.css'

// SSO: Token pass desde guardianos-audit
const AUDIT_URL = import.meta.env.VITE_AUDIT_URL ?? 'https://audit.guardianos.es'

const handleHandoffToken = () => {
  const params = new URLSearchParams(window.location.search)
  const handoff = params.get('handoff')

  if (handoff) {
    // Canjear token efímero con backend blockchain
    fetch(`/api/v1/auth/session?handoff=${encodeURIComponent(handoff)}`)
      .then(res => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json()
      })
      .then(data => {
        if (data.token) {
          localStorage.setItem('bc_jwt', data.token)
          // Limpiar URL para no dejar token en historial
          window.history.replaceState({}, '', '/')
          window.location.reload()
        } else {
          // El backend no entregó token — volver a guardianos-audit
          window.location.href = AUDIT_URL
        }
      })
      .catch(() => {
        // Error de red o backend — redirigir a guardianos-audit
        window.location.href = AUDIT_URL
      })
  }
}

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 5 * 60 * 1000,
    },
  },
})

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <TokenHandler />
        <Routes>
          <Route path="/*" element={<App />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>
)

// Componente para manejar handoff al montar
function TokenHandler() {
  useEffect(() => {
    handleHandoffToken()
  }, [])
  return null
}
