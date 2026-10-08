import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { acknowledgeEscalation, previewEscalationAcknowledgement, resolveEscalationAsRecipient } from '../../api/escalations'
import { ApiError } from '../../api/client'
import type { EscalationAcknowledgement, EscalationResolution } from '../../api/types'
import { Button } from '../../components/Elements'
import { formatDate } from '../../lib/format'

function acknowledgementError(error: Error | null): string {
  if (error instanceof ApiError) {
    if (error.status === 410) return 'This acknowledgement window has closed. Ask your team to review the escalation.'
    if (error.status === 409) return 'This escalation has moved on, or another recipient already acknowledged it.'
    if (error.status === 400) return 'This acknowledgement link is invalid. Open the link from the original email.'
  }
  return error?.message ?? 'AlertOps could not load this acknowledgement link.'
}

function resolutionError(error: Error | null): string {
  if (error instanceof ApiError) {
    if (error.status === 409) return 'This resolution window has closed or the escalation was already advanced.'
    if (error.status === 400) return 'This resolution link is invalid. Open the link from the original email.'
  }
  return error?.message ?? 'AlertOps could not resolve this escalation.'
}

// Explains whether acknowledgement started an active resolution window.
function AcknowledgedMessage({ result }: { result: EscalationAcknowledgement }) {
  const waitingForResolution = result.status === 'ACKNOWLEDGED'
  return <div className="notice notice-success" role="status">
    <strong>{waitingForResolution ? 'Acknowledgement saved' : 'Escalation acknowledged'}</strong>
    <div>{waitingForResolution
      ? 'You now own the active resolution window for this escalation.'
      : `Further response steps have been stopped for “${result.escalationName}”.`}</div>
    {result.acknowledgedAt && <small>Acknowledged by {result.acknowledgedBy ?? result.recipientEmail} at {formatDate(result.acknowledgedAt)}.</small>}
  </div>
}

// Confirms that the active resolution window has ended successfully.
function ResolvedMessage({ result }: { result: EscalationResolution }) {
  return <div className="notice notice-success" role="status">
    <strong>Escalation resolved</strong>
    <div>AlertOps stopped the remaining response steps for “{result.escalationName}”.</div>
    {result.resolvedAt && <small>Resolved by {result.resolvedBy ?? 'the current recipient'} at {formatDate(result.resolvedAt)}.</small>}
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
  const acknowledge = useMutation({ mutationFn: () => acknowledgeEscalation(token) })
  const resolve = useMutation({ mutationFn: () => resolveEscalationAsRecipient(token) })
  const acknowledgedResult = acknowledge.data
    ?? (preview.data?.status === 'ACKNOWLEDGED' ? preview.data : null)
  const completedAcknowledgement = acknowledge.data?.status === 'COMPLETED'
    ? acknowledge.data
    : preview.data?.status === 'COMPLETED' && preview.data.alreadyAcknowledged
      ? preview.data
      : null

  return <div className="auth-page">
    <Link className="brand" to="/"><span className="brand-icon"><b /><b /><b /></span><span>ALERT<span>OPS</span></span></Link>
    <div className="auth-card acknowledgement-card">
      <span className="eyebrow">ESCALATION RESPONSE</span>
      <h1>{resolve.data ? 'Escalation resolved' : acknowledgedResult ? 'Resolve this escalation' : completedAcknowledgement ? 'Acknowledgement saved' : 'Acknowledge this escalation'}</h1>
      <p>{resolve.data
        ? 'The remaining response steps were stopped.'
        : acknowledgedResult
          ? 'Confirm when the issue is fixed. This ends your resolution window and stops the remaining response steps.'
          : 'This confirmation records that you are handling the current response step.'}</p>

      {!token && <div className="form-error" role="alert">The acknowledgement link is missing its token. Open the complete link from your email.</div>}
      {token && preview.isPending && <p role="status">Checking this secure acknowledgement link…</p>}
      {token && preview.isError && <div className="form-error" role="alert">{acknowledgementError(preview.error)}</div>}

      {preview.data && <div className="acknowledgement-details">
        <span>ESCALATION</span><strong>{preview.data.escalationName}</strong>
        <span>ASSIGNED EMAIL</span><strong>{preview.data.recipientEmail}</strong>
        {acknowledgedResult && <>
          <span>ACKNOWLEDGED BY</span><strong>{acknowledgedResult.acknowledgedBy ?? preview.data.recipientEmail}</strong>
          <span>RESOLUTION DEADLINE</span><strong>{acknowledgedResult.resolutionDeadline ? formatDate(acknowledgedResult.resolutionDeadline) : 'Saved deadline unavailable'}</strong>
        </>}
      </div>}

      {resolve.data
        ? <ResolvedMessage result={resolve.data} />
        : acknowledgedResult
          ? <>
              <AcknowledgedMessage result={acknowledgedResult} />
              {resolve.error && <div className="form-error" role="alert">{resolutionError(resolve.error)}</div>}
              <Button className="button-full" disabled={resolve.isPending} onClick={() => resolve.mutate()}>
                {resolve.isPending ? 'Resolving escalation…' : 'Confirm resolution'}
              </Button>
            </>
          : completedAcknowledgement
            ? <AcknowledgedMessage result={completedAcknowledgement} />
            : preview.data && preview.isSuccess && <>
                {acknowledge.error && <div className="form-error" role="alert">{acknowledgementError(acknowledge.error)}</div>}
                <Button className="button-full" disabled={acknowledge.isPending} onClick={() => acknowledge.mutate()}>
                  {acknowledge.isPending ? 'Saving acknowledgement…' : 'Confirm acknowledgement'}
                </Button>
              </>}
      <div className="acknowledgement-footnote">This link is for {preview.data?.recipientEmail ?? 'the person assigned to this step'} and expires {preview.data ? formatDate(preview.data.expiresAt) : 'after a limited time'}. {acknowledgedResult?.resolutionDeadline ? `Resolve before ${formatDate(acknowledgedResult.resolutionDeadline)}.` : ''}</div>
    </div>
    <div className="auth-caption">ALERTOPS <i /> DURABLE, ORDERED ESCALATION</div>
  </div>
}
