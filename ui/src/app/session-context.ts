import { createContext } from 'react'
import type { SelectedTeam } from '../api/types'

export interface SessionValue {
  token: string | null
  team: SelectedTeam | null
  beginSession: (token: string) => void
  chooseTeam: (token: string, team: SelectedTeam) => void
  logout: () => void
}

export const SessionContext = createContext<SessionValue | null>(null)
