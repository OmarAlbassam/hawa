import { useState } from 'react'
import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { LayoutDashboard, Tag, FileBarChart, LogOut, Menu, X } from 'lucide-react'
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
    <div className="min-h-screen bg-background text-foreground">
      <header className="sticky top-0 z-30 h-14 border-b border-border bg-background/85 backdrop-blur supports-[backdrop-filter]:bg-background/70">
        <div className="flex h-full items-center gap-3 px-4 lg:px-6">
          <Button
            variant="ghost"
            size="icon"
            className="lg:hidden"
            onClick={() => setSidebarOpen((v) => !v)}
            aria-label="Toggle sidebar"
          >
            {sidebarOpen ? <X /> : <Menu />}
          </Button>

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

      <div className="flex">
        <aside
          className={cn(
            'fixed bottom-0 left-0 top-14 z-20 w-[220px] shrink-0 border-r border-border bg-muted/60 transition-transform',
            'lg:sticky lg:top-14 lg:h-[calc(100vh-3.5rem)] lg:translate-x-0',
            sidebarOpen ? 'translate-x-0' : '-translate-x-full',
          )}
        >
          <div className="flex h-full flex-col p-4">
            <div className="px-2 pb-3 pt-1">
              <span className="eyebrow">Workspace</span>
            </div>

            <nav className="flex flex-col gap-0.5">
              {navItems.map(({ to, label, icon: Icon }) => (
                <NavLink
                  key={to}
                  to={to}
                  onClick={() => setSidebarOpen(false)}
                  className={({ isActive }) =>
                    cn(
                      'flex h-8 items-center gap-2.5 rounded-sm px-2 text-[13px] transition-colors',
                      isActive
                        ? 'border border-border bg-card font-medium text-foreground [&_svg]:text-primary'
                        : 'text-muted-foreground hover:bg-muted hover:text-foreground [&_svg]:text-text-3',
                    )
                  }
                >
                  <Icon className="size-3.5" />
                  <span>{label}</span>
                </NavLink>
              ))}
            </nav>

            <div className="mt-auto">
              <Separator className="my-3" />
              <button
                onClick={handleLogout}
                className="flex h-8 w-full items-center gap-2.5 rounded-sm px-2 text-[13px] text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
              >
                <LogOut className="size-3.5 text-text-3" />
                <span>Logout</span>
              </button>
            </div>
          </div>
        </aside>

        {sidebarOpen && (
          <button
            type="button"
            aria-label="Close sidebar"
            onClick={() => setSidebarOpen(false)}
            className="fixed inset-0 top-14 z-10 bg-black/30 backdrop-blur-sm lg:hidden"
          />
        )}

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
