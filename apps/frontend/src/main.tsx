import { createRoot } from 'react-dom/client'
import './index.css'
import AppRouter from './router'
import { ThemeProvider } from '@/components/theme-provider'

createRoot(document.getElementById('root')!).render(
  <ThemeProvider>
    <AppRouter />
  </ThemeProvider>,
)
