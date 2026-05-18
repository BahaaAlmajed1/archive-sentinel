import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Clock3, Pencil, Save, Trash2, X } from 'lucide-react'
import { api, type ApiHeaders } from '../api/client'
import { PageHeader } from '../components/PageHeader'
import type { AutomationDto, RootDto } from '../domain/types'

export function AutomationPage({
  headers,
  roots,
  automations,
  refresh,
}: {
  headers: ApiHeaders
  roots: RootDto[]
  automations: AutomationDto[]
  refresh: () => Promise<void>
}) {
  const [storageRootId, setStorageRootId] = useState(roots[0]?.id ?? '')
  const [intervalMinutes, setIntervalMinutes] = useState(60)
  const [search, setSearch] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [drafts, setDrafts] = useState<Record<string, { enabled: boolean; intervalMinutes: number }>>({})
  const visibleRules = useMemo(
    () =>
      automations.filter((rule) => {
        const root = roots.find((item) => item.id === rule.storageRootId)
        return `${root?.label ?? ''} ${root?.path ?? ''}`.toLowerCase().includes(search.toLowerCase())
      }),
    [automations, roots, search],
  )

  async function saveRule(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!event.currentTarget.checkValidity()) {
      event.currentTarget.classList.add('validated')
      event.currentTarget.reportValidity()
      return
    }
    if (!storageRootId) return
    await api.upsertAutomation({ storageRootId, enabled: true, intervalMinutes }, headers)
    await refresh()
  }

  async function deleteRule(id: string) {
    await api.deleteAutomation(id, headers)
    await refresh()
  }

  function startEdit(rule: AutomationDto) {
    setEditingId(rule.id)
    setDrafts((current) => ({ ...current, [rule.id]: { enabled: rule.enabled, intervalMinutes: rule.intervalMinutes } }))
  }

  async function saveEdit(rule: AutomationDto) {
    const draft = drafts[rule.id]
    if (!draft || draft.intervalMinutes < 1) return
    await api.upsertAutomation({ storageRootId: rule.storageRootId, ...draft }, headers)
    setEditingId(null)
    await refresh()
  }

  return (
    <>
      <PageHeader
        title="Automation"
        description="Scheduled automations scan roots and start optimization for newly discovered files; automatic rescanning only refreshes inventory."
      />

      <section className="panel">
        <form onSubmit={saveRule} noValidate>
          <div className="form-grid">
            <label>
              Storage root
              <select value={storageRootId} onChange={(e) => setStorageRootId(e.target.value)} required>
                {roots.map((root) => (
                  <option key={root.id} value={root.id}>
                    {root.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Interval minutes
              <input type="number" min={1} value={intervalMinutes} onChange={(e) => setIntervalMinutes(Number(e.target.value))} required />
            </label>
          </div>
          <button type="submit">
            <Clock3 size={16} /> Save automation
          </button>
        </form>
      </section>

      <section className="panel">
        <div className="search-row">
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search automated roots" />
        </div>
        <div className="table automation-table">
          {visibleRules.map((rule) => {
            const root = roots.find((item) => item.id === rule.storageRootId)
            return (
              <div key={rule.id}>
                <span>{root?.label ?? 'Unknown root'}</span>
                <span title={root?.path}>{root?.path ?? rule.storageRootId}</span>
                {editingId === rule.id ? (
                  <input
                    type="number"
                    min={1}
                    value={drafts[rule.id]?.intervalMinutes ?? rule.intervalMinutes}
                    onChange={(e) =>
                      setDrafts((current) => ({
                        ...current,
                        [rule.id]: {
                          enabled: current[rule.id]?.enabled ?? rule.enabled,
                          intervalMinutes: Number(e.target.value),
                        },
                      }))
                    }
                  />
                ) : (
                  <span>{rule.intervalMinutes} min</span>
                )}
                <span>{rule.lastRunAt ? new Date(rule.lastRunAt).toLocaleString() : 'Not run yet'}</span>
                <div className="button-row">
                  {editingId === rule.id ? (
                    <>
                      <label className="check-row">
                        <input
                          type="checkbox"
                          checked={drafts[rule.id]?.enabled ?? rule.enabled}
                          onChange={(e) =>
                            setDrafts((current) => ({
                              ...current,
                              [rule.id]: {
                                enabled: e.target.checked,
                                intervalMinutes: current[rule.id]?.intervalMinutes ?? rule.intervalMinutes,
                              },
                            }))
                          }
                        />
                        <span>Enabled</span>
                      </label>
                      <button onClick={() => void saveEdit(rule)}>
                        <Save size={15} /> Save
                      </button>
                      <button onClick={() => setEditingId(null)}>
                        <X size={15} /> Cancel
                      </button>
                    </>
                  ) : (
                    <button onClick={() => startEdit(rule)}>
                      <Pencil size={15} /> Edit
                    </button>
                  )}
                  <button onClick={() => void deleteRule(rule.id)}>
                    <Trash2 size={15} /> Delete
                  </button>
                </div>
              </div>
            )
          })}
          {visibleRules.length === 0 && <p className="empty-state">No automations configured.</p>}
        </div>
      </section>
    </>
  )
}
