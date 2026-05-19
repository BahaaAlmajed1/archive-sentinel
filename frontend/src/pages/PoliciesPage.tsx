import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { FileVideo, FolderTree, Pencil, Plus, Save, Trash2, X } from 'lucide-react'
import { api, type ApiHeaders } from '../api/client'
import { PageHeader } from '../components/PageHeader'
import { PathPickerButton } from '../components/PathPickerButton'
import { Tooltip } from '../components/Tooltip'
import type { PolicyDto, SettingsDto } from '../domain/types'

export function PoliciesPage({
  headers,
  policies,
  settings,
  refresh,
}: {
  headers: ApiHeaders
  policies: PolicyDto[]
  settings: SettingsDto
  refresh: () => Promise<void>
}) {
  const [paths, setPaths] = useState<string[]>([])
  const [manualPath, setManualPath] = useState('')
  const [targetType, setTargetType] = useState<'FOLDER' | 'FILE'>('FOLDER')
  const [retentionChoice, setRetentionChoice] = useState('inherit')
  const [customRetentionDays, setCustomRetentionDays] = useState('')
  const [search, setSearch] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [drafts, setDrafts] = useState<Record<string, PolicyDto>>({})
  const retentionPresets = useMemo(
    () => [
      { label: `${settings.defaultRetentionDays} days (default)`, value: 'inherit' },
      { label: 'Immediate', value: '0' },
      { label: '7 days', value: '7' },
      { label: '14 days', value: '14' },
      { label: '30 days', value: '30' },
      { label: '60 days', value: '60' },
      { label: '90 days', value: '90' },
      { label: 'Custom', value: 'custom' },
    ],
    [settings.defaultRetentionDays],
  )
  const visiblePolicies = policies.filter((policy) => policy.path.toLowerCase().includes(search.toLowerCase()))

  function appendPaths(nextPaths: string[]) {
    setPaths((current) => [...new Set([...current, ...nextPaths.map((path) => path.trim()).filter(Boolean)])])
  }

  function addManualPath() {
    if (!manualPath.trim()) return
    appendPaths([manualPath])
    setManualPath('')
  }

  async function addPolicies(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!event.currentTarget.checkValidity()) {
      event.currentTarget.classList.add('validated')
      event.currentTarget.reportValidity()
      return
    }
    const form = new FormData(event.currentTarget)
    const retentionDays =
      retentionChoice === 'inherit'
        ? null
        : Number(retentionChoice === 'custom' ? customRetentionDays : retentionChoice)
    await Promise.all(
      paths.map((path) =>
        api.addPolicy(
          {
            path,
            targetType,
            excluded: form.get('excluded') === 'on',
            recursive: form.get('recursive') === 'on',
            excludedExtensions: String(form.get('excludedExtensions') ?? ''),
            retentionDays: Number.isFinite(retentionDays) ? retentionDays : null,
            deletionMode: (form.get('deletionMode') || null) as PolicyDto['deletionMode'],
            outputMode: (form.get('outputMode') || null) as PolicyDto['outputMode'],
          },
          headers,
        ),
      ),
    )
    setPaths([])
    setTargetType('FOLDER')
    setRetentionChoice('inherit')
    setCustomRetentionDays('')
    event.currentTarget.reset()
    await refresh()
  }

  async function deletePolicy(id: string) {
    await api.deletePolicy(id, headers)
    await refresh()
  }

  function startEdit(policy: PolicyDto) {
    setEditingId(policy.id)
    setDrafts((current) => ({ ...current, [policy.id]: { ...policy } }))
  }

  function patchDraft<K extends keyof PolicyDto>(id: string, key: K, value: PolicyDto[K]) {
    setDrafts((current) => ({ ...current, [id]: { ...current[id], [key]: value } }))
  }

  async function savePolicy(id: string) {
    await api.updatePolicy(drafts[id], headers)
    setEditingId(null)
    await refresh()
  }

  return (
    <>
      <PageHeader title="Policies" description="Path-specific safety, retention, output, and extension controls." />

      <section className="panel">
        <form onSubmit={addPolicies} className="policy-form" noValidate>
          <div className="form-grid">
            <label>
              <span>
                Target type <Tooltip text="File policies match only one file. Folder policies match files inside the folder." />
              </span>
              <select value={targetType} onChange={(e) => setTargetType(e.target.value as 'FOLDER' | 'FILE')}>
                <option value="FOLDER">Folder</option>
                <option value="FILE">File</option>
              </select>
            </label>
            <label>
              <span>
                Add path <Tooltip text="Use the picker or add a path manually. Every picked path is appended to the list below." />
              </span>
              <span className="input-with-action policy-path-input">
                <input value={manualPath} onChange={(e) => setManualPath(e.target.value)} placeholder="E:\Videos or /mnt/archive" />
                <button type="button" onClick={addManualPath} title="Add path">
                  <Plus size={16} />
                </button>
              </span>
            </label>
          </div>

          <div className="policy-picker-row">
            <PathPickerButton headers={headers} kind={targetType === 'FILE' ? 'file' : 'folder'} multiple onPick={appendPaths} />
            <span>{targetType === 'FILE' ? 'Pick files' : 'Pick folders'}</span>
          </div>

          <div className="selected-path-list">
            {paths.map((path) => (
              <div key={path}>
                <span title={path}>{path}</span>
                <button type="button" onClick={() => setPaths((current) => current.filter((item) => item !== path))} title="Remove path">
                  <X size={15} />
                </button>
              </div>
            ))}
            {paths.length === 0 && <p className="empty-state">No policy paths selected yet.</p>}
          </div>

          <div className="form-grid">
            <label>
              Retention days
              <select value={retentionChoice} onChange={(e) => setRetentionChoice(e.target.value)}>
                {retentionPresets.map((preset) => (
                  <option key={preset.value} value={preset.value}>
                    {preset.label}
                  </option>
                ))}
              </select>
            </label>
            {retentionChoice === 'custom' && (
              <label>
                Custom retention days
                <input type="number" min={0} value={customRetentionDays} onChange={(e) => setCustomRetentionDays(e.target.value)} required />
              </label>
            )}
            <label>
              <span>
                Deletion mode <Tooltip text="Manual pauses for approval after retention; automatic deletes archived originals after retention." />
              </span>
              <select name="deletionMode" defaultValue="">
                <option value="">Use {settings.defaultDeletionMode}</option>
                <option value="MANUAL">Manual approval</option>
                <option value="AUTOMATIC">Automatic</option>
              </select>
            </label>
            <label>
              <span>
                Output mode <Tooltip text="Parallel writes to the optimized root. Same library writes next to the original path." />
              </span>
              <select name="outputMode" defaultValue="">
                <option value="">Use {settings.defaultOutputMode}</option>
                <option value="PARALLEL">Parallel</option>
                <option value="SAME_LIBRARY">Same library</option>
              </select>
            </label>
            <label>
              <span>
                Extension exclusions <Tooltip text="Comma-separated extensions to skip under this policy, for example mov,mxf,prores." />
              </span>
              <input name="excludedExtensions" placeholder="mov,mxf" />
            </label>
          </div>

          <div className="toggles">
            <label>
              <input type="checkbox" name="excluded" />
              Exclude target
            </label>
            <label>
              <input type="checkbox" name="recursive" defaultChecked />
              Recursive
              <Tooltip text="Recursive folder policies include descendants. When off, only direct children of that folder match." />
            </label>
          </div>

          <button type="submit" disabled={paths.length === 0}>
            Add policies
          </button>
        </form>
      </section>

      <section className="panel">
        <div className="search-row">
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search policy paths" />
        </div>
        <div className="policy-list">
          {visiblePolicies.map((policy) => {
            const draft = drafts[policy.id] ?? policy
            const editing = editingId === policy.id
            return (
              <div key={policy.id} className="policy-row">
                <div className="policy-path-cell">
                  <div>
                    {policy.targetType === 'FOLDER' ? <FolderTree size={15} /> : <FileVideo size={15} />}
                    <strong title={policy.path}>{policy.path}</strong>
                  </div>
                  <small>{policyDescription(policy, settings)}</small>
                </div>
                {editing ? (
                  <div className="policy-edit-grid">
                    <select value={draft.targetType} onChange={(e) => patchDraft(policy.id, 'targetType', e.target.value as PolicyDto['targetType'])}>
                      <option value="FOLDER">Folder</option>
                      <option value="FILE">File</option>
                    </select>
                    <select value={String(draft.recursive)} onChange={(e) => patchDraft(policy.id, 'recursive', e.target.value === 'true')}>
                      <option value="true">Recursive</option>
                      <option value="false">Direct only</option>
                    </select>
                    <select value={String(draft.excluded)} onChange={(e) => patchDraft(policy.id, 'excluded', e.target.value === 'true')}>
                      <option value="false">Included</option>
                      <option value="true">Excluded</option>
                    </select>
                    <input
                      type="number"
                      min={0}
                      value={draft.retentionDays ?? ''}
                      placeholder={`${settings.defaultRetentionDays}`}
                      onChange={(e) => patchDraft(policy.id, 'retentionDays', e.target.value === '' ? null : Number(e.target.value))}
                    />
                    <select
                      value={draft.deletionMode ?? ''}
                      onChange={(e) => patchDraft(policy.id, 'deletionMode', (e.target.value || null) as PolicyDto['deletionMode'])}
                    >
                      <option value="">Default</option>
                      <option value="MANUAL">Manual</option>
                      <option value="AUTOMATIC">Automatic</option>
                    </select>
                    <select value={draft.outputMode ?? ''} onChange={(e) => patchDraft(policy.id, 'outputMode', (e.target.value || null) as PolicyDto['outputMode'])}>
                      <option value="">Default</option>
                      <option value="PARALLEL">Parallel</option>
                      <option value="SAME_LIBRARY">Same library</option>
                    </select>
                    <input value={draft.excludedExtensions ?? ''} placeholder="Extensions" onChange={(e) => patchDraft(policy.id, 'excludedExtensions', e.target.value)} />
                  </div>
                ) : (
                  <div className="tag-row">
                    <span>{policy.targetType}</span>
                    <span>{policy.recursive ? 'Recursive' : 'Direct only'}</span>
                    <span>{policy.excluded ? 'Excluded' : 'Included'}</span>
                    <span>{policy.retentionDays ?? settings.defaultRetentionDays} days</span>
                    <span>{policy.deletionMode ?? settings.defaultDeletionMode}</span>
                    {!policy.excluded && <span>{policy.outputMode ?? settings.defaultOutputMode}</span>}
                    {!policy.excluded && policy.excludedExtensions && <span>Skip {policy.excludedExtensions}</span>}
                  </div>
                )}
                <div className="button-row policy-actions">
                  {editing ? (
                    <button onClick={() => void savePolicy(policy.id)}>
                      <Save size={15} /> Save
                    </button>
                  ) : (
                    <button onClick={() => startEdit(policy)}>
                      <Pencil size={15} /> Edit
                    </button>
                  )}
                  <button onClick={() => void deletePolicy(policy.id)} title="Delete policy">
                    <Trash2 size={15} /> Delete
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      </section>
    </>
  )
}

function policyDescription(policy: PolicyDto, settings: SettingsDto) {
  const noun = policy.targetType.toLowerCase()
  const retention = policy.retentionDays ?? settings.defaultRetentionDays
  const deletion = policy.deletionMode ?? settings.defaultDeletionMode
  if (policy.excluded) {
    return `This ${noun} will be ${policy.recursive ? 'recursively ' : ''}excluded from optimization and will ${
      deletion === 'MANUAL' ? 'need manual approval for deletion' : 'be deleted automatically'
    } after ${retention} days.`
  }
  const clauses = [
    `This ${noun} will be ${policy.recursive ? 'recursively ' : ''}processed`,
    `retain originals for ${retention} days`,
    deletion === 'MANUAL' ? 'need manual approval for deletion' : 'delete automatically after retention',
    `write ${(policy.outputMode ?? settings.defaultOutputMode).toLowerCase().replace('_', ' ')} output`,
  ]
  if (policy.excludedExtensions) clauses.push(`skip ${policy.excludedExtensions} extensions`)
  return `${clauses.join(', ')}.`
}
