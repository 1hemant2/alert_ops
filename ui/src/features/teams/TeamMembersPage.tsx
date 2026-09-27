import { useState, type FormEvent } from 'react'
import { useParams } from 'react-router'
import { useMutation, useQuery } from '@tanstack/react-query'
import { createTeamInvite, getTeamMembers } from '../../api/teams'
import type { TeamInviteRequest } from '../../api/types'
import { useSession } from '../../app/useSession'
import { Button, Card, EmptyState, ErrorState, Field, InlineNotice, LoadingRows, PageHeader } from '../../components/Elements'
import { teamRoleDescription, teamRoleLabel } from './teamRoles'

export function TeamMembersPage() {
  const { teamId = '' } = useParams()
  const { team } = useSession()
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<TeamInviteRequest['role']>('USER')
  const [expiresInHours, setExpiresInHours] = useState(72)
  const [inviteSentTo, setInviteSentTo] = useState('')
  const canInvite = team?.role === 'TEAM_OWNER' || team?.role === 'ADMIN'
  const members = useQuery({
    queryKey: ['team-members', teamId],
    queryFn: getTeamMembers,
  })
  const invite = useMutation({
    mutationFn: (request: TeamInviteRequest) => createTeamInvite(request),
    onSuccess: result => {
      setEmail('')
      setInviteSentTo(result.email)
    },
  })

  function submitInvite(event: FormEvent) {
    event.preventDefault()
    setInviteSentTo('')
    invite.mutate({ email: email.trim(), role, expiresInHours })
  }

  return <>
    <PageHeader
      eyebrow="WORKSPACE / MEMBERS"
      title="Team members"
      description="See who belongs to this workspace and the role the server has assigned to each person."
    />
    {canInvite && <Card className="invite-card">
      <div className="card-heading">
        <div><span className="eyebrow">GROW YOUR TEAM</span><h2>Invite a member</h2></div>
      </div>
      <p className="invite-description">Send a secure invitation link to a teammate. They must sign in with the invited email address.</p>
      <form className="invite-form" onSubmit={submitInvite}>
        <Field label="Work email">
          <input autoComplete="email" type="email" required maxLength={254} value={email} onChange={event => setEmail(event.target.value)} placeholder="teammate@company.com" />
        </Field>
        <Field label="Role" hint={teamRoleDescription(role)}>
          <select value={role} onChange={event => setRole(event.target.value as TeamInviteRequest['role'])}>
            {team?.role === 'TEAM_OWNER' && <option value="ADMIN">Admin</option>}
            <option value="USER">User</option>
          </select>
        </Field>
        <Field label="Link expires after">
          <select value={expiresInHours} onChange={event => setExpiresInHours(Number(event.target.value))}>
            <option value={24}>24 hours</option>
            <option value={72}>3 days</option>
            <option value={168}>7 days</option>
          </select>
        </Field>
        <Button disabled={invite.isPending}>{invite.isPending ? 'Sending invitation…' : 'Send invitation'} <span>→</span></Button>
      </form>
      {inviteSentTo && <InlineNotice tone="success">The invitation email was accepted by SMTP for {inviteSentTo}.</InlineNotice>}
      {invite.error && <div className="form-error" role="alert">{invite.error.message}</div>}
    </Card>}
    {!canInvite && <InlineNotice>Only team owners and admins can send invitations. The API checks this permission when an invitation is submitted.</InlineNotice>}
    <Card className="members-card">
      <div className="card-heading">
        <div><span className="eyebrow">CURRENT TEAM</span><h2>People</h2></div>
        <span className="count-pill">{members.data?.length ?? '—'} MEMBERS</span>
      </div>
      {members.isPending ? <LoadingRows count={4} />
        : members.isError ? <ErrorState message={members.error.message} onRetry={() => void members.refetch()} />
          : members.data.length === 0 ? <EmptyState title="No members found" description="This team has no member records to show." />
            : <div className="member-list">
              {members.data.map(member => (
                <article className="member-row" key={member.memberId}>
                  <span className="member-mark" aria-hidden="true">{member.name.slice(0, 1).toUpperCase()}</span>
                  <div className="member-copy">
                    <strong>{member.name}</strong>
                    <a href={`mailto:${member.email}`}>{member.email}</a>
                  </div>
                  <div className="member-role-details">
                    <span className="member-role">{teamRoleLabel(member.role)}</span>
                    <small>{teamRoleDescription(member.role)}</small>
                  </div>
                </article>
              ))}
            </div>}
    </Card>
    <p className="members-note">Role changes are not available yet.</p>
  </>
}
