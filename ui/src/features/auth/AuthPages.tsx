import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { useMutation } from '@tanstack/react-query'
import { login, register } from '../../api/auth'
import { useSession } from '../../app/useSession'
import { Button, Field } from '../../components/Elements'

export function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const { beginSession } = useSession()
  const navigate = useNavigate()
  const mutation = useMutation({
    mutationFn: () => login(email.trim(), password),
    onSuccess: token => {
      beginSession(token)
      navigate('/teams')
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <div className="auth-page">
      <Link className="brand" to="/"><span className="brand-icon"><b /><b /><b /></span><span>ALERT<span>OPS</span></span></Link>
      <div className="auth-card">
        <span className="eyebrow">WELCOME BACK</span><h1>Sign in to AlertOps</h1><p>Pick up where your incident workflows left off.</p>
        <form onSubmit={submit} className="form-stack">
          <Field label="Email"><input autoComplete="email" type="email" required value={email} onChange={event => setEmail(event.target.value)} placeholder="you@company.com" /></Field>
          <Field label="Password"><input autoComplete="current-password" type="password" required value={password} onChange={event => setPassword(event.target.value)} placeholder="Your password" /></Field>
          {mutation.error && <div role="alert" className="form-error">{mutation.error.message}</div>}
          <Button className="button-full" disabled={mutation.isPending}>{mutation.isPending ? 'Signing in…' : 'Sign in'} <span>→</span></Button>
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
  const mutation = useMutation({
    mutationFn: () => register({ name: name.trim(), email: email.trim(), password }),
    onSuccess: () => navigate('/login', { state: { registered: true } }),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
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
          {mutation.error && <div role="alert" className="form-error">{mutation.error.message}</div>}
          <Button className="button-full" disabled={mutation.isPending}>{mutation.isPending ? 'Creating account…' : 'Create account'} <span>→</span></Button>
        </form>
        <div className="auth-switch">Already have an account? <Link to="/login">Sign in</Link></div>
      </div>
      <div className="auth-caption">ALERTOPS <i /> DURABLE, ORDERED ESCALATION</div>
    </div>
  )
}
