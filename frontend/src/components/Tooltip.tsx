import { CircleHelp } from 'lucide-react'
import type { ReactNode } from 'react'

export function Tooltip({ text }: { text: ReactNode }) {
  return (
    <span className="tooltip" tabIndex={0}>
      <CircleHelp size={14} />
      <span className="tooltip-panel">{text}</span>
    </span>
  )
}
