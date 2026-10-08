import { Link, Navigate } from 'react-router'
import { useSession } from '../app/useSession'

// Renders the public ReplyTrail landing page.
export function PublicPage() {
  const { token, team } = useSession()
  if (token && team) return <Navigate to={`/app/${team.id}`} replace />

  return (
    <div className="public-page">
      <header className="public-header">
        <Link className="brand brand-light" to="/"><span className="brand-icon"><b /><b /><b /></span><span>REPLY<span>TRAIL</span></span></Link>
        <div><Link className="public-login" to="/login">Sign in</Link><Link className="button button-lime" to={token ? '/teams' : '/register'}>{token ? 'Open workspace' : 'Create account'} <span>↗</span></Link></div>
      </header>
      <main className="public-main">
        <div className="public-copy">
          <div className="eyebrow eyebrow-lime"><span className="live-dot" /> INCIDENT RESPONSE, WITHOUT THE CHAOS</div>
          <h1>Get the right people<br /><em>on every alert.</em></h1>
          <p>Build team-specific escalation paths, set clear handoff timing, and follow each alert from its first notification through resolution.</p>
          <div className="public-actions"><Link className="button button-lime button-large" to={token ? '/teams' : '/register'}>{token ? 'Open workspace' : 'Create account'} <span>→</span></Link><a href="#how-it-works" className="text-link">See how it works <span>↓</span></a></div>
          <div className="public-footnote"><span>TEAM WORKSPACES</span><i /> <span>ORDERED NOTIFICATIONS</span></div>
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
      <footer className="public-footer"><span>REPLYTRAIL <i /> KEEP EVERY RESPONSE ON TRACK.</span><span>BUILT FOR RESPONSE TEAMS</span></footer>
    </div>
  )
}
