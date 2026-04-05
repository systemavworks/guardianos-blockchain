import { Link, useLocation } from 'react-router-dom'
import { Shield, List, Plus, LogOut, ExternalLink } from 'lucide-react'
import { cn } from '../utils/cn'
import { useAuth } from '../services/auth'

const AUDIT_DASHBOARD = import.meta.env.VITE_AUDIT_URL ?? 'https://audit.guardianos.es'

interface NavItem {
  to: string
  icon: React.ReactNode
  label: string
  exact?: boolean
}

const navItems: NavItem[] = [
  { to: '/',    icon: <List size={18} />,  label: 'Auditorías',     exact: true },
  { to: '/new', icon: <Plus size={18} />,  label: 'Nueva auditoría' },
]

export default function Sidebar() {
  const { pathname } = useLocation()
  const { email, logout } = useAuth()

  function isActive(item: NavItem) {
    return item.exact ? pathname === item.to : pathname.startsWith(item.to)
  }

  return (
    <aside className="w-56 min-h-screen flex flex-col bg-cyber-surface border-r border-cyber-border py-4">
      {/* Logo */}
      <div className="px-5 mb-8 flex items-center gap-2">
        <Shield size={24} className="text-cyber-accent" />
        <div>
          <p className="text-cyber-text font-semibold text-sm leading-tight">GuardianOS</p>
          <p className="text-cyber-accent text-xs">Blockchain</p>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 px-3 space-y-1">
        {navItems.map((item) => (
          <Link
            key={item.to}
            to={item.to}
            className={cn(
              'flex items-center gap-3 px-3 py-2 rounded-md text-sm transition-colors',
              isActive(item)
                ? 'bg-cyber-accent/10 text-cyber-accent font-medium'
                : 'text-cyber-text/70 hover:text-cyber-text hover:bg-white/5'
            )}
          >
            {item.icon}
            {item.label}
          </Link>
        ))}
      </nav>

      {/* Volver a dashboard principal */}
      <div className="px-3 mb-4">
        <a
          href={AUDIT_DASHBOARD}
          className="flex items-center gap-3 px-3 py-2 rounded-md text-sm text-cyber-text/50 hover:text-cyber-text/80 hover:bg-white/5 transition-colors"
        >
          <ExternalLink size={16} />
          Dashboard principal
        </a>
      </div>

      {/* User + logout */}
      <div className="px-4 pt-4 border-t border-cyber-border">
        {email && (
          <p className="text-xs text-cyber-text/40 mb-2 truncate">{email}</p>
        )}
        <button
          onClick={logout}
          className="flex items-center gap-2 text-xs text-cyber-text/40 hover:text-red-400 transition-colors"
        >
          <LogOut size={14} />
          Cerrar sesión
        </button>
      </div>
    </aside>
  )
}
