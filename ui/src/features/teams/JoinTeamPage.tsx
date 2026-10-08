import { useEffect } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { useMutation, useQuery } from '@tanstack/react-query'
import { acceptTeamInvite, previewTeamInvite, selectTeam } from '../../api/teams'
import { useSession } from '../../app/useSession'
import { Button, Card, ErrorState, InlineNotice, LoadingRows } from '../../components/Elements'
import { teamRoleDescription, teamRoleLabel } from './teamRoles'

const PENDING_INVITE_KEY = 'alertops.pending-invite'

// Renders the ReplyTrail invitation preview and acceptance flow.
export function JoinTeamPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const { token: sessionToken, chooseTeam } = useSession()
  const urlToken = searchParams.get('token')
  const inviteToken = urlToken ?? sessionStorage.getItem(PENDING_INVITE_KEY) ?? ''

  useEffect(() => {
    if (!urlToken) return
    sessionStorage.setItem(PENDING_INVITE_KEY, urlToken)
    const cleanParams = new URLSearchParams(searchParams)
    cleanParams.delete('token')
    setSearchParams(cleanParams, { replace: true })
  }, [searchParams, setSearchParams, urlToken])

  const preview = useQuery({
    queryKey: ['team-invite-preview', inviteToken],
    queryFn: () => previewTeamInvite(inviteToken),
    enabled: Boolean(inviteToken),
  })

  const accept = useMutation({
    mutationFn: async () => {
      const accepted = await acceptTeamInvite(inviteToken)
      return selectTeam({ id: accepted.teamId, name: accepted.teamName, role: accepted.role })
    },
    onSuccess: result => {
      sessionStorage.removeItem(PENDING_INVITE_KEY)
      chooseTeam(result.token, result.team)
      navigate(`/app/${result.team.id}`)
    },
  })

  return <div className="auth-page join-page">
    <Link className="brand" to="/"><span className="brand-icon"><b /><b /><b /></span><span>REPLY<span>TRAIL</span></span></Link>
    <Card className="auth-card join-card">
      <span className="eyebrow">TEAM INVITATION</span>
      {!inviteToken ? <>
        <h1>Invitation link missing</h1>
        <p>Open the invitation link from your email to view and accept it.</p>
        <Link className="button button-secondary" to="/teams">Go to your teams</Link>
      </> : preview.isPending ? <>
        <h1>Checking invitation…</h1>
        <LoadingRows count={2} />
      </> : preview.isError ? <>
        <h1>Invitation unavailable</h1>
        <ErrorState message={preview.error.message} onRetry={() => void preview.refetch()} />
      </> : <>
        <h1>Join {preview.data.teamName}</h1>
        <p>You’ve been invited to join this ReplyTrail team as a <strong>{teamRoleLabel(preview.data.role).toLowerCase()}</strong>.</p>
        <p>{teamRoleDescription(preview.data.role)}</p>
        <dl className="invite-summary">
          <div><dt>Invited email</dt><dd>{preview.data.email}</dd></div>
          <div><dt>Invitation expires</dt><dd>{new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(preview.data.expiresAt))}</dd></div>
        </dl>
        {!sessionToken ? <>
          <InlineNotice>Sign in or create an account with the invited email address, then verify it before accepting.</InlineNotice>
          <div className="join-actions">
            <Link className="button button-primary" to="/login">Sign in to accept <span>→</span></Link>
            <Link className="button button-secondary" to="/register">Create an account</Link>
          </div>
        </> : <>
          {accept.error && <div role="alert" className="form-error">{accept.error.message}</div>}
          <Button className="button-full" disabled={accept.isPending} onClick={() => accept.mutate()}>
            {accept.isPending ? 'Joining team…' : 'Accept invitation'} <span>→</span>
          </Button>
        </>}
      </>}
    </Card>
    <div className="auth-caption">REPLYTRAIL <i /> KEEP EVERY RESPONSE ON TRACK</div>
  </div>
}
