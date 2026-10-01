import { useState, type FormEvent } from 'react'
import { useParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createTask, getTasks } from '../../api/tasks'
import { Button, Card, EmptyState, ErrorState, Field, LoadingRows, PageHeader } from '../../components/Elements'
import { formatDate } from '../../lib/format'

export function TasksPage() {
  const { teamId = '' } = useParams()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [source, setSource] = useState('Manual')
  const [priority, setPriority] = useState('')
  const [category, setCategory] = useState('')
  const [referenceUrl, setReferenceUrl] = useState('')
  const queryClient = useQueryClient()
  const tasks = useQuery({ queryKey: ['tasks', teamId], queryFn: getTasks })
  const create = useMutation({
    mutationFn: () => createTask({
      name: name.trim(),
      description: description.trim(),
      source: source.trim() || 'Manual',
      priority: priority.trim() || null,
      category: category.trim() || null,
      referenceUrl: referenceUrl.trim() || null,
    }),
    onSuccess: async () => {
      setName('')
      setDescription('')
      setSource('Manual')
      setPriority('')
      setCategory('')
      setReferenceUrl('')
      await queryClient.invalidateQueries({ queryKey: ['tasks', teamId] })
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate()
  }

  return <>
    <PageHeader eyebrow="CONFIGURATION / TASKS" title="Tasks" description="A task carries the context that moves through each response path." />
    <div className="two-column-layout">
      <Card className="main-list-card">
        <div className="card-heading"><div><span className="eyebrow">TEAM TASKS</span><h2>Task library</h2></div><span className="count-pill">{tasks.data?.length ?? '—'}</span></div>
        {tasks.isPending ? <LoadingRows count={4} /> : tasks.isError ? <ErrorState message={tasks.error.message} onRetry={() => void tasks.refetch()} /> : tasks.data.length === 0 ? <EmptyState title="No tasks yet" description="Create a task to give your first response path useful context." /> : <div className="task-list">{tasks.data.map(task => <article className="task-row" key={task.id}><span className="task-icon">▤</span><div className="task-copy"><strong>{task.name}</strong><p>{task.description || 'No description provided.'}</p><small>{task.source || 'Manual'}{task.category ? ` · ${task.category}` : ''} · Created {formatDate(task.createdAt)}</small></div><span className="task-id">{task.id.slice(0, 8)}</span></article>)}</div>}
      </Card>
      <Card className="side-form-card">
        <div className="card-heading"><div><span className="eyebrow">NEW TASK</span><h2>Add context</h2></div><span className="form-number">01</span></div>
        <p className="form-intro">Give the people receiving this response path enough context to act.</p>
        <form onSubmit={submit} className="form-stack">
          <Field label="Title"><input required maxLength={120} value={name} onChange={event => setName(event.target.value)} placeholder="New hire needs account access" /></Field>
          <Field label="Description" hint="This text is included in the email sent to each recipient."><textarea required rows={5} maxLength={1000} value={description} onChange={event => setDescription(event.target.value)} placeholder="What needs a response and what should the recipient check first?" /></Field>
          <Field label="Source"><input required maxLength={120} value={source} onChange={event => setSource(event.target.value)} placeholder="Customer support" /></Field>
          <div className="form-grid-two">
            <Field label="Priority"><input maxLength={20} value={priority} onChange={event => setPriority(event.target.value)} placeholder="P1, VIP, urgent" /></Field>
            <Field label="Category"><input maxLength={80} value={category} onChange={event => setCategory(event.target.value)} placeholder="Onboarding" /></Field>
          </div>
          <Field label="Reference URL" hint="Optional HTTP(S) link for the recipient."><input type="url" maxLength={2048} value={referenceUrl} onChange={event => setReferenceUrl(event.target.value)} placeholder="https://example.com/request/123" /></Field>
          {create.error && <div className="form-error" role="alert">{create.error.message}</div>}
          <Button disabled={create.isPending}>{create.isPending ? 'Creating task…' : 'Create task'} <span>→</span></Button>
        </form>
      </Card>
    </div>
  </>
}
