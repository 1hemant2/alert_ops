import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { acknowledgeEscalation, previewEscalationAcknowledgement } from '../../api/escalations'
import { ApiError } from '../../api/client'
import type { EscalationAcknowledgement } from '../../api/types'
import { Button } from '../../components/Elements'
import { formatDate } from '../../lib/format'

function acknowledgementError(error: Error | null): string {
  if (error instanceof ApiError) {
    if (error.status === 410) return 'This acknowledgement link has expired. Ask your team to send a new alert.'
    if (error.status === 409) return 'This escalation is no longer active, or another recipient already acknowledged it.'
    if (error.status === 400) return 'This acknowledgement link is invalid. Open the link from the original email.'
  }
  return error?.message ?? 'AlertOps could not load this acknowledgement link.'
}

function AcknowledgedMessage({ result }: { result: EscalationAcknowledgement }) {
  return <div className="notice notice-success" role="status">
    <strong>Escalation acknowledged</strong>
    <div>Further response steps have been stopped for “{result.escalationName}”.</div>
    {result.acknowledgedAt && <small>Acknowledged by {result.acknowledgedBy ?? result.recipientEmail} at {formatDate(result.acknowledgedAt)}.</small>}
  </div>
}

export function AcknowledgeEscalationPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const preview = useQuery({
    queryKey: ['escalation-acknowledgement-preview', token],
    queryFn: () => previewEscalationAcknowledgement(token),
    enabled: Boolean(token),
    retry: (failureCount, error) => !(error instanceof ApiError && error.status < 500) && failureCount < 1,
  })
  const confirm = useMutation({ mutationFn: () => acknowledgeEscalation(token) })
  const completedAcknowledgement = confirm.data ?? (preview.data?.alreadyAcknowledged ? preview.data : null)

  return <div className="auth-page">
    <Link className="brand" to="/"><span className="brand-icon"><b /><b /><b /></span><span>ALERT<span>OPS</span></span></Link>
    <div className="auth-card acknowledgement-card">
      <span className="eyebrow">ESCALATION RESPONSE</span>
      <h1>{completedAcknowledgement ? 'Acknowledgement saved' : 'Acknowledge this escalation'}</h1>
      <p>This confirmation stops the remaining response steps for this run.</p>

      {!token && <div className="form-error" role="alert">The acknowledgement link is missing its token. Open the complete link from your email.</div>}
      {token && preview.isPending && <p role="status">Checking this secure acknowledgement link…</p>}
      {token && preview.isError && <div className="form-error" role="alert">{acknowledgementError(preview.error)}</div>}

      {preview.data && <div className="acknowledgement-details">
        <span>ESCALATION</span><strong>{preview.data.escalationName}</strong>
        <span>ASSIGNED EMAIL</span><strong>{preview.data.recipientEmail}</strong>
      </div>}

      {completedAcknowledgement
        ? <AcknowledgedMessage result={completedAcknowledgement} />
        : preview.data && !preview.error && <>
            {confirm.error && <div className="form-error" role="alert">{acknowledgementError(confirm.error)}</div>}
            <Button className="button-full" disabled={confirm.isPending} onClick={() => confirm.mutate()}>
              {confirm.isPending ? 'Saving acknowledgement…' : 'Confirm and stop escalation'}
            </Button>
          </>}
      <div className="acknowledgement-footnote">This link is for {preview.data?.recipientEmail ?? 'the person assigned to this step'} and expires {preview.data ? formatDate(preview.data.expiresAt) : 'after a limited time'}.</div>
    </div>
    <div className="auth-caption">ALERTOPS <i /> DURABLE, ORDERED ESCALATION</div>
  </div>
}
