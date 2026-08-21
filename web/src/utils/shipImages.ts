export interface ShipImageReference {
  multibaseHash?: string
  filename?: string
  contentType?: string
  size?: number
  url?: string
  src?: string
}

export type ShipImage = string | ShipImageReference

export function getShipImageSrc(image: ShipImage): string | null {
  if (typeof image === 'string') return normalizeImageSource(image)

  const direct = image.url ?? image.src ?? image.multibaseHash
  return direct ? normalizeImageSource(direct) : null
}

export function getShipImageAlt(image: ShipImage, index: number): string {
  if (typeof image !== 'string' && image.filename) return image.filename
  return `Ship image ${index + 1}`
}

function normalizeImageSource(value: string): string | null {
  const trimmed = value.trim()
  if (!trimmed) return null
  if (/^https?:\/\//i.test(trimmed) || trimmed.startsWith('data:') || trimmed.startsWith('/')) {
    return trimmed
  }
  if (trimmed.startsWith('ipfs://')) {
    return `https://ipfs.io/ipfs/${trimmed.slice('ipfs://'.length)}`
  }
  return `https://ipfs.io/ipfs/${trimmed}`
}
