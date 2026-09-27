import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { SelectedTeam } from '../api/types'
import { SessionContext } from './session-context'

function readTeam(): SelectedTeam | null {
  try {
    const team = sessionStorage.getItem('alertops.team')
    return team ? JSON.parse(team) as SelectedTeam : null
  } catch {
    return null
  }
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [token, setToken] = useState(() => sessionStorage.getItem('alertops.token'))
  const [team, setTeam] = useState<SelectedTeam | null>(readTeam)

  const logout = useCallback(() => {
    sessionStorage.removeItem('alertops.token')
    sessionStorage.removeItem('alertops.team')
    setToken(null)
    setTeam(null)
    queryClient.clear()
  }, [queryClient])

  const beginSession = useCallback((newToken: string) => {
    sessionStorage.setItem('alertops.token', newToken)
    sessionStorage.removeItem('alertops.team')
    setToken(newToken)
    setTeam(null)
    queryClient.clear()
  }, [queryClient])

  const chooseTeam = useCallback((newToken: string, selectedTeam: SelectedTeam) => {
    sessionStorage.setItem('alertops.token', newToken)
    sessionStorage.setItem('alertops.team', JSON.stringify(selectedTeam))
    setToken(newToken)
    setTeam(selectedTeam)
    queryClient.clear()
  }, [queryClient])

  useEffect(() => {
    const clearExpiredSession = () => logout()
    window.addEventListener('alertops:unauthorized', clearExpiredSession)
    return () => window.removeEventListener('alertops:unauthorized', clearExpiredSession)
  }, [logout])

  const value = useMemo(() => ({ token, team, beginSession, chooseTeam, logout }), [token, team, beginSession, chooseTeam, logout])
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}
