import { createContext } from 'react'
import type { ThemeMode } from './theme-model'

export type ThemeContextValue = {
  mode: ThemeMode
  toggleTheme: () => void
}

export const ThemeContext = createContext<ThemeContextValue | null>(null)
