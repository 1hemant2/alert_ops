export const ThemeMode = {
  LIGHT: 'light',
  DARK: 'dark',
} as const

export type ThemeMode = typeof ThemeMode[keyof typeof ThemeMode]

export const THEME_STORAGE_KEY = 'replytrail.theme'
