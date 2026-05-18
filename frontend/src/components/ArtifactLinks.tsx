import { ExternalLink, FileText, ScrollText } from 'lucide-react'

export function ArtifactLinks({ reportUrl, logUrl }: { reportUrl?: string | null; logUrl?: string | null }) {
  return (
    <span className="artifact-links">
      {reportUrl && (
        <a href={reportUrl} target="_blank" rel="noreferrer">
          <FileText size={14} /> Report <ExternalLink size={12} />
        </a>
      )}
      {logUrl && (
        <a href={logUrl} target="_blank" rel="noreferrer">
          <ScrollText size={14} /> Log <ExternalLink size={12} />
        </a>
      )}
    </span>
  )
}
