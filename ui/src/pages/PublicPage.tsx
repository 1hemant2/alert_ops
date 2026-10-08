import { Link, Navigate } from 'react-router'
import { useSession } from '../app/useSession'
import { ThemeToggle } from '../components/ThemeToggle'

const useCases = [
  { icon: '↗', title: 'Website or app problems', description: 'Notify the person who looks after a service, then their team lead if help is still needed.' },
  { icon: '✦', title: 'New customer help', description: 'Ask someone to help a customer get set up, with a clear next person if they are unavailable.' },
  { icon: '⌘', title: 'IT help', description: 'Follow up on login issues, access requests, and the work that keeps everyone moving.' },
  { icon: '◌', title: 'Failed jobs', description: 'Bring the right owner to a failed import, background job, or data pipeline.' },
  { icon: '↔', title: 'Unanswered requests', description: 'Keep support work visible when a customer is still waiting for help.' },
  { icon: '⟳', title: 'Failed backups', description: 'Make sure someone checks a backup that failed or could not be restored.' },
  { icon: '◆', title: 'Security problems', description: 'Route a reported security issue to the person responsible for reviewing it.' },
  { icon: '$', title: 'Payment or invoice issues', description: 'Give billing problems a clear owner and a timely follow-up path.' },
  { icon: '↻', title: 'Upcoming renewals', description: 'Remind the right person before a certificate, contract, or subscription expires.' },
  { icon: '→', title: 'Late deliveries', description: 'Prompt purchasing to follow up when a supplier misses an expected delivery.' },
  { icon: '+', title: 'Office repairs', description: 'Keep broken equipment and maintenance requests from quietly getting lost.' },
  { icon: '◷', title: 'Future follow-ups', description: 'Schedule a one-time reminder to check back on a task later.' },
  { icon: '✓', title: 'Personal reminders', description: 'Remind yourself about the same task once a day for as long as you need.' },
] as const

// Renders the public ReplyTrail landing page.
export function PublicPage() {
  const { token, team } = useSession()
  if (token && team) return <Navigate to={`/app/${team.id}`} replace />

  return (
    <div className="public-page">
      <header className="public-header">
        <Link className="brand brand-light" to="/"><span className="brand-icon"><b /><b /><b /></span><span>REPLY<span>TRAIL</span></span></Link>
        <div><ThemeToggle /><Link className="public-login" to="/login">Sign in</Link><Link className="button button-lime" to={token ? '/teams' : '/register'}>{token ? 'Open workspace' : 'Create account'} <span>↗</span></Link></div>
      </header>
      <main className="public-main">
        <div className="public-copy">
          <div className="eyebrow eyebrow-lime"><span className="live-dot" /> INCIDENT RESPONSE, WITHOUT THE CHAOS</div>
          <h1>Get the right people<br /><em>on every alert.</em></h1>
          <p>Turn alerts, requests, and reminders into clear follow-up paths with an owner, a waiting time, and a next step.</p>
          <div className="public-actions"><Link className="button button-lime button-large" to={token ? '/teams' : '/register'}>{token ? 'Open workspace' : 'Create account'} <span>→</span></Link><a href="#use-cases" className="text-link">Explore use cases <span>↓</span></a></div>
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
      <section id="use-cases" className="public-use-cases" aria-labelledby="use-cases-heading">
        <div className="use-cases-heading">
          <div>
            <span className="eyebrow use-cases-eyebrow">ONE WORKFLOW / MANY FOLLOW-UPS</span>
            <h2 id="use-cases-heading">From broken builds<br />to future reminders.</h2>
          </div>
          <div className="use-cases-intro"><p>Whatever needs a response, give it a clear owner and a next step. ReplyTrail keeps the handoff visible until someone acknowledges the work or the sequence is complete.</p><div className="use-cases-stat"><strong>13</strong><span>ready-to-run<br />follow-up paths</span></div></div>
        </div>
        <div className="use-case-grid">
          {useCases.map((useCase) => (
            <article className="use-case-card" key={useCase.title}>
              <span className="use-case-icon" aria-hidden="true">{useCase.icon}</span>
              <div>
                <h3>{useCase.title}</h3>
                <p>{useCase.description}</p>
              </div>
            </article>
          ))}
        </div>
        <div className="use-cases-footer"><span>START WITH A TASK</span><i /> Choose your recipients, set the wait, and start now or schedule it for later.</div>
      </section>
      <footer className="public-footer"><span>REPLYTRAIL <i /> KEEP EVERY RESPONSE ON TRACK.</span><span>BUILT FOR RESPONSE TEAMS</span></footer>
    </div>
  )
}
