import { useState } from 'react'
import type { FormEvent } from 'react'
import { KeyRound, Save, Trash2 } from 'lucide-react'
import type { ApiHeaders } from '../api/client'
import { api } from '../api/client'
import { PathPickerButton } from '../components/PathPickerButton'
import { Tooltip } from '../components/Tooltip'
import type { SettingsDto } from '../domain/types'

type SettingsFormProps = {
  title: string
  settings: SettingsDto
  setSettings: (settings: SettingsDto) => void
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  headers: ApiHeaders
  onMessage?: (message: string) => void
}

export function SettingsPage({
  settings,
  setSettings,
  onSubmit,
  headers,
  onPasswordMessage,
}: {
  settings: SettingsDto
  setSettings: (settings: SettingsDto) => void
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  headers: ApiHeaders
  onPasswordMessage: (message: string) => void
}) {
  async function changePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!event.currentTarget.checkValidity()) {
      event.currentTarget.classList.add('validated')
      event.currentTarget.reportValidity()
      return
    }
    const form = new FormData(event.currentTarget)
    const response = await api.changePassword(
      {
        currentPassword: form.get('currentPassword'),
        newPassword: form.get('newPassword'),
      },
      headers,
    )
    onPasswordMessage(response.ok ? 'Password changed.' : 'Could not change password.')
    if (response.ok) event.currentTarget.reset()
  }

  return (
    <>
      <SettingsForm title="Settings" settings={settings} setSettings={setSettings} onSubmit={onSubmit} headers={headers} onMessage={onPasswordMessage} />
      <section className="settings-card password-card">
        <form onSubmit={changePassword} noValidate>
          <h2>
            <KeyRound size={18} /> Change password
          </h2>
          <div className="form-grid compact-grid">
            <label>
              Current password
              <input name="currentPassword" type="password" required />
            </label>
            <label>
              New password
              <input name="newPassword" type="password" required />
            </label>
          </div>
          <button type="submit">
            <Save size={16} /> Change password
          </button>
        </form>
      </section>
    </>
  )
}

