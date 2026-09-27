import { Link, Navigate } from 'react-router'
import { useSession } from '../app/useSession'

export function PublicPage() {
  const { token, team } = useSession()
  if (token && team) return <Navigate to={`/app/${team.id}`} replace />

  return (
    <div className="public-page">
      <header className="public-header">
        <Link className="brand brand-light" to="/"><span className="brand-icon"><b /><b /><b /></span><span>ALERT<span>OPS</span></span></Link>
        <div><Link className="public-login" to="/login">Sign in</Link><Link className="button button-lime" to={token ? '/teams' : '/register'}>{token ? 'Open workspace' : 'Create account'} <span>↗</span></Link></div>
      </header>
      <main className="public-main">
        <div className="public-copy">
          <div className="eyebrow eyebrow-lime"><span className="live-dot" /> A QUEUE-DRIVEN ESCALATION ENGINE</div>
          <h1>When time matters,<br /><em>make every step count.</em></h1>
          <p>AlertOps models an incident response as a durable, ordered workflow. Configure who gets notified and when, then let the queue carry each step forward.</p>
          <div className="public-actions"><Link className="button button-lime button-large" to={token ? '/teams' : '/register'}>{token ? 'Enter control room' : 'Start the demo'} <span>→</span></Link><a href="#how-it-works" className="text-link">See how it works <span>↓</span></a></div>
          <div className="public-footnote"><span>JAVA · SPRING BOOT</span><i /> <span>POSTGRES · RABBITMQ · REDIS</span></div>
        </div>
        <div className="hero-console" aria-label="Illustration of an ordered escalation timeline">
          <div className="console-top"><span className="console-lights"><i /><i /><i /></span><span>ILLUSTRATIVE FLOW / API LATENCY</span><span className="console-state"><i /> SAMPLE</span></div>
          <div className="console-inner">
            <div className="console-kicker">ESCALATION SEQUENCE <span>01—03</span></div>
            <div className="console-node done"><div className="node-marker">✓</div><div><strong>Primary on-call</strong><small>ops@northstar.dev</small></div><span className="node-time">NOW</span></div>
            <div className="console-connector"><span /></div>
            <div className="console-node active-node"><div className="node-marker">02</div><div><strong>Platform lead</strong><small>maya@northstar.dev</small></div><span className="node-time">+ 5 MIN</span></div>
            <div className="console-connector"><span /></div>
            <div className="console-node waiting"><div className="node-marker">03</div><div><strong>Incident commander</strong><small>team escalation</small></div><span className="node-time">+ 15 MIN</span></div>
            <div className="console-footer"><span><i /> DURABLE STATE</span><span>RESTART SAFE&nbsp; ↗</span></div>
          </div>
          <div className="floating-chip"><span className="chip-check">✓</span><span><strong>Example saved state</strong><small>resume after application restart</small></span></div>
        </div>
      </main>
      <section id="how-it-works" className="public-explainer">
        <div><span className="eyebrow">BUILT FOR THE FAILURE PATH</span><h2>Escalations are workflows.<br />Treat them that way.</h2></div>
        <div className="explainer-steps">
          <article><span>01</span><h3>Configure</h3><p>Define a task and an ordered list of recipients with their delays.</p></article>
          <article><span>02</span><h3>Persist</h3><p>Store each execution step before publishing delayed work to RabbitMQ.</p></article>
          <article><span>03</span><h3>Resume</h3><p>Recover running work from saved state when the application starts again.</p></article>
        </div>
        <div className="simulation-note"><span>DEMO NOTE</span> Notification delivery is simulated in this project; the consumer records the send attempt in application logs.</div>
      </section>
      <footer className="public-footer"><span>ALERTOPS <i /> RELIABLE ESCALATION, MADE EXPLICIT.</span><span>AN ENGINEERING DEMONSTRATION</span></footer>
    </div>
  )
}
