import type { ReactNode } from 'react'

export function MetricCard({ icon, label, value, onClick }: { icon: ReactNode; label: string; value: ReactNode; onClick?: () => void }) {
  const content = (
    <>
      <span className="metric-icon">{icon}</span>
      <span>{label}</span>
      <strong>{value}</strong>
    </>
  )
  return onClick ? (
    <button type="button" className="metric-card clickable" onClick={onClick}>
      {content}
    </button>
  ) : (
    <article className="metric-card">{content}</article>
  )
}
