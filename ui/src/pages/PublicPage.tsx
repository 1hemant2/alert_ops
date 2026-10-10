import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router'
import { useSession } from '../app/useSession'
import { ThemeToggle } from '../components/ThemeToggle'

type UseCase = {
  icon: string
  category: string
  title: string
  description: string
  scenario: string
  primary: string
  fallback: string
  timing: string
  result: string
}

const useCases: UseCase[] = [
  { icon: '↗', category: 'OPERATIONS', title: 'Website or app problems', description: 'Notify the person who looks after a service, then their team lead if help is still needed.', scenario: 'An API latency alert fires while the primary on-call is away from their desk.', primary: 'Primary on-call', fallback: 'Platform lead', timing: '5 minutes', result: 'The incident keeps moving until someone acknowledges it or the path is complete.' },
  { icon: '✦', category: 'CUSTOMER SUCCESS', title: 'New customer help', description: 'Ask someone to help a customer get set up, with a clear next person if they are unavailable.', scenario: 'A new account needs hands-on setup help before the customer kickoff.', primary: 'Customer success owner', fallback: 'Onboarding lead', timing: '30 minutes', result: 'The customer gets a visible owner instead of an inbox thread with no next step.' },
  { icon: '⌘', category: 'IT SERVICE DESK', title: 'IT help', description: 'Follow up on login issues, access requests, and the work that keeps everyone moving.', scenario: 'A teammate is blocked from a work tool and needs a response before their next shift.', primary: 'IT support', fallback: 'IT lead', timing: '15 minutes', result: 'Access requests get an accountable owner and a clear escalation path.' },
  { icon: '◌', category: 'DATA & JOBS', title: 'Failed jobs', description: 'Bring the right owner to a failed import, background job, or data pipeline.', scenario: 'A nightly import fails and the data team needs to investigate before morning.', primary: 'Data engineer', fallback: 'Engineering lead', timing: '10 minutes', result: 'The failure becomes a tracked task with context, ownership, and history.' },
  { icon: '↔', category: 'SUPPORT', title: 'Unanswered requests', description: 'Keep support work visible when a customer is still waiting for help.', scenario: 'A high-priority support request has not received a reply within the promised window.', primary: 'Support owner', fallback: 'Support manager', timing: '20 minutes', result: 'The customer request stays visible until it is acknowledged or resolved.' },
  { icon: '⟳', category: 'RELIABILITY', title: 'Failed backups', description: 'Make sure someone checks a backup that failed or could not be restored.', scenario: 'A backup job reports failure and needs a human check before the next run.', primary: 'Backup owner', fallback: 'Reliability lead', timing: '10 minutes', result: 'A failed backup is treated as work to resolve, not a notification to ignore.' },
  { icon: '◆', category: 'SECURITY', title: 'Security problems', description: 'Route a reported security issue to the person responsible for reviewing it.', scenario: 'A suspicious event needs a security review with a documented handoff.', primary: 'Security reviewer', fallback: 'Security lead', timing: '5 minutes', result: 'Sensitive follow-up has a durable owner trail and escalation history.' },
  { icon: '$', category: 'FINANCE', title: 'Payment or invoice issues', description: 'Give billing problems a clear owner and a timely follow-up path.', scenario: 'A failed payment needs billing attention before a customer account is affected.', primary: 'Billing owner', fallback: 'Finance lead', timing: '1 hour', result: 'Billing follow-up gets the same accountable workflow as an operational alert.' },
  { icon: '↻', category: 'RENEWALS', title: 'Upcoming renewals', description: 'Remind the right person before a certificate, contract, or subscription expires.', scenario: 'A certificate or contract needs an owner before its renewal date arrives.', primary: 'Account owner', fallback: 'Operations lead', timing: '1 day', result: 'Time-sensitive reminders are scheduled once and tracked to completion.' },
  { icon: '→', category: 'PROCUREMENT', title: 'Late deliveries', description: 'Prompt purchasing to follow up when a supplier misses an expected delivery.', scenario: 'A supplier misses a delivery date and the team needs an update from purchasing.', primary: 'Purchasing owner', fallback: 'Procurement lead', timing: '4 hours', result: 'The missed delivery creates a next action instead of disappearing in a spreadsheet.' },
  { icon: '+', category: 'WORKPLACE', title: 'Office repairs', description: 'Keep broken equipment and maintenance requests from quietly getting lost.', scenario: 'A broken printer or room needs a facilities follow-up before the next workday.', primary: 'Facilities owner', fallback: 'Office manager', timing: '1 day', result: 'Small workplace requests get a lightweight path without a heavy ticketing system.' },
  { icon: '◷', category: 'PLANNED FOLLOW-UP', title: 'Future follow-ups', description: 'Schedule a one-time reminder to check back on a task later.', scenario: 'A decision is pending and someone needs a reminder to check back next week.', primary: 'Task owner', fallback: 'Team lead', timing: 'Next week', result: 'A scheduled start turns a future promise into a visible, reliable follow-up.' },
  { icon: '✓', category: 'PERSONAL', title: 'Personal reminders', description: 'Remind yourself about the same task once a day for as long as you need.', scenario: 'You need a daily nudge for a finite sequence of reminders over ten days.', primary: 'You', fallback: 'You again', timing: '24 hours', result: 'Repeated reminders stop when you acknowledge or the configured sequence ends.' },
]

