import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { confirmRecipientEscalateNow, previewRecipientEscalateNow } from '../../api/escalations'
import { ApiError } from '../../api/client'
import type { EscalationManualAction } from '../../api/types'
import { Button } from '../../components/Elements'
import { formatDate } from '../../lib/format'

function manualActionError(error: Error | null): string {
  if (error instanceof ApiError) {
    if (error.status === 410) return 'This Escalate now link has expired.'
    if (error.status === 409) return 'This escalation has already moved or the action is no longer available.'
    if (error.status === 400) return 'This Escalate now link is invalid. Open the complete link from your email.'
  }
  return error?.message ?? 'AlertOps could not load this Escalate now link.'
}

function CompletedAction({ result }: { result: EscalationManualAction }) {
  return <div className="notice notice-success" role="status">
    <strong>Next notification scheduled</strong>
    <div>AlertOps removed the wait for the next response step in “{result.escalationName}”.</div>
    {result.targetRecipientEmail && <small>The next notification is assigned to {result.targetRecipientEmail}.</small>}
  </div>
}

export function EscalateNowPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const preview = useQuery({
    queryKey: ['escalate-now-preview', token],
    queryFn: () => previewRecipientEscalateNow(token),
    enabled: Boolean(token),
    retry: (failureCount, error) => !(error instanceof ApiError && error.status < 500) && failureCount < 1,
  })
  const confirm = useMutation({ mutationFn: () => confirmRecipientEscalateNow(token) })
  const completed = confirm.data ?? (preview.data?.alreadyEscalated ? preview.data : null)

  return <div className="auth-page">
    <Link className="brand" to="/"><span className="brand-icon"><b /><b /><b /></span><span>ALERT<span>OPS</span></span></Link>
    <div className="auth-card acknowledgement-card">
      <span className="eyebrow">ESCALATION RESPONSE</span>
      <h1>{completed ? 'Next step scheduled' : 'Escalate this alert now'}</h1>
      <p>This removes the current wait and notifies the next person in the saved escalation path.</p>

      {!token && <div className="form-error" role="alert">The Escalate now link is missing its token. Open the complete link from your email.</div>}
      {token && preview.isPending && <p role="status">Checking this secure Escalate now link…</p>}
      {token && preview.isError && <div className="form-error" role="alert">{manualActionError(preview.error)}</div>}

      {preview.data && <div className="acknowledgement-details">
        <span>ESCALATION</span><strong>{preview.data.escalationName}</strong>
        <span>NEXT RECIPIENT</span><strong>{preview.data.targetRecipientEmail ?? 'Unavailable'}</strong>
      </div>}

      {completed
        ? <CompletedAction result={completed} />
        : preview.data && !preview.error && <>
            {!preview.data.actionAvailable && <div className="form-error" role="alert">{preview.data.unavailableReason ?? 'This action is no longer available.'}</div>}
            {preview.data.actionAvailable && <>
              {confirm.error && <div className="form-error" role="alert">{manualActionError(confirm.error)}</div>}
              <Button className="button-full" disabled={confirm.isPending} onClick={() => confirm.mutate()}>
                {confirm.isPending ? 'Scheduling next step…' : 'Confirm and escalate now'}
              </Button>
            </>}
          </>}
      <div className="acknowledgement-footnote">This link is scoped to the current response step. The saved response deadline is {preview.data?.actionDeadline ? formatDate(preview.data.actionDeadline) : 'not available'}.</div>
    </div>
    <div className="auth-caption">ALERTOPS <i /> DURABLE, ORDERED ESCALATION</div>
  </div>
}
