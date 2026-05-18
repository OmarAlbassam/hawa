import { useState } from 'react'
import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { LayoutDashboard, Tag, FileBarChart, LogOut, PanelLeftClose, PanelLeftOpen } from 'lucide-react'
import { useAuth } from '../../context/useAuth'
import { logout } from '../../services/authService'
import BrandSelector from '../BrandSelector/BrandSelector'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { ThemeToggle } from '@/components/theme-toggle'
import { HawaLogo } from '@/components/brand/hawa-mark'

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/brands', label: 'Brands', icon: Tag },
  { to: '/reports', label: 'Reports', icon: FileBarChart },
]

const MarketingLayout = (): React.JSX.Element => {
  const { user, refreshToken, clearAuth } = useAuth()
  const navigate = useNavigate()
  const [sidebarOpen, setSidebarOpen] = useState(false)

  const handleLogout = async () => {
    try {
      if (refreshToken) await logout(refreshToken)
    } finally {
      clearAuth()
      navigate('/login', { replace: true })
    }
  }

  return (
    <div className="flex min-h-screen bg-background text-foreground">
      <aside
        className={cn(
          'sticky top-0 h-screen shrink-0 overflow-hidden border-r border-border bg-background',
          'transition-[width] duration-300 ease-out motion-reduce:transition-none',
          sidebarOpen ? 'w-[220px]' : 'w-14',
        )}
        aria-label="Primary navigation"
      >
        <div className="flex h-full w-[220px] flex-col">
          <div className="flex h-14 shrink-0 items-center border-b border-border px-2">
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setSidebarOpen((v) => !v)}
              aria-label={sidebarOpen ? 'Collapse sidebar' : 'Expand sidebar'}
              aria-expanded={sidebarOpen}
            >
              {sidebarOpen ? <PanelLeftClose /> : <PanelLeftOpen />}
            </Button>
          </div>

          <div className="flex min-h-0 flex-1 flex-col p-2">
            <div
              className={cn(
                'px-2 pb-3 pt-1 transition-opacity duration-200',
                sidebarOpen ? 'opacity-100' : 'opacity-0',
              )}
              aria-hidden={!sidebarOpen}
            >
              <span className="eyebrow">Workspace</span>
            </div>

            <nav className="flex flex-col gap-0.5">
              {navItems.map(({ to, label, icon: Icon }) => (
                <NavLink
                  key={to}
                  to={to}
                  title={sidebarOpen ? undefined : label}
                  className={({ isActive }) =>
                    cn(
                      'flex h-9 items-center gap-2.5 rounded-sm px-2.5 text-[13px] transition-colors',
                      isActive
                        ? 'border border-border bg-card font-medium text-foreground [&_svg]:text-primary'
                        : 'text-muted-foreground hover:bg-muted hover:text-foreground [&_svg]:text-text-3',
                    )
                  }
                >
                  <Icon className="size-4 shrink-0" />
                  <span
                    className={cn(
                      'transition-opacity duration-200',
                      sidebarOpen ? 'opacity-100' : 'opacity-0',
                    )}
                  >
                    {label}
                  </span>
                </NavLink>
              ))}
            </nav>

            <div className="mt-auto">
              <Separator className="my-3" />
              <button
                onClick={handleLogout}
                title={sidebarOpen ? undefined : 'Logout'}
                aria-label="Logout"
                className="flex h-9 w-full items-center gap-2.5 rounded-sm px-2.5 text-[13px] text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
              >
                <LogOut className="size-4 shrink-0 text-text-3" />
                <span
                  className={cn(
                    'transition-opacity duration-200',
                    sidebarOpen ? 'opacity-100' : 'opacity-0',
                  )}
                >
                  Logout
                </span>
              </button>
            </div>
          </div>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 h-14 border-b border-border bg-background/85 backdrop-blur supports-[backdrop-filter]:bg-background/70">
          <div className="flex h-full items-center gap-3 px-4 lg:px-6">
            <div className="flex items-center gap-3">
              <HawaLogo />
              <span className="h-4 w-px bg-border-strong" />
              <span className="eyebrow">Marketing</span>
            </div>

            <div className="ml-auto flex items-center gap-3">
              <BrandSelector />
              <div className="hidden text-right sm:block">
                <div className="text-[13px] font-medium text-foreground">
                  {user?.firstName} {user?.lastName}
                </div>
                <div className="text-[11px] font-mono uppercase tracking-[0.1em] text-text-3">
                  Marketing
                </div>
              </div>
              <ThemeToggle />
            </div>
          </div>
        </header>

        <main className="min-w-0 flex-1">
          <div className="px-4 py-6 lg:px-8 lg:py-8">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}

export default MarketingLayout
