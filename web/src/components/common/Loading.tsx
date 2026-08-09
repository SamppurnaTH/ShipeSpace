import type { CSSProperties } from 'react'

type SkeletonProps = {
  className?: string
  style?: CSSProperties
  circle?: boolean
}

export function LoadingSpinner({ size = 18, label = 'Loading' }: { size?: number; label?: string }) {
  return <span className="loading-spinner" role="status" aria-label={label} style={{ width: size, height: size }} />
}

export function ButtonLoader({ label }: { label: string }) {
  return (
    <span className="button-loader" role="status" aria-live="polite">
      <LoadingSpinner size={16} label={label} />
      <span>{label}</span>
    </span>
  )
}

export function PageLoader({ message = 'Loading ShipSpace...' }: { message?: string }) {
  return (
    <main className="page-loader" role="status" aria-live="polite" aria-busy="true">
      <span className="page-loader-mark" aria-hidden="true"><ShipSpaceMark /></span>
      <p>{message}</p>
      <span className="page-loader-progress" aria-hidden="true" />
    </main>
  )
}

export function Skeleton({ className = '', style, circle = false }: SkeletonProps) {
  return <span className={`skeleton ${circle ? 'skeleton-circle' : ''} ${className}`} style={style} aria-hidden="true" />
}

export function ShipCardSkeleton({ media = false }: { media?: boolean }) {
  return (
    <article className="skeleton-card ship-card-skeleton" aria-hidden="true">
      <div className="skeleton-card-head">
        <Skeleton circle style={{ width: 42, height: 42 }} />
        <div className="skeleton-stack">
          <Skeleton style={{ width: '38%', height: 13 }} />
          <Skeleton style={{ width: '26%', height: 11 }} />
        </div>
        <Skeleton style={{ width: 44, height: 11 }} />
      </div>
      <div className="skeleton-stack skeleton-content-lines">
        <Skeleton style={{ width: '96%', height: 14 }} />
        <Skeleton style={{ width: '86%', height: 14 }} />
        <Skeleton style={{ width: '58%', height: 14 }} />
      </div>
      {media && <Skeleton className="skeleton-media" />}
      <div className="skeleton-actions">
        <Skeleton style={{ width: 64, height: 24 }} />
        <Skeleton style={{ width: 74, height: 24 }} />
        <Skeleton style={{ width: 58, height: 24 }} />
      </div>
    </article>
  )
}

export function FeedSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div className="feed-skeleton" aria-busy="true" aria-label="Loading ships">
      {Array.from({ length: count }).map((_, index) => <ShipCardSkeleton key={index} media={index === 1} />)}
    </div>
  )
}

export function BuilderCardSkeleton() {
  return (
    <article className="skeleton-card builder-card-skeleton" aria-hidden="true">
      <Skeleton circle style={{ width: 72, height: 72 }} />
      <div className="skeleton-stack">
        <Skeleton style={{ width: '46%', height: 18 }} />
        <Skeleton style={{ width: '68%', height: 13 }} />
      </div>
      <div className="skeleton-stat-row">
        <Skeleton style={{ width: '30%', height: 34 }} />
        <Skeleton style={{ width: '30%', height: 34 }} />
        <Skeleton style={{ width: '30%', height: 34 }} />
      </div>
      <Skeleton style={{ width: 132, height: 38 }} />
    </article>
  )
}

export function ProjectCardSkeleton() {
  return (
    <article className="skeleton-card project-card-skeleton" aria-hidden="true">
      <Skeleton style={{ width: '44%', height: 18 }} />
      <div className="skeleton-stack">
        <Skeleton style={{ width: '94%', height: 13 }} />
        <Skeleton style={{ width: '72%', height: 13 }} />
      </div>
      <div className="skeleton-chip-row">
        <Skeleton style={{ width: 64, height: 24 }} />
        <Skeleton style={{ width: 78, height: 24 }} />
        <Skeleton style={{ width: 58, height: 24 }} />
      </div>
      <Skeleton style={{ width: '34%', height: 12 }} />
    </article>
  )
}

export function ProjectHeaderSkeleton() {
  return (
    <section className="skeleton-card project-header-skeleton" aria-hidden="true">
      <Skeleton style={{ width: '42%', height: 28 }} />
      <div className="skeleton-stack">
        <Skeleton style={{ width: '96%', height: 14 }} />
        <Skeleton style={{ width: '82%', height: 14 }} />
      </div>
      <div className="skeleton-chip-row">
        <Skeleton style={{ width: 70, height: 24 }} />
        <Skeleton style={{ width: 86, height: 24 }} />
        <Skeleton style={{ width: 62, height: 24 }} />
      </div>
      <Skeleton style={{ width: '24%', height: 12 }} />
    </section>
  )
}

export function UploadProgress({ label, tone = 'loading' }: { label: string; tone?: 'loading' | 'success' | 'error' }) {
  return (
    <div className={`upload-progress upload-progress-${tone}`} role="status" aria-live="polite" aria-busy={tone === 'loading'}>
      <span>{label}</span>
      {tone === 'loading' && <span className="upload-progress-bar" aria-hidden="true"><span /></span>}
    </div>
  )
}

function ShipSpaceMark() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M5 15.5 12 4l7 11.5" />
      <path d="M8 15.5h8" />
      <path d="M6.5 19h11" />
      <path d="M12 4v15" />
    </svg>
  )
}
