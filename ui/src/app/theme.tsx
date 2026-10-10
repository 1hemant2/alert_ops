import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { ThemeContext } from './theme-context'
import { ThemeMode, THEME_STORAGE_KEY } from './theme-model'

// Reads the saved theme mode without trusting arbitrary local-storage values.
function readStoredThemeMode(): ThemeMode {
  try {
    return window.localStorage.getItem(THEME_STORAGE_KEY) === ThemeMode.DARK
      ? ThemeMode.DARK
      : ThemeMode.LIGHT
  } catch {
    return ThemeMode.LIGHT
  }
}

// Applies the selected theme to the document before the next render.
function applyThemeMode(mode: ThemeMode) {
  document.documentElement.dataset.theme = mode
  document.documentElement.style.colorScheme = mode
}

// Provides the persisted theme preference to the application tree.
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<ThemeMode>(readStoredThemeMode)

  useEffect(() => {
    applyThemeMode(mode)
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, mode)
    } catch {
      // Private browsing may reject local-storage writes; the in-memory mode still works.
    }
  }, [mode])

  // Toggles between the two supported theme modes.
  function toggleTheme() {
    setMode(currentMode => currentMode === ThemeMode.DARK ? ThemeMode.LIGHT : ThemeMode.DARK)
  }

  const value = useMemo(() => ({ mode, toggleTheme }), [mode])
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}