export function SettingsForm({ title, settings, setSettings, onSubmit, headers, onMessage }: SettingsFormProps) {
  const patch = <K extends keyof SettingsDto>(key: K, value: SettingsDto[K]) => setSettings({ ...settings, [key]: value })
  const setPath = (key: 'archiveRoot' | 'optimizedRoot' | 'stagingRoot', paths: string[]) => patch(key, paths[0] ?? '')
  const [newExtension, setNewExtension] = useState('')
  const maxAvailableThreads = settings.maxAvailableThreads || navigator.hardwareConcurrency || 4
  const threadTotal = settings.scanThreads + settings.analysisThreads + settings.validationThreads + settings.fileOpsThreads
  const threadRemaining = maxAvailableThreads - threadTotal
  const maxFor = (value: number) => Math.max(1, value + threadRemaining)

  function toggleExtension(extension: string, enabled: boolean) {
    const current = new Set(settings.enabledExtensions)
    if (enabled) current.add(extension)
    else current.delete(extension)
    patch('enabledExtensions', [...current].sort())
  }

  function addExtension() {
    const extension = normalizeExtension(newExtension)
    if (!extension) return
    const options = new Set(settings.precheckExtensionOptions)
    const enabled = new Set(settings.enabledExtensions)
    options.add(extension)
    enabled.add(extension)
    setSettings({
      ...settings,
      precheckExtensionOptions: [...options].sort(),
      enabledExtensions: [...enabled].sort(),
    })
    setNewExtension('')
  }

  return (
    <section className="settings-card">
      <form onSubmit={onSubmit} noValidate>
        <div className="settings-title">
          <div>
            <h1>{title}</h1>
            <p>Host-native mode is the default; Docker stays available for isolated deployments.</p>
          </div>
          <button type="submit">
            <Save size={16} /> Save settings
          </button>
        </div>

        <div className="form-section">
          <h2>Runtime</h2>
          <div className="form-grid">
            <label>
              <span>
                Execution mode <Tooltip text="Host-native uses paths visible to this machine. Docker mode uses container mount paths such as /data/originals." />
              </span>
              <select value={settings.executionMode} onChange={(e) => patch('executionMode', e.target.value as SettingsDto['executionMode'])}>
                <option value="HOST_NATIVE">Host native</option>
                <option value="DOCKER">Docker compose</option>
              </select>
            </label>
            <label>
              <span>
                Tdarr mode <Tooltip text="Managed Tdarr is for the compose stack. Existing Tdarr points to a host or network Tdarr server." />
              </span>
              <select value={settings.tdarrMode} onChange={(e) => patch('tdarrMode', e.target.value as SettingsDto['tdarrMode'])}>
                <option value="EXISTING">Existing Tdarr</option>
                <option value="MANAGED">Managed Tdarr</option>
              </select>
            </label>
            <label>
              <span>
                Tdarr URL <Tooltip text="The backend submits transcode work to this Tdarr API base URL." />
              </span>
              <input value={settings.tdarrBaseUrl} onChange={(e) => patch('tdarrBaseUrl', e.target.value)} required />
            </label>
            <label>
              <span>
                Tdarr queue batch size <Tooltip text="Number of files Archive Sentinel submits to Tdarr at once. Tdarr still manages its own worker queue after submission." />
              </span>
              <input type="number" value={settings.tdarrSubmissionConcurrency} onChange={(e) => patch('tdarrSubmissionConcurrency', Number(e.target.value))} />
            </label>
          </div>
          <div className="toggles">
            <label>
              <input type="checkbox" checked={settings.managedTdarrEnabled} onChange={(e) => patch('managedTdarrEnabled', e.target.checked)} />
              Managed Tdarr enabled
            </label>
          </div>
        </div>

        <div className="form-section">
          <h2>Paths</h2>
          <div className="form-grid">
            <PathField label="Archive root" help="Verified originals move here after optimized output passes every validation gate." value={settings.archiveRoot} onChange={(value) => patch('archiveRoot', value)} onPick={(paths) => setPath('archiveRoot', paths)} headers={headers} required />
            <PathField label="Optimized root" help="Parallel output mode writes final optimized files under this root." value={settings.optimizedRoot} onChange={(value) => patch('optimizedRoot', value)} onPick={(paths) => setPath('optimizedRoot', paths)} headers={headers} required />
            <PathField label="Staging root" help="Tdarr candidates land here first, then move into the optimized location after validation." value={settings.stagingRoot ?? ''} onChange={(value) => patch('stagingRoot', value)} onPick={(paths) => setPath('stagingRoot', paths)} headers={headers} />
            <label>
              <span>
                Retention days <Tooltip text="How long archived originals are retained before manual approval or automatic deletion can happen." />
              </span>
              <input type="number" value={settings.defaultRetentionDays} onChange={(e) => patch('defaultRetentionDays', Number(e.target.value))} required />
            </label>
          </div>
          <div className="toggles">
            <label>
              <input type="checkbox" checked={settings.stagingEnabled} onChange={(e) => patch('stagingEnabled', e.target.checked)} />
              Staging enabled
            </label>
          </div>
        </div>

        <div className="form-section">
          <h2>Safety defaults</h2>
          <div className="form-grid">
            <label>
              <span>
                Deletion mode <Tooltip text="Manual requires approval after retention. Automatic deletes archived originals when retention expires." />
              </span>
              <select value={settings.defaultDeletionMode} onChange={(e) => patch('defaultDeletionMode', e.target.value as SettingsDto['defaultDeletionMode'])}>
                <option value="MANUAL">Manual approval</option>
                <option value="AUTOMATIC">Automatic after retention</option>
              </select>
              <small>Manual is safest for regulated archives; automatic reduces operator work once confidence is high.</small>
            </label>
            <label>
              <span>
                Output mode <Tooltip text="Parallel writes optimized files under the optimized root. Same library writes next to each original." />
              </span>
              <select value={settings.defaultOutputMode} onChange={(e) => patch('defaultOutputMode', e.target.value as SettingsDto['defaultOutputMode'])}>
                <option value="PARALLEL">Parallel output root</option>
                <option value="SAME_LIBRARY">Same library folder</option>
              </select>
              <small>Parallel mode keeps new outputs isolated; same-library mode is useful when directory structure must stay local.</small>
            </label>
          </div>
        </div>

        <div className="form-section">
          <h2>Precheck settings</h2>
          <div className="form-grid">
            <label>
              <span>
                Estimated optimized size percent <Tooltip text="Used only for precheck estimates. For example, 65 means the optimized file is estimated at 65% of the original size." />
              </span>
              <input
                type="number"
                min={1}
                max={100}
                value={settings.estimatedOutputRatioPercent}
                onChange={(e) => patch('estimatedOutputRatioPercent', Number(e.target.value))}
              />
            </label>
            <label>
              <span>
                Add allowed extension <Tooltip text="Add another allowed extension to the selectable precheck list, without a leading dot." />
              </span>
              <span className="input-with-action">
                <input value={newExtension} onChange={(e) => setNewExtension(e.target.value)} placeholder="mxf" />
                <button type="button" onClick={addExtension}>
                  Add
                </button>
              </span>
            </label>
          </div>
          <div className="extension-grid">
            {settings.precheckExtensionOptions.map((extension) => (
              <label key={extension}>
                <input
                  type="checkbox"
                  checked={settings.enabledExtensions.includes(extension)}
                  onChange={(e) => toggleExtension(extension, e.target.checked)}
                />
                .{extension}
              </label>
            ))}
          </div>
        </div>

        <div className="form-section">
          <h2>Output and validation</h2>
          <div className="form-grid">
            <label>
              <span>
                Output container extension <Tooltip text="Optimized candidates and promoted files use this container extension." />
              </span>
              <input value={settings.outputContainerExtension} onChange={(e) => patch('outputContainerExtension', normalizeExtension(e.target.value))} required />
            </label>
            <label>
              <span>
                Duration tolerance seconds <Tooltip text="Maximum accepted duration difference between the original and the candidate." />
              </span>
              <input
                type="number"
                step="0.1"
                min={0}
                value={settings.durationToleranceSeconds}
                onChange={(e) => patch('durationToleranceSeconds', Number(e.target.value))}
              />
            </label>
          </div>
        </div>

        <div className="form-section">
          <h2>Tdarr encoding</h2>
          <div className="form-grid">
            <label>
              <span>
                Codecs to skip <Tooltip text="Files already using these codecs are left alone by the Tdarr plugin decision step." />
              </span>
              <input value={settings.tdarrCodecsToExclude} onChange={(e) => patch('tdarrCodecsToExclude', e.target.value)} />
            </label>
            <label>
              <span>
                Transcode arguments <Tooltip text="Arguments passed into the Tdarr ffmpeg plugin for candidate generation." />
              </span>
              <input value={settings.tdarrTranscodeArguments} onChange={(e) => patch('tdarrTranscodeArguments', e.target.value)} required />
            </label>
          </div>
          <div className="button-row">
            <button
              type="button"
              onClick={async () => {
                const result = await api.clearTdarrQueue(headers)
                onMessage?.(`Cleared ${result.clearedPaths} tracked Tdarr queue paths.`)
              }}
            >
              <Trash2 size={16} /> Force clear Tdarr queue
            </button>
          </div>
        </div>

        <div className="form-section">
          <h2>Media tools</h2>
          <div className="form-grid">
            <ToolPathField
              label="FFmpeg path"
              help="Used for candidate decode validation. Leave blank to auto-detect the Tdarr-bundled binary or fall back to PATH."
              value={settings.mediaFfmpegPath}
              onChange={(value) => patch('mediaFfmpegPath', value)}
              onPick={(paths) => patch('mediaFfmpegPath', paths[0] ?? '')}
              headers={headers}
            />
            <ToolPathField
              label="FFprobe path"
              help="Used for precheck analysis and validation metadata. Leave blank to auto-detect the Tdarr-bundled binary or fall back to PATH."
              value={settings.mediaFfprobePath}
              onChange={(value) => patch('mediaFfprobePath', value)}
              onPick={(paths) => patch('mediaFfprobePath', paths[0] ?? '')}
              headers={headers}
            />
          </div>
        </div>

        <div className="form-section">
          <h2>SMTP reports</h2>
          <div className="form-grid">
            <label>
              <span>
                Report interval hours <Tooltip text="Aggregate email summaries are sent no more often than this interval." />
              </span>
              <input type="number" value={settings.reportIntervalHours} onChange={(e) => patch('reportIntervalHours', Number(e.target.value))} />
            </label>
            <label>
              <span>
                Report recipients <Tooltip text="Comma-separated email recipients for scheduled aggregate summaries." />
              </span>
              <input value={settings.reportRecipients} onChange={(e) => patch('reportRecipients', e.target.value)} />
            </label>
            <label>
              SMTP host
              <input value={settings.smtpHost} onChange={(e) => patch('smtpHost', e.target.value)} />
            </label>
            <label>
              SMTP port
              <input type="number" value={settings.smtpPort} onChange={(e) => patch('smtpPort', Number(e.target.value))} />
            </label>
            <label>
              SMTP username
              <input value={settings.smtpUsername} onChange={(e) => patch('smtpUsername', e.target.value)} />
            </label>
            <label>
              SMTP password
              <input type="password" value={settings.smtpPassword} onChange={(e) => patch('smtpPassword', e.target.value)} />
            </label>
            <label>
              From address
              <input value={settings.smtpFrom} onChange={(e) => patch('smtpFrom', e.target.value)} />
            </label>
          </div>
          <div className="toggles">
            <label>
              <input type="checkbox" checked={settings.smtpAuth} onChange={(e) => patch('smtpAuth', e.target.checked)} />
              SMTP auth
            </label>
            <label>
              <input type="checkbox" checked={settings.smtpStartTls} onChange={(e) => patch('smtpStartTls', e.target.checked)} />
              STARTTLS
            </label>
          </div>
        </div>

        <div className="form-section">
          <h2>Worker threads</h2>
          <p className={threadRemaining < 0 ? 'warning-text' : 'muted-text'}>
            Max threads available: {maxAvailableThreads}. Remaining budget: {threadRemaining}.
          </p>
          <div className="form-grid four">
            <NumberField label="Scan" value={settings.scanThreads} max={maxFor(settings.scanThreads)} onChange={(value) => patch('scanThreads', value)} />
            <NumberField label="Analysis" value={settings.analysisThreads} max={maxFor(settings.analysisThreads)} onChange={(value) => patch('analysisThreads', value)} />
            <NumberField label="Validation" value={settings.validationThreads} max={maxFor(settings.validationThreads)} onChange={(value) => patch('validationThreads', value)} />
            <NumberField label="File ops" value={settings.fileOpsThreads} max={maxFor(settings.fileOpsThreads)} onChange={(value) => patch('fileOpsThreads', value)} />
          </div>
        </div>

        <div className="form-section">
          <h2>Background scans</h2>
          <div className="form-grid">
            <label>
              <span>
                Refresh interval minutes <Tooltip text="How often Archive Sentinel may rescan roots that have automatic rescanning enabled and have recorded filesystem changes." />
              </span>
              <input
                type="number"
                min={1}
                value={settings.backgroundScanRefreshMinutes}
                onChange={(e) => patch('backgroundScanRefreshMinutes', Number(e.target.value))}
              />
            </label>
          </div>
        </div>
      </form>
    </section>
  )
}

