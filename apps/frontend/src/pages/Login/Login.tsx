import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AlertCircle, Loader2 } from 'lucide-react'
import { useAuth } from '../../context/useAuth'
import { login } from '../../services/authService'
import type { LoginRequest } from '../../types/auth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ThemeToggle } from '@/components/theme-toggle'
import { cn } from '@/lib/utils'
import { HawaLogo } from '@/components/brand/hawa-mark'

const Login = (): React.JSX.Element => {
  const navigate = useNavigate()
  const { setAuth } = useAuth()
  const [form, setForm] = useState<LoginRequest>({ email: '', password: '' })
  const [errors, setErrors] = useState<Partial<Record<keyof LoginRequest, string>>>({})
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const validate = (): boolean => {
    const newErrors: Partial<Record<keyof LoginRequest, string>> = {}
    if (!form.email.trim()) newErrors.email = 'Email is required'
    if (!form.password) newErrors.password = 'Password is required'
    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!validate()) return
    setSubmitting(true)
    setSubmitError(null)
    try {
      const authResponse = await login(form)
      setAuth(authResponse)
      navigate(authResponse.user.role === 'ADMIN' ? '/admin' : '/dashboard')
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Login failed')
    } finally {
      setSubmitting(false)
    }
  }

  const handleChange = (field: keyof LoginRequest, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }))
    if (errors[field]) setErrors((prev) => ({ ...prev, [field]: undefined }))
    if (submitError) setSubmitError(null)
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center bg-background px-4 py-12">
      <div className="absolute right-4 top-4">
        <ThemeToggle />
      </div>

      <div className="w-full max-w-[400px]">
        {/* Brand mark */}
        <div className="mb-10 flex items-center justify-center">
          <HawaLogo
            markClassName="h-9 w-9"
            wordmarkClassName="text-[28px]"
          />
        </div>

        <div className="rounded-md border border-border bg-card p-8 shadow-sm">
          <div className="mb-6 space-y-1">
            <h1 className="font-display text-[22px] font-semibold tracking-[-0.02em] text-foreground">
              Sign in
            </h1>
            <p className="text-[13px] text-muted-foreground">
              Access your sentiment analysis workspace.
            </p>
          </div>

          {submitError && (
            <div
              role="alert"
              className="mb-5 flex items-start gap-2.5 rounded-md border border-neg/30 bg-neg-bg px-3 py-2.5 text-[13px] text-neg-text"
            >
              <AlertCircle className="mt-0.5 size-4 shrink-0" />
              <span>{submitError}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                value={form.email}
                onChange={(e) => handleChange('email', e.target.value)}
                placeholder="you@example.com"
                autoComplete="email"
                aria-invalid={!!errors.email}
                aria-describedby={errors.email ? 'email-error' : undefined}
                className={cn(errors.email && 'border-neg focus-visible:border-neg focus-visible:ring-neg/15')}
              />
              {errors.email && (
                <span id="email-error" role="alert" className="text-[12px] text-neg-text">
                  {errors.email}
                </span>
              )}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                value={form.password}
                onChange={(e) => handleChange('password', e.target.value)}
                placeholder="Enter your password"
                autoComplete="current-password"
                aria-invalid={!!errors.password}
                aria-describedby={errors.password ? 'password-error' : undefined}
                className={cn(errors.password && 'border-neg focus-visible:border-neg focus-visible:ring-neg/15')}
              />
              {errors.password && (
                <span id="password-error" role="alert" className="text-[12px] text-neg-text">
                  {errors.password}
                </span>
              )}
            </div>

            <Button type="submit" disabled={submitting} className="w-full">
              {submitting && <Loader2 className="size-4 animate-spin" />}
              {submitting ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>
        </div>

      </div>
    </div>
  )
}

export default Login
