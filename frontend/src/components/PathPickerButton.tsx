import { FolderOpen } from 'lucide-react'
import { api, type ApiHeaders } from '../api/client'

export function PathPickerButton({
  headers,
  initialPath,
  onPick,
  kind = 'folder',
}: {
  headers: ApiHeaders
  initialPath?: string
  onPick: (paths: string[]) => void
  kind?: 'folder' | 'file'
}) {
  async function pick() {
    const result = kind === 'folder' ? await api.pickFolders(headers, initialPath) : await api.pickFiles(headers, initialPath)
    if (!result.cancelled && result.paths.length > 0) onPick(result.paths)
  }

  return (
    <button type="button" className="icon-button" onClick={pick} title={kind === 'folder' ? 'Browse folders' : 'Browse files'}>
      <FolderOpen size={16} />
    </button>
  )
}
