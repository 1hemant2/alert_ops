// Finds the saved predecessor that places a step at the requested one-based position.
export function findStepPredecessor(
  steps: readonly { id: string }[],
  stepId: string,
  position: number,
): string | null | undefined {
  if (!Number.isInteger(position) || position < 1 || position > steps.length) return undefined
  if (!steps.some(step => step.id === stepId)) return undefined
  const remaining = steps.filter(step => step.id !== stepId)
  return position === 1 ? null : remaining[position - 2]?.id
}
