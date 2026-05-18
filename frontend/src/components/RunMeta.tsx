import type { RunDto } from '../domain/types'

export function RunMeta({ run }: { run: RunDto }) {
  const created = new Date(run.startedAt).toLocaleString()
  const finished = run.completedAt ? new Date(run.completedAt).toLocaleString() : null
  return (
    <small className="run-meta" title={`Created: ${created}\n${finished ? `Finished: ${finished}` : 'Status: In progress'}`}>
      Created {created} | {finished ? `Finished ${finished}` : 'In progress'}
    </small>
  )
}
