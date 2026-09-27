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
  const queryClient = useQueryClient()
  const tasks = useQuery({ queryKey: ['tasks', teamId], queryFn: getTasks })
  const create = useMutation({
    mutationFn: () => createTask({ name: name.trim(), description: description.trim() }),
    onSuccess: async () => {
      setName('')
      setDescription('')
      await queryClient.invalidateQueries({ queryKey: ['tasks', teamId] })
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate()
  }

  return <>
    <PageHeader eyebrow="CONFIGURATION / TASKS" title="Incident tasks" description="A task carries the context that moves through each escalation step." />
    <div className="two-column-layout">
      <Card className="main-list-card">
        <div className="card-heading"><div><span className="eyebrow">TEAM TASKS</span><h2>Task library</h2></div><span className="count-pill">{tasks.data?.length ?? '—'}</span></div>
        {tasks.isPending ? <LoadingRows count={4} /> : tasks.isError ? <ErrorState message={tasks.error.message} onRetry={() => void tasks.refetch()} /> : tasks.data.length === 0 ? <EmptyState title="Nothing needs attention yet" description="Create a task to give your first escalation useful incident context." /> : <div className="task-list">{tasks.data.map(task => <article className="task-row" key={task.id}><span className="task-icon">▤</span><div className="task-copy"><strong>{task.name}</strong><p>{task.description || 'No description provided.'}</p><small>Created {formatDate(task.createdAt)}</small></div><span className="task-id">{task.id.slice(0, 8)}</span></article>)}</div>}
      </Card>
      <Card className="side-form-card">
        <div className="card-heading"><div><span className="eyebrow">NEW TASK</span><h2>Add context</h2></div><span className="form-number">01</span></div>
        <p className="form-intro">Give responders a concise summary of the issue that triggered the escalation.</p>
        <form onSubmit={submit} className="form-stack">
          <Field label="Task name"><input required maxLength={120} value={name} onChange={event => setName(event.target.value)} placeholder="Production API latency" /></Field>
          <Field label="Description" hint="This text is included in the simulated notification."><textarea rows={5} maxLength={1000} value={description} onChange={event => setDescription(event.target.value)} placeholder="What is failing? What should the responder check first?" /></Field>
          {create.error && <div className="form-error" role="alert">{create.error.message}</div>}
          <Button disabled={create.isPending}>{create.isPending ? 'Creating task…' : 'Create task'} <span>→</span></Button>
        </form>
      </Card>
    </div>
  </>
}