type FeatureGuideItem = {
  icon: string
  title: string
  description: string
  detailLabel: string
  detail: string
}

const featureGuideItems: FeatureGuideItem[] = [
  { icon: '◎', title: 'Teams keep work separated', description: 'Work inside an explicit team with verified members, shared tasks, response paths, webhooks, and runs.', detailLabel: 'Team boundary', detail: 'The server checks access' },
  { icon: '▤', title: 'Tasks keep the context', description: 'Give every request a name, description, source, and optional priority, category, or reference link.', detailLabel: 'Reusable context', detail: 'Saved with every run' },
  { icon: '↯', title: 'Paths define the handoff', description: 'Choose team members in order and decide how long each response step should wait.', detailLabel: 'Ordered ownership', detail: 'One path, many runs' },
  { icon: '◇', title: 'Runs follow an explicit lifecycle', description: 'Every run moves through named states so your team can see whether it is open, acknowledged, resolved, or complete.', detailLabel: 'Clear state', detail: 'No hidden transitions' },
  { icon: '◷', title: 'Runs start on your terms', description: 'Start immediately, schedule a future time with its timezone, or repeat a schedule daily or weekly.', detailLabel: 'Flexible starts', detail: 'Now, later, or recurring' },
  { icon: '✓', title: 'Email actions make ownership explicit', description: 'Recipients preview and confirm acknowledgement, then resolve the issue when a resolution window is enabled.', detailLabel: 'Clear responsibility', detail: 'Acknowledge or resolve' },
  { icon: '→', title: 'Escalate now moves the next step', description: 'An eligible team member or recipient can make the next response step due without resending the previous email.', detailLabel: 'Manual control', detail: 'Advance the next step' },
  { icon: '⌁', title: 'Webhooks connect external systems', description: 'An authenticated event creates one task and one run, with safe retries when the same event is sent again.', detailLabel: 'Idempotent intake', detail: 'One event, one run' },
  { icon: '≡', title: 'History shows every milestone', description: 'Review saved user and system activity, including delivery attempts, acknowledgement, resolution, and completion.', detailLabel: 'Investigation ready', detail: 'A durable timeline' },
  { icon: '↻', title: 'Recovery keeps work moving', description: 'Saved workflow state lets the application recover scheduled work and ignore stale or duplicate callbacks.', detailLabel: 'Restart safe', detail: 'PostgreSQL stays authoritative' },
]

const signedInSteps = [
  { number: '01', title: 'Primary on-call', detail: 'ops@replytrail.dev', time: 'ACKNOWLEDGED', state: 'done' },
  { number: '02', title: 'Platform lead', detail: 'maya@replytrail.dev', time: '+ 5 MIN', state: 'active' },
  { number: '03', title: 'Incident commander', detail: 'team escalation', time: '+ 15 MIN', state: 'queued' },
] as const

