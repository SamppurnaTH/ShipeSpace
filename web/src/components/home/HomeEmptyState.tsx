import type { ReactNode } from 'react'

interface HomeEmptyStateProps {
  title: string
  body: string
  action?: ReactNode
  tone?: 'default' | 'warning' | 'error'
}

export function HomeEmptyState({ title, body, action, tone = 'default' }: HomeEmptyStateProps) {
  return (
    <div className={`home-empty home-empty-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      <div className="home-empty-mark" aria-hidden="true">
        {tone === 'error' ? '!' : tone === 'warning' ? '/' : '+'}
      </div>
      <h3>{title}</h3>
      <p>{body}</p>
      {action && <div className="home-empty-action">{action}</div>}
    </div>
  )
}
