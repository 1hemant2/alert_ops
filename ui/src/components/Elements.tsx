import { useState, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode } from 'react'
import { Link } from 'react-router'

export function Button({
  children,
  className = '',
  variant = 'primary',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'quiet' | 'danger' }) {
  return <button className={`button button-${variant} ${className}`} {...props}>{children}</button>
}

export function PageHeader({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string
  title: string
  description?: string
  action?: ReactNode
}) {
  return (
    <div className="page-header">
      <div>
        <div className="eyebrow">{eyebrow}</div>
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      {action && <div className="page-header-action">{action}</div>}
    </div>
  )
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <section className={`card ${className}`}>{children}</section>
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string
  description: string
  action?: ReactNode
}) {
  return (
    <div className="empty-state">
      <span className="empty-mark">＋</span>
      <h3>{title}</h3>
      <p>{description}</p>
      {action}
    </div>
  )
}

export function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="error-state" role="alert">
      <span className="error-dot" />
      <div><strong>Couldn’t load this data</strong><p>{message}</p></div>
      <Button variant="secondary" onClick={onRetry}>Try again</Button>
    </div>
  )
}

export function LoadingRows({ count = 3 }: { count?: number }) {
  return <div className="loading-rows" aria-label="Loading">{Array.from({ length: count }, (_, index) => <i key={index} />)}</div>
}

export function StatusBadge({ status }: { status: string }) {
  const normalized = status.toLowerCase()
  return <span className={`status-badge status-${normalized}`}><i />{status.replaceAll('_', ' ')}</span>
}

export function LinkButton({ to, children }: { to: string; children: ReactNode }) {
  return <Link className="button button-secondary" to={to}>{children}</Link>
}

export function InlineNotice({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'warning' | 'success' | 'error' }) {
  return <div className={`notice notice-${tone}`}>{children}</div>
}

export function Field({
  label,
  hint,
  children,
}: {
  label: string
  hint?: string
  children: ReactNode
}) {
  return <label className="field"><span>{label}</span>{children}{hint && <small>{hint}</small>}</label>
}

// Lets users verify a password while preserving masked input by default.
export function PasswordInput(props: InputHTMLAttributes<HTMLInputElement>) {
  const [isVisible, setIsVisible] = useState(false)

  return (
    <span className="password-input-shell">
      <input {...props} type={isVisible ? 'text' : 'password'} />
      <button
        type="button"
        className="password-toggle"
        aria-label={isVisible ? 'Hide password' : 'Show password'}
        aria-pressed={isVisible}
        onClick={() => setIsVisible(previous => !previous)}
      >
        <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          {isVisible ? <><path d="M3 3l18 18" /><path d="M10.6 10.7a2 2 0 0 0 2.7 2.7" /><path d="M9.9 4.3A10.8 10.8 0 0 1 12 4c5.2 0 8.7 4 10 8-.5 1.5-1.3 2.8-2.4 4" /><path d="M6.6 6.6C4.6 7.9 3.3 10 2 12c1.3 4 4.8 8 10 8 1.7 0 3.2-.4 4.5-1.1" /></> : <><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6-10-6-10-6Z" /><circle cx="12" cy="12" r="2.5" /></>}
        </svg>
      </button>
    </span>
  )
}