// Renders the public ReplyTrail landing page.
export function PublicPage() {
  const { token, team } = useSession()
  const [selectedUseCase, setSelectedUseCase] = useState<UseCase | null>(null)

  // Keeps the public use-case dialog dismissible without a mouse.
  useEffect(() => {
    if (!selectedUseCase) return
    // Closes the selected use-case detail dialog when Escape is pressed.
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setSelectedUseCase(null)
    }
    window.addEventListener('keydown', handleEscape)
    return () => window.removeEventListener('keydown', handleEscape)
  }, [selectedUseCase])

  if (token && team) return <Navigate to={`/app/${team.id}`} replace />

  return (
    <div className="public-page">
      <header className="public-header">
        <Link className="brand brand-light" to="/"><span className="brand-icon"><b /><b /><b /></span><span>REPLY<span>TRAIL</span></span></Link>
        <div><a className="public-nav-link" href="#features">Features</a><ThemeToggle /><Link className="public-login" to="/login">Sign in</Link><Link className="button button-lime" to={token ? '/teams' : '/register'}>{token ? 'Open workspace' : 'Create account'} <span>↗</span></Link></div>
      </header>
      <main className="public-main">
        <div className="public-copy">
          <div className="eyebrow eyebrow-lime"><span className="live-dot" /> INCIDENT RESPONSE, WITHOUT THE CHAOS</div>
          <h1>Get the right people<br /><em>on every alert.</em></h1>
          <p>Turn alerts, requests, and reminders into clear follow-up paths with an owner, a waiting time, and a next step.</p>
          <div className="public-actions"><Link className="button button-lime button-large" to={token ? '/teams' : '/register'}>{token ? 'Open workspace' : 'Create account'} <span>→</span></Link><a href="#features" className="text-link">See the features <span>↓</span></a><a href="#use-cases" className="text-link">Explore use cases <span>↓</span></a></div>
          <div className="public-footnote"><span>ALERTS</span><i /> <span>REQUESTS</span><i /> <span>REMINDERS</span></div>
          <div className="hero-proof-row" aria-label="Workflow benefits"><span><i>✓</i> Clear owner</span><span><i>↗</i> Timed handoff</span><span><i>↻</i> Restart-safe</span></div>
        </div>
        <div className="hero-console" aria-label="Illustration of an ordered escalation timeline">
          <div className="console-top"><span className="console-lights"><i /><i /><i /></span><span>PATH PREVIEW / API LATENCY</span><span className="console-state"><i /> PREVIEW</span></div>
          <div className="console-inner">
            <div className="console-kicker">ESCALATION SEQUENCE <span>01—03</span></div>
            <div className="console-node done"><div className="node-marker">✓</div><div><strong>Primary on-call</strong><small>ops@northstar.dev</small></div><span className="node-time">NOW</span></div>
            <div className="console-connector"><span /></div>
            <div className="console-node active-node"><div className="node-marker">02</div><div><strong>Platform lead</strong><small>maya@northstar.dev</small></div><span className="node-time">+ 5 MIN</span></div>
            <div className="console-connector"><span /></div>
            <div className="console-node waiting"><div className="node-marker">03</div><div><strong>Incident commander</strong><small>team escalation</small></div><span className="node-time">+ 15 MIN</span></div>
            <div className="console-footer"><span><i /> DURABLE STATE</span><span>RESTART SAFE&nbsp; ↗</span></div>
          </div>
          <div className="floating-chip"><span className="chip-check">✓</span><span><strong>Durable execution state</strong><small>resumes after a restart</small></span></div>
        </div>
      </main>
      <section id="how-it-works" className="public-explainer">
        <div><span className="eyebrow">BUILT FOR THE MOMENT THAT MATTERS</span><h2>Clear ownership.<br />Reliable follow-through.</h2></div>
        <div className="explainer-steps">
          <article><span>01</span><h3>Configure</h3><p>Set the incident context and choose who should be notified.</p></article>
          <article><span>02</span><h3>Coordinate</h3><p>Control notification order and response timing in one escalation path.</p></article>
          <article><span>03</span><h3>Recover</h3><p>Active escalations resume from saved progress after a restart.</p></article>
        </div>
        <div className="delivery-note"><span>EMAIL NOTIFICATIONS</span> Each configured recipient gets a formatted escalation email. SENT means the email service accepted it; it does not confirm delivery.</div>
      </section>
      <section id="product-preview" className="public-product-tour" aria-labelledby="product-preview-heading">
        <div className="product-tour-copy">
          <span className="eyebrow product-tour-eyebrow">AFTER SIGN-IN / ESCALATION RUN</span>
          <h2 id="product-preview-heading">See the handoff,<br /><em>not just the alert.</em></h2>
          <p>Inside the workspace, every run has a task, a response path, and a visible next step. Your team can see who was notified, who acknowledged, and who is next.</p>
          <a className="product-tour-link" href="#use-cases">Open a workflow story <span>↓</span></a>
        </div>
        <div className="product-window" aria-label="Preview of a signed-in ReplyTrail escalation run">
          <div className="product-window-top"><span className="console-lights"><i /><i /><i /></span><span>REPLYTRAIL / WORKSPACE</span><span className="product-window-live"><i /> ACTIVE RUN</span></div>
          <div className="product-window-body">
            <aside className="product-window-sidebar" aria-label="Workspace navigation preview">
              <div className="product-window-brand"><span className="brand-icon"><b /><b /><b /></span><strong>REPLYTRAIL</strong></div>
              <span className="product-window-section">WORKSPACE</span>
              <span className="product-window-nav active"><i>◈</i> Overview</span>
              <span className="product-window-nav"><i>◎</i> Tasks</span>
              <span className="product-window-nav"><i>↯</i> Escalations</span>
              <span className="product-window-nav"><i>⌁</i> Response paths</span>
            </aside>
            <div className="product-window-main">
              <div className="product-window-heading"><div><span className="product-window-kicker">ESCALATION RUN / ACTIVE</span><h3>API latency spike</h3><p>Production API · created 2 minutes ago</p></div><span className="product-status-badge">IN PROGRESS</span></div>
              <div className="product-run-summary"><span><small>OWNER</small><strong>Platform team</strong></span><span><small>PATH</small><strong>API reliability</strong></span><span><small>NEXT WAIT</small><strong>5 minutes</strong></span></div>
              <div className="product-step-list">
                {signedInSteps.map((step) => (
                  <div className={`product-step product-step-${step.state}`} key={step.number}>
                    <span className="product-step-marker">{step.state === 'done' ? '✓' : step.number}</span>
                    <span className="product-step-copy"><strong>{step.title}</strong><small>{step.detail}</small></span>
                    <span className="product-step-time">{step.time}</span>
                  </div>
                ))}
              </div>
              <div className="product-window-bottom"><span><i /> SAVED TO ACTIVITY</span><span>ACKNOWLEDGE&nbsp; →</span></div>
            </div>
          </div>
        </div>
      </section>
      <section id="features" className="public-features" aria-labelledby="features-heading">
        <div className="feature-guide-heading">
          <div>
            <span className="eyebrow feature-guide-eyebrow">THE REPLYTRAIL FEATURE GUIDE</span>
            <h2 id="features-heading">Everything that keeps<br /><em>the handoff moving.</em></h2>
          </div>
          <p>ReplyTrail gives your team one place to define the work, route the response, and understand what happened next.</p>
        </div>
        <div className="feature-guide-grid">
          {featureGuideItems.map((feature, index) => (
            <article className="feature-guide-card" key={feature.title}>
              <div className="feature-guide-card-top"><span className="feature-guide-index">0{index + 1}</span><span className="feature-guide-icon" aria-hidden="true">{feature.icon}</span></div>
              <div className="feature-guide-card-copy"><h3>{feature.title}</h3><p>{feature.description}</p></div>
              <div className="feature-guide-card-detail"><span>{feature.detailLabel}</span><strong>{feature.detail}</strong></div>
            </article>
          ))}
        </div>
        <div className="feature-guide-boundary"><span>PRODUCT BOUNDARY</span><p>ReplyTrail currently sends email. SMS, phone calls, push notifications, native Slack or Teams delivery, rotating on-call calendars, and test escalations are not part of the current product.</p></div>
      </section>
      <section id="use-cases" className="public-use-cases" aria-labelledby="use-cases-heading">
        <div className="use-cases-heading">
          <div>
            <span className="eyebrow use-cases-eyebrow">ONE WORKFLOW / MANY FOLLOW-UPS</span>
            <h2 id="use-cases-heading">Choose a problem.<br />See the path.</h2>
          </div>
          <div className="use-cases-intro"><p>Click any story to see how ReplyTrail turns it into a task, a timed handoff, and a clear finish line.</p><div className="use-cases-stat"><strong>13</strong><span>ready-to-run<br />follow-up paths</span></div></div>
        </div>
        <div className="use-case-grid">
          {useCases.map((useCase) => (
            <button className="use-case-card" key={useCase.title} type="button" onClick={() => setSelectedUseCase(useCase)} aria-label={`Explore the ${useCase.title} workflow`}>
              <span className="use-case-card-top"><span className="use-case-icon" aria-hidden="true">{useCase.icon}</span><span className="use-case-category">{useCase.category}</span></span>
              <span className="use-case-card-copy"><strong>{useCase.title}</strong><span>{useCase.description}</span></span>
              <span className="use-case-card-footer"><span>Explore the path</span><span aria-hidden="true">↗</span></span>
            </button>
          ))}
        </div>
        <div className="use-cases-footer"><span>START WITH A TASK</span><i /> Choose your recipients, set the wait, and start now or schedule it for later.</div>
      </section>
      {selectedUseCase && (
        <div className="use-case-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setSelectedUseCase(null) }}>
          <section className="use-case-dialog" role="dialog" aria-modal="true" aria-labelledby="use-case-dialog-title" aria-describedby="use-case-dialog-description">
            <button className="use-case-dialog-close" type="button" onClick={() => setSelectedUseCase(null)} aria-label="Close workflow details">×</button>
            <div className="use-case-dialog-copy">
              <span className="eyebrow use-case-dialog-eyebrow">{selectedUseCase.category} / WORKFLOW STORY</span>
              <h2 id="use-case-dialog-title">{selectedUseCase.title}</h2>
              <p id="use-case-dialog-description">{selectedUseCase.scenario}</p>
              <div className="use-case-dialog-result"><span>WHAT REPLYTRAIL DOES</span><strong>{selectedUseCase.result}</strong></div>
              <Link className="button button-lime" to={token ? '/teams' : '/register'}>Build a path like this <span>↗</span></Link>
            </div>
            <div className="use-case-dialog-workflow" aria-label={`${selectedUseCase.title} escalation steps`}>
              <div className="dialog-workflow-top"><span>AFTER SIGN-IN / RESPONSE PATH</span><span>3 STEPS</span></div>
              <div className="dialog-task-card"><span className="dialog-task-icon">{selectedUseCase.icon}</span><span><small>TASK CREATED</small><strong>{selectedUseCase.title}</strong></span><span className="dialog-task-status">OPEN</span></div>
              <div className="dialog-path-list">
                <div className="dialog-path-line"><span className="dialog-path-dot done">✓</span><span><strong>{selectedUseCase.primary}</strong><small>First owner notified · now</small></span><em>NOW</em></div>
                <div className="dialog-path-line"><span className="dialog-path-dot active">02</span><span><strong>{selectedUseCase.fallback}</strong><small>Next handoff if nobody responds</small></span><em>+ {selectedUseCase.timing}</em></div>
                <div className="dialog-path-line"><span className="dialog-path-dot">03</span><span><strong>Acknowledgement or resolution</strong><small>Stop the path when the work is accepted</small></span><em>DONE</em></div>
              </div>
              <div className="dialog-workflow-footer"><span><i /> DURABLE ACTIVITY</span><span>RESTART SAFE&nbsp; ↗</span></div>
            </div>
          </section>
        </div>
      )}
      <footer className="public-footer"><span>REPLYTRAIL <i /> KEEP EVERY RESPONSE ON TRACK.</span><span>BUILT FOR RESPONSE TEAMS</span></footer>
    </div>
  )
}
