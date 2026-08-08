import { useEffect, useRef, type RefObject } from 'react'
import { fields } from './data/model'
import type { SaveGame } from './db'
import { fieldInfection } from './garden'

type GardenMapProps = {
  mapRootRef: RefObject<HTMLElement | null>
  save: SaveGame
  zoom: number
}

function drawCover(
  context: CanvasRenderingContext2D,
  image: HTMLImageElement,
  width: number,
  height: number,
) {
  const imageRatio = image.naturalWidth / image.naturalHeight
  const canvasRatio = width / height
  const drawnWidth = imageRatio > canvasRatio ? height * imageRatio : width
  const drawnHeight = imageRatio > canvasRatio ? height : width / imageRatio

  context.drawImage(
    image,
    (width - drawnWidth) / 2,
    (height - drawnHeight) / 2,
    drawnWidth,
    drawnHeight,
  )
}

function seededUnit(seed: number, salt: number): number {
  let value = (seed + Math.imul(salt, 0x9e3779b9)) >>> 0
  value = Math.imul(value ^ (value >>> 16), 0x45d9f3b)
  value = Math.imul(value ^ (value >>> 16), 0x45d9f3b)
  return ((value ^ (value >>> 16)) >>> 0) / 4294967295
}

function eraseSoftEllipse(
  context: CanvasRenderingContext2D,
  centerX: number,
  centerY: number,
  radiusX: number,
  radiusY: number,
  rotation: number,
) {
  if (radiusX <= 0 || radiusY <= 0) return

  context.save()
  context.translate(centerX, centerY)
  context.rotate(rotation)
  context.scale(radiusX, radiusY)

  const feather = context.createRadialGradient(0, 0, 0, 0, 0, 1)
  feather.addColorStop(0, 'rgba(0, 0, 0, 1)')
  feather.addColorStop(0.68, 'rgba(0, 0, 0, 1)')
  feather.addColorStop(0.86, 'rgba(0, 0, 0, .55)')
  feather.addColorStop(1, 'rgba(0, 0, 0, 0)')
  context.fillStyle = feather
  context.fillRect(-1, -1, 2, 2)
  context.restore()
}

/**
 * Paint the overgrown map over the clean map, then erase one stable, soft
 * patch per field according to its learning progress. The field buttons only
 * provide geometry and interaction; they no longer draw card-like plots.
 */
export function GardenMap({ mapRootRef, save, zoom }: GardenMapProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    const canvas = canvasRef.current
    const mapRoot = mapRootRef.current
    if (!canvas || !mapRoot) return

    const overgrownMap = new Image()
    let animationFrame = 0
    let disposed = false

    const paint = () => {
      if (!overgrownMap.complete || !overgrownMap.naturalWidth || disposed) return

      const canvasRect = canvas.getBoundingClientRect()
      const width = Math.max(1, Math.round(canvasRect.width))
      const height = Math.max(1, Math.round(canvasRect.height))
      const pixelRatio = Math.min(window.devicePixelRatio || 1, 2)
      const bitmapWidth = Math.round(width * pixelRatio)
      const bitmapHeight = Math.round(height * pixelRatio)

      if (canvas.width !== bitmapWidth || canvas.height !== bitmapHeight) {
        canvas.width = bitmapWidth
        canvas.height = bitmapHeight
      }

      const context = canvas.getContext('2d')
      if (!context) return

      context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0)
      context.clearRect(0, 0, width, height)
      context.globalCompositeOperation = 'source-over'
      drawCover(context, overgrownMap, width, height)
      context.globalCompositeOperation = 'destination-out'

      for (const field of fields) {
        const cleared = Math.max(0, Math.min(1, 1 - fieldInfection(field, save.cards)))
        if (cleared === 0) continue

        const hotspot = mapRoot.querySelector<HTMLElement>(`[data-field-id="${field.id}"]`)
        if (!hotspot) continue

        const hotspotRect = hotspot.getBoundingClientRect()
        const growth = cleared ** 0.68
        const centerX = hotspotRect.left - canvasRect.left + hotspotRect.width / 2
        const centerY = hotspotRect.top - canvasRect.top + hotspotRect.height / 2
        const angle = (seededUnit(field.seed, 1) - 0.5) * 0.34
        const drift = 1 - cleared
        const offsetX = (seededUnit(field.seed, 2) - 0.5) * hotspotRect.width * 0.18 * drift
        const offsetY = (seededUnit(field.seed, 3) - 0.5) * hotspotRect.height * 0.18 * drift
        // The last part of the cleanup expands the opaque center far enough
        // to cover the whole plot; only the feathered fringe reaches outside.
        const completeFieldReach = 0.76 + 0.29 * cleared ** 4
        const radiusX = hotspotRect.width * completeFieldReach * growth
        const radiusY = hotspotRect.height * completeFieldReach * growth

        eraseSoftEllipse(context, centerX + offsetX, centerY + offsetY, radiusX, radiusY, angle)

        // Two smaller lobes keep the reveal edge natural and deterministic.
        // They grow from the same place, so later progress never reshuffles it.
        const lobeRadius = 0.44 + cleared * 0.12
        eraseSoftEllipse(
          context,
          centerX + offsetX + (seededUnit(field.seed, 4) - 0.5) * radiusX * 0.78,
          centerY + offsetY + (seededUnit(field.seed, 5) - 0.5) * radiusY * 0.68,
          radiusX * lobeRadius,
          radiusY * lobeRadius,
          angle - 0.38,
        )
        eraseSoftEllipse(
          context,
          centerX + offsetX + (seededUnit(field.seed, 6) - 0.5) * radiusX * 0.78,
          centerY + offsetY + (seededUnit(field.seed, 7) - 0.5) * radiusY * 0.68,
          radiusX * lobeRadius,
          radiusY * lobeRadius,
          angle + 0.42,
        )
      }

      context.globalCompositeOperation = 'source-over'
    }

    const repaintWhileLayoutMoves = () => {
      const startedAt = performance.now()
      const tick = () => {
        paint()
        if (!disposed && performance.now() - startedAt < 500) {
          animationFrame = requestAnimationFrame(tick)
        }
      }
      cancelAnimationFrame(animationFrame)
      animationFrame = requestAnimationFrame(tick)
    }

    const resizeObserver = new ResizeObserver(repaintWhileLayoutMoves)
    resizeObserver.observe(mapRoot)
    overgrownMap.addEventListener('load', repaintWhileLayoutMoves)
    overgrownMap.src = '/assets/garden-map_negative.png'
    if (overgrownMap.complete) repaintWhileLayoutMoves()

    return () => {
      disposed = true
      cancelAnimationFrame(animationFrame)
      resizeObserver.disconnect()
      overgrownMap.removeEventListener('load', repaintWhileLayoutMoves)
    }
  }, [mapRootRef, save.cards, zoom])

  return <canvas className="garden-map-overlay" ref={canvasRef} aria-hidden="true" />
}
