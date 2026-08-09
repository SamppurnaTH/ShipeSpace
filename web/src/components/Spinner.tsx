import { LoadingSpinner } from './common/Loading'

interface SpinnerProps { size?: number; color?: string }

export function Spinner({ size = 24 }: SpinnerProps) {
  return <LoadingSpinner size={size} />
}
