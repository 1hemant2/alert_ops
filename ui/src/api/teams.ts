import { jsonBody, request } from './client'
import type { SelectedTeam, Team } from './types'

export function getTeams(): Promise<Team[]> {
  return request<Team[]>('/api/v1/team')
}

export async function createTeam(teamName: string): Promise<SelectedTeam> {
  const result = await request<Team>('/api/v1/team', {
    method: 'POST',
    body: jsonBody({ teamName }),
  })
  return {
    id: result.teamId ?? result.id,
    name: result.teamName ?? result.name,
    role: result.role,
  }
}

export async function selectTeam(team: Team): Promise<{ team: SelectedTeam; token: string }> {
  const teamId = team.teamId ?? team.id
  const result = await request<{ data: { token: string } }>(
    `/api/v1/team/select?teamId=${encodeURIComponent(teamId)}`,
  )
  return {
    team: {
      id: teamId,
      name: team.teamName ?? team.name,
      role: team.role,
    },
    token: result.data.token,
  }
}
