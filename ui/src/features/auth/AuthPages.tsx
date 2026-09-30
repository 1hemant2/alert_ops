import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router'
import { useMutation } from '@tanstack/react-query'
import { login, register, resendVerificationEmail, verifyEmail } from '../../api/auth'
import { ApiError } from '../../api/client'
import { useSession } from '../../app/useSession'
import { Button, Field, InlineNotice } from '../../components/Elements'

const PENDING_VERIFICATION_EMAIL_KEY = 'alertops.pending-verification-email'

export function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const { beginSession } = useSession()
  const navigate = useNavigate()
  const loginMutation = useMutation({
    mutationFn: () => login(email.trim(), password),
    onError: error => {
      if (error instanceof ApiError && error.status === 403) {
        const emailAddress = email.trim()
        sessionStorage.setItem(PENDING_VERIFICATION_EMAIL_KEY, emailAddress)
        navigate('/verify-email', { state: { email: emailAddress } })
      }
    },
    onSuccess: token => {
      beginSession(token)
      navigate(sessionStorage.getItem('alertops.pending-invite') ? '/join' : '/teams')
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    loginMutation.mutate()
  }

  return (
    <div className="auth-page">
      <Link className="brand" to="/"><span className="brand-icon"><b /><b /><b /></span><span>ALERT<span>OPS</span></span></Link>
      <div className="auth-card">
        <span className="eyebrow">WELCOME BACK</span><h1>Sign in to AlertOps</h1><p>Pick up where your incident workflows left off.</p>
        <form onSubmit={submit} className="form-stack">
          <Field label="Email"><input autoComplete="email" type="email" required value={email} onChange={event => setEmail(event.target.value)} placeholder="you@company.com" /></Field>
          <Field label="Password"><input autoComplete="current-password" type="password" required value={password} onChange={event => setPassword(event.target.value)} placeholder="Your password" /></Field>
          {loginMutation.error && <div role="alert" className="form-error">{loginMutation.error.message}</div>}
          <Button className="button-full" disabled={loginMutation.isPending}>{loginMutation.isPending ? 'Signing in…' : 'Sign in'} <span>→</span></Button>
        </form>
        <div className="auth-switch">New to AlertOps? <Link to="/register">Create an account</Link></div>
      </div>
      <div className="auth-caption">ALERTOPS <i /> DURABLE, ORDERED ESCALATION</div>
    </div>
  )
}

export function RegisterPage() {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const navigate = useNavigate()
  const registrationMutation = useMutation({
    mutationFn: () => register({ name: name.trim(), email: email.trim(), password }),
    onSuccess: () => {
      const emailAddress = email.trim()
      sessionStorage.setItem(PENDING_VERIFICATION_EMAIL_KEY, emailAddress)
      navigate('/verify-email', { state: { email: emailAddress } })
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    registrationMutation.mutate()
  }

  return (
    <div className="auth-page">
      <Link className="brand" to="/"><span className="brand-icon"><b /><b /><b /></span><span>ALERT<span>OPS</span></span></Link>
      <div className="auth-card">
        <span className="eyebrow">GET STARTED</span><h1>Create your account</h1><p>Build a workspace and walk through a live escalation.</p>
        <form onSubmit={submit} className="form-stack">
          <Field label="Your name"><input autoComplete="name" required value={name} onChange={event => setName(event.target.value)} placeholder="Alex Morgan" /></Field>
          <Field label="Work email"><input autoComplete="email" type="email" required value={email} onChange={event => setEmail(event.target.value)} placeholder="you@company.com" /></Field>
          <Field label="Password" hint="Use at least 8 characters."><input autoComplete="new-password" type="password" minLength={8} required value={password} onChange={event => setPassword(event.target.value)} placeholder="Create a password" /></Field>
          {registrationMutation.error && <div role="alert" className="form-error">{registrationMutation.error.message}</div>}
          <Button className="button-full" disabled={registrationMutation.isPending}>{registrationMutation.isPending ? 'Creating account…' : 'Create account'} <span>→</span></Button>
        </form>
        <div className="auth-switch">Already have an account? <Link to="/login">Sign in</Link></div>
      </div>
      <div className="auth-caption">ALERTOPS <i /> DURABLE, ORDERED ESCALATION</div>
    </div>
  )
}

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const location = useLocation()
  const navigate = useNavigate()
  const { token: sessionToken } = useSession()
  const locationState = location.state as { email?: unknown } | null
  const emailFromNavigation = typeof locationState?.email === 'string' ? locationState.email : ''
  const [verificationEmail, setVerificationEmail] = useState(
    () => emailFromNavigation || sessionStorage.getItem(PENDING_VERIFICATION_EMAIL_KEY) || '',
  )
  const verificationTokenFromUrl = searchParams.get('token') ?? ''
  const [emailVerified, setEmailVerified] = useState(false)

  const verifyEmailMutation = useMutation({
    mutationFn: () => verifyEmail(verificationTokenFromUrl),
    onSuccess: () => {
      sessionStorage.removeItem(PENDING_VERIFICATION_EMAIL_KEY)
      setEmailVerified(true)
    },
  })
  const resendVerificationMutation = useMutation({
    mutationFn: () => resendVerificationEmail(verificationEmail),
  })

  const continueAfterEmailVerification = () => {
    sessionStorage.removeItem(PENDING_VERIFICATION_EMAIL_KEY)
    if (!sessionToken) {
      navigate('/login')
      return
    }
    navigate(sessionStorage.getItem('alertops.pending-invite') ? '/join' : '/teams')
  }

  return (
    <div className="auth-page">
      <Link className="brand" to="/"><span className="brand-icon"><b /><b /><b /></span><span>ALERT<span>OPS</span></span></Link>
      <div className="auth-card">
        <span className="eyebrow">ACCOUNT SECURITY</span>
        <h1>{emailVerified ? 'Email verified' : 'Verify your email'}</h1>
        {emailVerified ? (
          <>
            <p>Your AlertOps account is ready. You can now create teams and accept invitations.</p>
            <Button className="button-full" onClick={continueAfterEmailVerification}>Continue <span>→</span></Button>
          </>
        ) : verificationTokenFromUrl ? (
          <>
            <p>Confirm this email address to unlock your AlertOps workspace.</p>
            {verifyEmailMutation.error && <div role="alert" className="form-error">{verifyEmailMutation.error.message}</div>}
            <Button className="button-full" disabled={verifyEmailMutation.isPending} onClick={() => verifyEmailMutation.mutate()}>
              {verifyEmailMutation.isPending ? 'Verifying…' : 'Verify email'} <span>→</span>
            </Button>
            {verifyEmailMutation.error && <>
              <p>Request a fresh link using the email address for this account.</p>
              {!verificationEmail && <Field label="Email"><input autoComplete="email" type="email" required value={verificationEmail} onChange={event => setVerificationEmail(event.target.value)} placeholder="you@company.com" /></Field>}
              {resendVerificationMutation.error && <div role="alert" className="form-error">{resendVerificationMutation.error.message}</div>}
              {resendVerificationMutation.isSuccess && <InlineNotice tone="success">A fresh verification link is on its way.</InlineNotice>}
              <Button className="button-full" disabled={!verificationEmail || resendVerificationMutation.isPending} onClick={() => resendVerificationMutation.mutate()}>
                {resendVerificationMutation.isPending ? 'Sending…' : 'Send a new link'} <span>→</span>
              </Button>
            </>}
          </>
        ) : (
          <>
            <p>We sent a verification link{verificationEmail ? <> to <strong>{verificationEmail}</strong></> : ''}. Open it to activate your account.</p>
            <InlineNotice tone="neutral">The link expires soon and can only be used once.</InlineNotice>
            {!verificationEmail && <Field label="Email"><input autoComplete="email" type="email" required value={verificationEmail} onChange={event => setVerificationEmail(event.target.value)} placeholder="you@company.com" /></Field>}
            {resendVerificationMutation.error && <div role="alert" className="form-error">{resendVerificationMutation.error.message}</div>}
            {resendVerificationMutation.isSuccess && <InlineNotice tone="success">A fresh verification link is on its way.</InlineNotice>}
            <Button className="button-full" disabled={!verificationEmail || resendVerificationMutation.isPending} onClick={() => resendVerificationMutation.mutate()}>
              {resendVerificationMutation.isPending ? 'Sending…' : 'Send a new link'} <span>→</span>
            </Button>
          </>
        )}
        <div className="auth-switch"><Link to="/">Return to AlertOps</Link></div>
      </div>
      <div className="auth-caption">ALERTOPS <i /> DURABLE, ORDERED ESCALATION</div>
    </div>
  )
}
