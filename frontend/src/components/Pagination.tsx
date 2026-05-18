import { ChevronLeft, ChevronRight } from 'lucide-react'

export function Pagination({
  page,
  totalPages,
  onPage,
}: {
  page: number
  totalPages: number
  onPage: (page: number) => void
}) {
  return (
    <div className="pagination">
      <button type="button" disabled={page <= 0} onClick={() => onPage(page - 1)} title="Previous page">
        <ChevronLeft size={16} />
      </button>
      <span>
        Page {totalPages === 0 ? 0 : page + 1} of {totalPages}
      </span>
      <button type="button" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)} title="Next page">
        <ChevronRight size={16} />
      </button>
    </div>
  )
}