function PathField({
  label,
  help,
  value,
  onChange,
  onPick,
  headers,
  required = false,
}: {
  label: string
  help: string
  value: string
  onChange: (value: string) => void
  onPick: (paths: string[]) => void
  headers: ApiHeaders
  required?: boolean
}) {
  return (
    <label>
      <span>
        {label} <Tooltip text={help} />
      </span>
      <span className="input-with-action">
        <input value={value} onChange={(e) => onChange(e.target.value)} required={required} />
        <PathPickerButton headers={headers} initialPath={value} onPick={onPick} />
      </span>
    </label>
  )
}

function ToolPathField({
  label,
  help,
  value,
  onChange,
  onPick,
  headers,
}: {
  label: string
  help: string
  value: string
  onChange: (value: string) => void
  onPick: (paths: string[]) => void
  headers: ApiHeaders
}) {
  return (
    <label>
      <span>
        {label} <Tooltip text={help} />
      </span>
      <span className="input-with-action">
        <input value={value} onChange={(e) => onChange(e.target.value)} placeholder="Auto-detect" />
        <PathPickerButton headers={headers} initialPath={value} kind="file" onPick={onPick} />
      </span>
    </label>
  )
}

function NumberField({ label, value, max, onChange }: { label: string; value: number; max?: number; onChange: (value: number) => void }) {
  return (
    <label>
      {label}
      <input type="number" min={1} max={max} value={value} onChange={(e) => onChange(Number(e.target.value))} />
      {max && <small>Max now: {max}</small>}
    </label>
  )
}

function normalizeExtension(value: string) {
  return value.trim().replace(/^\./, '').toLowerCase()
}
