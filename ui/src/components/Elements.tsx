import type { ButtonHTMLAttributes, ReactNode } from 'react'
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
