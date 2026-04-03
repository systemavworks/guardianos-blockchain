import React, { useEffect } from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'
import './index.css'

// SSO: Token pass desde guardianos-audit
const handleHandoffToken = () => {
  const params = new URLSearchParams(window.location.search)
  const handoff = params.get('handoff')
  
  if (handoff) {
    // Canjear token efímero con backend blockchain
    fetch(`/api/v1/auth/session?handoff=${handoff}`)
      .then(res => res.json())
      .then(data => {
        if (data.token) {
          localStorage.setItem('bc_jwt', data.token)
          // Limpiar URL para no dejar token en historial
          window.history.replaceState({}, '', '/')
          // Recargar para aplicar auth
          window.location.reload()
        }
      })
      .catch(err => console.error('Error canjeando handoff token:', err))
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
