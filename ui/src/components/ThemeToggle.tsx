import { ThemeMode } from '../app/theme-model'
import { useTheme } from '../app/useTheme'

// Renders the persisted light/dark theme control.
export function ThemeToggle() {
  const { mode, toggleTheme } = useTheme()
  const darkModeEnabled = mode === ThemeMode.DARK
  const nextModeLabel = darkModeEnabled ? 'Switch to light mode' : 'Switch to dark mode'

  return (
    <button
      className="theme-toggle"
      type="button"
      aria-label={nextModeLabel}
      aria-pressed={darkModeEnabled}
      title={nextModeLabel}
      onClick={toggleTheme}
    >
      <span className="theme-toggle-icon" aria-hidden="true">{darkModeEnabled ? '☀' : '☾'}</span>
      <span className="theme-toggle-label">{darkModeEnabled ? 'Light' : 'Dark'}</span>
    </button>
  )
}
