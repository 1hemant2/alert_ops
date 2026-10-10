import assert from 'node:assert/strict'
import { test } from 'node:test'
import { findStepPredecessor } from '../src/features/flows/stepOrdering.ts'

const steps = ['a', 'b', 'c', 'd'].map(id => ({ id }))

// Verifies that moving an earlier step later chooses its predecessor after removal.
test('earlier steps move to adjacent and final positions', () => {
  assert.equal(findStepPredecessor(steps, 'a', 2), 'b')
  assert.equal(findStepPredecessor(steps, 'a', 4), 'd')
  assert.equal(findStepPredecessor(steps, 'b', 3), 'c')
  assert.equal(findStepPredecessor(steps, 'b', 4), 'd')
})

// Verifies that later steps can move to the first or an earlier middle position.
test('later steps move to first and middle positions', () => {
  assert.equal(findStepPredecessor(steps, 'd', 1), null)
  assert.equal(findStepPredecessor(steps, 'd', 2), 'a')
  assert.equal(findStepPredecessor(steps, 'c', 2), 'a')
})

// Verifies unchanged positions and single-step paths preserve their predecessor.
test('unchanged positions preserve order without mutating the source', () => {
  assert.equal(findStepPredecessor(steps, 'a', 1), null)
  assert.equal(findStepPredecessor(steps, 'b', 2), 'a')
  assert.equal(findStepPredecessor([{ id: 'a' }], 'a', 1), null)
  assert.deepEqual(steps.map(step => step.id), ['a', 'b', 'c', 'd'])
})

// Rejects positions and identifiers that cannot describe a valid saved move.
test('invalid positions and missing steps are rejected', () => {
  for (const position of [0, 5, -1, 1.5, NaN]) {
    assert.equal(findStepPredecessor(steps, 'a', position), undefined)
  }
  assert.equal(findStepPredecessor(steps, 'missing', 2), undefined)
  assert.equal(findStepPredecessor([], 'a', 1), undefined)
})
