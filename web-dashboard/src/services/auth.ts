import { useMemo } from 'react'

const AUDIT_DOMAIN = import.meta.env.VITE_AUDIT_URL ?? 'https://audit.guardianos.es'

function decodeJwt(token: string): Record<string, unknown> | null {
  try {
    const payload = token.split('.')[1]
    return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
  } catch {
    return null
  }
}

function isExpired(decoded: Record<string, unknown>): boolean {
  const exp = decoded['exp']
  if (typeof exp !== 'number') return false
  return Date.now() / 1000 > exp
}

export function useAuth() {
  const token = localStorage.getItem('bc_jwt')

  const { isAuthenticated, tenantId, email } = useMemo(() => {
    if (!token) return { isAuthenticated: false, tenantId: null, email: null }
    const decoded = decodeJwt(token)
    if (!decoded || isExpired(decoded)) {
      localStorage.removeItem('bc_jwt')
      return { isAuthenticated: false, tenantId: null, email: null }
    }
    return {
      isAuthenticated: true,
      tenantId: decoded['tenantId'] as string | null,
      email:    decoded['email']    as string | null,
    }
  }, [token])

  function logout() {
    localStorage.removeItem('bc_jwt')
    window.location.href = `${AUDIT_DOMAIN}/login`
  }

  return { isAuthenticated, tenantId, email, logout }
}
