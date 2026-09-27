import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createTeam, getTeams, selectTeam } from '../../api/teams'
import type { Team } from '../../api/types'
import { useSession } from '../../app/useSession'
import { Button, Card, ErrorState, Field, LoadingRows } from '../../components/Elements'

export function TeamPickerPage() {
  const [teamName, setTeamName] = useState('')
  const { chooseTeam } = useSession()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const teams = useQuery({ queryKey: ['teams'], queryFn: getTeams })
  const selection = useMutation({
    mutationFn: (team: Team) => selectTeam(team),
    onSuccess: async result => {
      queryClient.clear()
      chooseTeam(result.token, result.team)
      navigate(`/app/${result.team.id}`)
    },
  })
  const create = useMutation({
    mutationFn: async (name: string) => {
      const created = await createTeam(name)
      const result = await selectTeam({ id: created.id, name: created.name, role: created.role })
      return result
    },
    onSuccess: result => {
      queryClient.clear()
      chooseTeam(result.token, result.team)
      navigate(`/app/${result.team.id}`)
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate(teamName.trim())
  }

  return (
    <div className="team-picker">
      <div className="eyebrow">YOUR WORKSPACES</div><h1>Choose your team</h1><p className="page-lede">AlertOps keeps every task and escalation inside its team boundary.</p>
      <div className="team-picker-grid">
        <Card className="team-list-card">
          <div className="card-heading"><div><span className="eyebrow">AVAILABLE</span><h2>Teams</h2></div><span className="count-pill">{teams.data?.length ?? '—'}</span></div>
          {teams.isPending ? <LoadingRows /> : teams.isError ? <ErrorState message={teams.error.message} onRetry={() => void teams.refetch()} /> : teams.data.length === 0 ? <div className="subtle-empty">No teams yet. Create one to start the demo.</div> : (
            <div className="team-list">{teams.data.map((team, index) => <button key={team.id} className="team-row" disabled={selection.isPending} onClick={() => selection.mutate(team)}><span className={`team-mark team-mark-${index % 4}`}>{team.name.slice(0, 1).toUpperCase()}</span><span className="team-row-copy"><strong>{team.name}</strong><small>{team.role?.replaceAll('_', ' ') ?? 'Member'}</small></span><span className="team-enter">→</span></button>)}</div>
          )}
          {(selection.error || create.error) && <div className="form-error" role="alert">{(selection.error ?? create.error)?.message}</div>}
        </Card>
        <Card className="create-team-card">
          <div className="create-icon">＋</div><span className="eyebrow">NEW WORKSPACE</span><h2>Start with a team</h2><p>Create a workspace for your team members, then configure its first escalation flow.</p>
          <form onSubmit={submit} className="form-stack">
            <Field label="Team name"><input required maxLength={80} value={teamName} onChange={event => setTeamName(event.target.value)} placeholder="e.g. Platform Operations" /></Field>
            <Button disabled={create.isPending}>{create.isPending ? 'Creating…' : 'Create team'} <span>→</span></Button>
          </form>
        </Card>
      </div>
      <div className="security-footnote"><span className="lock-mark">◈</span> Every request uses a team scoped token. Switching workspaces clears cached data.</div>
    </div>
  )
}
