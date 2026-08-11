import { useCallback, useEffect, useRef, useState } from 'react'
import { LockKeyhole } from 'lucide-react'
import { assetUrl } from '../assetUrl'
import { plots, type PlotDefinition } from '../data/model'
import { cellRect, ENTER_ZOOM_THRESHOLD, plotBounds, WORLD_HEIGHT, WORLD_WIDTH } from '../data/mapLayout'
import type { SaveGame } from '../db'
import { plotInfection } from '../garden'
import {
  baseMapScale,
  cameraForWorldPoint,
  clampCamera,
  clampZoom,
  focusWorldPoint,
  type CameraState,
  type Point,
  type Viewport,
  zoomAroundPoint,
} from './cameraMath'

type WorldMapProps = {
  save: SaveGame
  camera: CameraState
  onCameraChange: (camera: CameraState) => void
  onEnterPlot: (plot: PlotDefinition) => void
}

type Drag = { pointerId: number; point: Point; camera: CameraState }
type Pinch = { distance: number; worldPoint: Point; camera: CameraState }

const debugMap = new URLSearchParams(window.location.search).get('debugMap') === '1'
const DRAG_THRESHOLD = 12

function distance(left: Point, right: Point): number {
  return Math.hypot(left.x - right.x, left.y - right.y)
}

function midpoint(left: Point, right: Point): Point {
  return { x: (left.x + right.x) / 2, y: (left.y + right.y) / 2 }
}

function localPoint(event: React.PointerEvent<HTMLElement> | React.WheelEvent<HTMLElement>, viewport: DOMRect): Point {
  return { x: event.clientX - viewport.left, y: event.clientY - viewport.top }
}

function rectToWorld(rect: { x: number; y: number; width: number; height: number }) {
  return { x: rect.x * WORLD_WIDTH, y: rect.y * WORLD_HEIGHT, width: rect.width * WORLD_WIDTH, height: rect.height * WORLD_HEIGHT }
}

function seededUnit(seed: number, salt: number): number {
  let value = (seed + Math.imul(salt, 0x9e3779b9)) >>> 0
  value = Math.imul(value ^ (value >>> 16), 0x45d9f3b)
  value = Math.imul(value ^ (value >>> 16), 0x45d9f3b)
  return ((value ^ (value >>> 16)) >>> 0) / 4294967295
}

type RevealEllipse = {
  centerX: number
  centerY: number
  radiusX: number
  radiusY: number
  rotation: number
}

/**
 * Recreate the former soft canvas reveal in fixed world coordinates. Its
 * seeded position means a plot's light patch grows naturally without jumping
 * when the card state changes, and the camera never needs to recalculate it.
 */
function revealEllipses(plot: PlotDefinition, infection: number): RevealEllipse[] {
  const cleared = Math.max(0, Math.min(1, 1 - infection))
  if (cleared === 0) return []

  const bounds = rectToWorld(plotBounds(plot.cells))
  const growth = cleared ** 0.68
  const centerX = bounds.x + bounds.width / 2
  const centerY = bounds.y + bounds.height / 2
  const rotation = (seededUnit(plot.seed, 1) - 0.5) * 0.34
  const drift = 1 - cleared
  const offsetX = (seededUnit(plot.seed, 2) - 0.5) * bounds.width * 0.18 * drift
  const offsetY = (seededUnit(plot.seed, 3) - 0.5) * bounds.height * 0.18 * drift
  // The opaque center covers a clean plot; the feathered fringe can extend
  // into the surrounding garden, avoiding the previous hard cell rectangle.
  const completePlotReach = 0.76 + 0.29 * cleared ** 4
  const radiusX = bounds.width * completePlotReach * growth
  const radiusY = bounds.height * completePlotReach * growth
  const lobeRadius = 0.44 + cleared * 0.12

  return [
    { centerX: centerX + offsetX, centerY: centerY + offsetY, radiusX, radiusY, rotation },
    {
      centerX: centerX + offsetX + (seededUnit(plot.seed, 4) - 0.5) * radiusX * 0.78,
      centerY: centerY + offsetY + (seededUnit(plot.seed, 5) - 0.5) * radiusY * 0.68,
      radiusX: radiusX * lobeRadius,
      radiusY: radiusY * lobeRadius,
      rotation: rotation - 0.38,
    },
    {
      centerX: centerX + offsetX + (seededUnit(plot.seed, 6) - 0.5) * radiusX * 0.78,
      centerY: centerY + offsetY + (seededUnit(plot.seed, 7) - 0.5) * radiusY * 0.68,
      radiusX: radiusX * lobeRadius,
      radiusY: radiusY * lobeRadius,
      rotation: rotation + 0.42,
    },
  ]
}

