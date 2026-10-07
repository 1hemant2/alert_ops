import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getTask, updateTask } from '../../api/tasks'
import type { Task } from '../../api/types'
import { Button, Card, ErrorState, Field, InlineNotice, LinkButton, LoadingRows, PageHeader } from '../../components/Elements'

type TaskFormState = Pick<Task, 'name' | 'description' | 'source' | 'priority' | 'category' | 'referenceUrl'>

// Converts nullable API values into editable form values.
function taskToForm(task: Task): TaskFormState {
  return {
    name: task.name,
    description: task.description,
    source: task.source || 'Manual',
    priority: task.priority ?? '',
    category: task.category ?? '',
    referenceUrl: task.referenceUrl ?? '',
  }
}

// Displays a nullable task field without implying that missing metadata is an error.
function displayTaskValue(value?: string | null): string {
  return value?.trim() || 'Not provided'
}

// Renders the editable details and saved context for one team task.
export function TaskDetailPage() {
  const { teamId = '', taskId = '' } = useParams()
  const task = useQuery({
    queryKey: ['task', teamId, taskId],
    queryFn: () => getTask(taskId),
    enabled: Boolean(taskId),
  })

  if (task.isPending) return <LoadingRows count={4} />
  if (task.isError) return <ErrorState message={task.error.message} onRetry={() => void task.refetch()} />

  const savedTask = task.data
  return <>
    <div className="back-link-row"><Link to={`/app/${teamId}/tasks`}>← Tasks</Link><span> / </span><span>{savedTask.name}</span></div>
    <PageHeader eyebrow="CONFIGURATION / TASK DETAIL" title={savedTask.name} description="Edit reusable task context without changing snapshots already saved in running escalations." action={<LinkButton to={`/app/${teamId}/tasks`}>Back to tasks</LinkButton>} />
    <div className="two-column-layout">
      <Card className="main-list-card task-detail-card">
        <div className="card-heading"><div><span className="eyebrow">SAVED CONTEXT</span><h2>{savedTask.name}</h2></div><span className="task-id">{savedTask.id.slice(0, 8)}</span></div>
        <p className="task-detail-description">{savedTask.description || 'No description provided.'}</p>
        <dl className="task-detail-fields">
          <div><dt>Source</dt><dd>{displayTaskValue(savedTask.source)}</dd></div>
          <div><dt>Priority</dt><dd>{displayTaskValue(savedTask.priority)}</dd></div>
          <div><dt>Category</dt><dd>{displayTaskValue(savedTask.category)}</dd></div>
          <div><dt>Reference URL</dt><dd>{savedTask.referenceUrl ? <a href={savedTask.referenceUrl} target="_blank" rel="noreferrer">{savedTask.referenceUrl} ↗</a> : 'Not provided'}</dd></div>
        </dl>
        <InlineNotice>Changes apply to future escalations. Existing runs keep the task context captured when they started.</InlineNotice>
      </Card>
      <TaskEditor task={savedTask} teamId={teamId} />
    </div>
  </>
}

// Owns editable task state and saves it through the existing task endpoints.
function TaskEditor({ task, teamId }: { task: Task; teamId: string }) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<TaskFormState>(() => taskToForm(task))
  const save = useMutation({
    mutationFn: () => updateTask(task.id, {
      name: form.name.trim(),
      description: form.description.trim(),
      source: form.source.trim() || 'Manual',
      priority: form.priority?.trim() || null,
      category: form.category?.trim() || null,
      referenceUrl: form.referenceUrl?.trim() || null,
    }),
    onSuccess: async savedTask => {
      setForm(taskToForm(savedTask))
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['task', teamId, task.id] }),
        queryClient.invalidateQueries({ queryKey: ['tasks', teamId] }),
      ])
    },
  })

  // Submits all edited task fields and keeps the saved form state visible.
  function submit(event: FormEvent) {
    event.preventDefault()
    save.mutate()
  }

  return <Card className="side-form-card">
    <div className="card-heading"><div><span className="eyebrow">EDIT TASK</span><h2>Update context</h2></div><span className="form-number">01</span></div>
    <p className="form-intro">Keep this reusable task current for the next response path.</p>
    <form onSubmit={submit} className="form-stack">
      <Field label="Title"><input required maxLength={120} value={form.name} onChange={event => setForm(current => ({ ...current, name: event.target.value }))} /></Field>
      <Field label="Description" hint="Included in notifications sent to recipients."><textarea required rows={5} maxLength={1000} value={form.description} onChange={event => setForm(current => ({ ...current, description: event.target.value }))} /></Field>
      <Field label="Source"><input required maxLength={120} value={form.source} onChange={event => setForm(current => ({ ...current, source: event.target.value }))} /></Field>
      <div className="form-grid-two">
        <Field label="Priority"><input maxLength={20} value={form.priority ?? ''} onChange={event => setForm(current => ({ ...current, priority: event.target.value }))} placeholder="P1, VIP, urgent" /></Field>
        <Field label="Category"><input maxLength={80} value={form.category ?? ''} onChange={event => setForm(current => ({ ...current, category: event.target.value }))} placeholder="Onboarding" /></Field>
      </div>
      <Field label="Reference URL" hint="Optional HTTP(S) link for the recipient."><input type="url" maxLength={2048} value={form.referenceUrl ?? ''} onChange={event => setForm(current => ({ ...current, referenceUrl: event.target.value }))} placeholder="https://example.com/request/123" /></Field>
      {save.error && <div className="form-error" role="alert">{save.error.message}</div>}
      {save.data && <InlineNotice tone="success">Task saved. Future escalations will use this context.</InlineNotice>}
      <Button disabled={save.isPending}>{save.isPending ? 'Saving task…' : 'Save task'} <span>→</span></Button>
    </form>
  </Card>
}
