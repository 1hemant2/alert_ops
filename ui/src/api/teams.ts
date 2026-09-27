import { jsonBody, request } from './client'
import type { SelectedTeam, Team, TeamInviteAcceptance, TeamInvitePreview, TeamInviteRequest, TeamInviteResponse, TeamMember } from './types'

export function getTeams(): Promise<Team[]> {
  return request<Team[]>('/api/v1/team')
}

export function getTeamMembers(): Promise<TeamMember[]> {
  return request<TeamMember[]>('/api/v1/team/members')
}

export function createTeamInvite(payload: TeamInviteRequest): Promise<TeamInviteResponse> {
  return request<TeamInviteResponse>('/api/v1/team/invite', {
    method: 'POST',
    body: jsonBody(payload),
  })
}

export function previewTeamInvite(token: string): Promise<TeamInvitePreview> {
  return request<TeamInvitePreview>(`/api/v1/team/join?token=${encodeURIComponent(token)}`, { public: true })
}

export function acceptTeamInvite(token: string): Promise<TeamInviteAcceptance> {
  return request<TeamInviteAcceptance>('/api/v1/team/join', {
    method: 'POST',
    body: jsonBody({ token }),
  })
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