function MapDebugOverlay() {
  return (
    <svg className="map-debug-overlay" viewBox={`0 0 ${WORLD_WIDTH} ${WORLD_HEIGHT}`} aria-hidden="true">
      {plots.flatMap((plot) => plot.cells.map((cell) => {
        const rect = rectToWorld(cellRect(cell))
        return <rect key={`${plot.id}:${cell.x}:${cell.y}`} {...rect} className="debug-cell" />
      }))}
      {plots.map((plot) => {
        const rect = rectToWorld(plotBounds(plot.cells))
        return (
          <g key={plot.id}>
            <rect {...rect} className="debug-plot" />
            <text x={rect.x + 5} y={rect.y + 15}>{plot.id}</text>
          </g>
        )
      })}
    </svg>
  )
}

/** A transformed world. Weed mask re-renders only for save changes, never for camera movement. */
export function WorldMap({ save, camera, onCameraChange, onEnterPlot }: WorldMapProps) {
  const viewportRef = useRef<HTMLElement>(null)
  const worldRef = useRef<HTMLDivElement>(null)
  const cameraRef = useRef(camera)
  const pointersRef = useRef(new Map<number, Point>())
  const dragRef = useRef<Drag | null>(null)
  const pinchRef = useRef<Pinch | null>(null)
  const movedRef = useRef(false)
  const wheelCommitRef = useRef<number | null>(null)
  const [lockedPulseId, setLockedPulseId] = useState<string | null>(null)

  const getViewport = useCallback((): Viewport | null => {
    const rect = viewportRef.current?.getBoundingClientRect()
    return rect ? { width: rect.width, height: rect.height } : null
  }, [])

  const paintCamera = useCallback((next: CameraState, transition = false): CameraState | null => {
    const viewport = getViewport()
    const world = worldRef.current
    if (!viewport || !world) return null
    const clamped = clampCamera(next, viewport)
    cameraRef.current = clamped
    world.style.transition = transition ? 'transform 360ms cubic-bezier(.2,.75,.2,1)' : 'none'
    world.style.transform = `translate3d(${viewport.width / 2 + clamped.x}px, ${viewport.height / 2 + clamped.y}px, 0) scale(${baseMapScale(viewport) * clamped.zoom}) translate3d(-${WORLD_WIDTH / 2}px, -${WORLD_HEIGHT / 2}px, 0)`
    return clamped
  }, [getViewport])

  const commitCamera = useCallback(() => onCameraChange(cameraRef.current), [onCameraChange])

  useEffect(() => {
    paintCamera(camera)
  }, [camera, paintCamera])

  useEffect(() => {
    const viewport = viewportRef.current
    if (!viewport) return
    const observer = new ResizeObserver(() => {
      const next = paintCamera(cameraRef.current)
      if (next) onCameraChange(next)
    })
    observer.observe(viewport)
    return () => observer.disconnect()
  }, [onCameraChange, paintCamera])

  useEffect(() => () => {
    if (wheelCommitRef.current) window.clearTimeout(wheelCommitRef.current)
  }, [])

  const startPinch = () => {
    const viewport = getViewport()
    const pointerValues = [...pointersRef.current.values()]
    if (!viewport || pointerValues.length !== 2) return
    const center = midpoint(pointerValues[0]!, pointerValues[1]!)
    pinchRef.current = {
      distance: distance(pointerValues[0]!, pointerValues[1]!),
      worldPoint: {
        x: WORLD_WIDTH / 2 + (center.x - viewport.width / 2 - cameraRef.current.x) / (baseMapScale(viewport) * cameraRef.current.zoom),
        y: WORLD_HEIGHT / 2 + (center.y - viewport.height / 2 - cameraRef.current.y) / (baseMapScale(viewport) * cameraRef.current.zoom),
      },
      camera: cameraRef.current,
    }
    dragRef.current = null
  }

  const onPointerDown = (event: React.PointerEvent<HTMLElement>) => {
    const viewport = viewportRef.current
    if (!viewport) return
    // Capturing immediately makes mobile browsers send the resulting click to
    // the map container instead of the plot button.  A real drag captures
    // later, after it crosses the gesture threshold.
    if (!(event.target instanceof Element && event.target.closest('.plot-hotspot'))) {
      viewport.setPointerCapture(event.pointerId)
    }
    pointersRef.current.set(event.pointerId, localPoint(event, viewport.getBoundingClientRect()))
    movedRef.current = false
    if (pointersRef.current.size === 1) {
      dragRef.current = { pointerId: event.pointerId, point: pointersRef.current.get(event.pointerId)!, camera: cameraRef.current }
    } else if (pointersRef.current.size === 2) {
      startPinch()
    }
  }

  const onPointerMove = (event: React.PointerEvent<HTMLElement>) => {
    const viewportElement = viewportRef.current
    const viewport = getViewport()
    if (!viewportElement || !viewport || !pointersRef.current.has(event.pointerId)) return
    pointersRef.current.set(event.pointerId, localPoint(event, viewportElement.getBoundingClientRect()))
    const pointerValues = [...pointersRef.current.values()]
    if (pointerValues.length === 2 && pinchRef.current) {
      const pinch = pinchRef.current
      const nextDistance = distance(pointerValues[0]!, pointerValues[1]!)
      if (pinch.distance <= 0) return
      const next = cameraForWorldPoint(pinch.worldPoint, midpoint(pointerValues[0]!, pointerValues[1]!), clampZoom(pinch.camera.zoom * nextDistance / pinch.distance), viewport)
      paintCamera(next)
      movedRef.current = true
      return
    }
    const drag = dragRef.current
    if (!drag || drag.pointerId !== event.pointerId) return
    const point = pointerValues[0]!
    const deltaX = point.x - drag.point.x
    const deltaY = point.y - drag.point.y
    // Touch emulation (and some trackpads) reports a few pixels of movement
    // while completing an otherwise ordinary tap.  Keep taps reliable without
    // compromising intentional map panning.
    if (!movedRef.current && Math.hypot(deltaX, deltaY) <= DRAG_THRESHOLD) return
    movedRef.current = true
    if (!viewportElement.hasPointerCapture(event.pointerId)) viewportElement.setPointerCapture(event.pointerId)
    paintCamera({ ...drag.camera, x: drag.camera.x + deltaX, y: drag.camera.y + deltaY })
  }

  const finishPointer = (event: React.PointerEvent<HTMLElement>) => {
    const shouldCommit = movedRef.current
    pointersRef.current.delete(event.pointerId)
    if (pointersRef.current.size === 1) {
      const [pointerId, point] = [...pointersRef.current.entries()][0]!
      dragRef.current = { pointerId, point, camera: cameraRef.current }
      pinchRef.current = null
    } else {
      dragRef.current = null
      pinchRef.current = null
    }
    if (shouldCommit) commitCamera()
  }

  const onWheel = (event: React.WheelEvent<HTMLElement>) => {
    event.preventDefault()
    const viewport = getViewport()
    const element = viewportRef.current
    if (!viewport || !element) return
    const intensity = event.deltaMode === 1 ? 0.045 : 0.0015
    const targetZoom = clampZoom(cameraRef.current.zoom * Math.exp(-event.deltaY * intensity))
    paintCamera(zoomAroundPoint(cameraRef.current, localPoint(event, element.getBoundingClientRect()), targetZoom, viewport))
    if (wheelCommitRef.current) window.clearTimeout(wheelCommitRef.current)
    wheelCommitRef.current = window.setTimeout(commitCamera, 120)
  }

  const activatePlot = (plot: PlotDefinition) => {
    const viewport = getViewport()
    if (!viewport) return
    if (cameraRef.current.zoom < ENTER_ZOOM_THRESHOLD) {
      const bounds = rectToWorld(plotBounds(plot.cells))
      const next = focusWorldPoint({ x: bounds.x + bounds.width / 2, y: bounds.y + bounds.height / 2 }, Math.max(ENTER_ZOOM_THRESHOLD, cameraRef.current.zoom * 1.75), viewport)
      paintCamera(next, true)
      commitCamera()
      return
    }
    if (save.unlockedPlotIds.includes(plot.id)) {
      onEnterPlot(plot)
      return
    }
    setLockedPulseId(plot.id)
    window.setTimeout(() => setLockedPulseId((current) => current === plot.id ? null : current), 500)
  }

  return (
    <section
      className="world-map-viewport"
      ref={viewportRef}
      aria-label="Карта сада: перетаскивайте для перемещения, используйте колесо или щипок для масштаба"
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={finishPointer}
      onPointerCancel={finishPointer}
      onWheel={onWheel}
    >
      <div className="world-map-world" ref={worldRef}>
        <img className="world-map-clean" src={assetUrl('assets/garden-map.png')} alt="" draggable={false} />
        <svg className="world-map-weed" viewBox={`0 0 ${WORLD_WIDTH} ${WORLD_HEIGHT}`} aria-hidden="true">
          <defs>
            <radialGradient id="world-weed-reveal" cx="50%" cy="50%" r="50%">
              <stop offset="0%" stopColor="black" />
              <stop offset="68%" stopColor="black" />
              <stop offset="86%" stopColor="black" stopOpacity=".55" />
              <stop offset="100%" stopColor="black" stopOpacity="0" />
            </radialGradient>
            <mask id="world-weed-mask" maskUnits="userSpaceOnUse" maskContentUnits="userSpaceOnUse">
              <rect width={WORLD_WIDTH} height={WORLD_HEIGHT} fill="white" />
              <g style={{ mixBlendMode: 'multiply' }}>
                {plots.flatMap((plot) => {
                  const infection = save.unlockedPlotIds.includes(plot.id) ? plotInfection(plot, save.cards) : 1
                  return revealEllipses(plot, infection).map((ellipse, index) => (
                    <ellipse
                      key={`${plot.id}:${index}`}
                      cx="0"
                      cy="0"
                      rx="1"
                      ry="1"
                      fill="url(#world-weed-reveal)"
                      transform={`translate(${ellipse.centerX} ${ellipse.centerY}) rotate(${ellipse.rotation * 180 / Math.PI}) scale(${ellipse.radiusX} ${ellipse.radiusY})`}
                    />
                  ))
                })}
              </g>
            </mask>
          </defs>
          <image href={assetUrl('assets/garden-map_negative.png')} width={WORLD_WIDTH} height={WORLD_HEIGHT} preserveAspectRatio="none" mask="url(#world-weed-mask)" />
        </svg>
        <div className="world-map-hotspots">
          {plots.map((plot) => {
            const rect = plotBounds(plot.cells)
            const unlocked = save.unlockedPlotIds.includes(plot.id)
            // Empty second halves can occur for a one-character source list.
            // A locked plot remains visibly overgrown until the player reaches it.
            const infection = unlocked ? plotInfection(plot, save.cards) : 1
            return (
              <button
                className={`plot-hotspot ${unlocked ? 'is-unlocked' : 'is-locked'} ${lockedPulseId === plot.id ? 'is-locked-pulse' : ''}`}
                key={plot.id}
                style={{ left: `${rect.x * 100}%`, top: `${rect.y * 100}%`, width: `${rect.width * 100}%`, height: `${rect.height * 100}%` }}
                data-plot-id={plot.id}
                onClick={() => activatePlot(plot)}
                aria-label={`Участок ${plot.id}, зарастание ${Math.ceil(infection * 10)} из 10${unlocked ? '' : ', путь закрыт'}`}
              >
                {!unlocked && <LockKeyhole className="plot-lock" />}
              </button>
            )
          })}
        </div>
        {debugMap && <MapDebugOverlay />}
      </div>
    </section>
  )
}
