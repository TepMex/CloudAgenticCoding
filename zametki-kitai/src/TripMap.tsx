import { buildTripMap } from './trip-map'

export function TripMap({
  dayId,
  place,
  paragraphs,
}: {
  dayId: string
  place: string
  paragraphs: string[]
}) {
  const model = buildTripMap(dayId, place, paragraphs)
  if (!model) return null
  const markerId = `trip-arrow-${dayId}`
  return (
    <div className="trip-map">
      <svg viewBox={model.viewBox} role="img" aria-label={model.aria}>
        <defs>
          <marker
            id={markerId}
            viewBox="0 0 12 12"
            refX="9"
            refY="6"
            markerWidth="7"
            markerHeight="7"
            orient="auto"
            markerUnits="userSpaceOnUse"
          >
            <path
              d="M1.6 1.7 L9.4 6 L1.6 10.3"
              fill="none"
              stroke="#8a3a32"
              strokeWidth="1.7"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </marker>
        </defs>
        {model.lands.map((land) => (
          <path
            key={land.id}
            d={land.d}
            className={
              land.id === 'cn' ? 'land land-china' : land.id === 'ru' ? 'land land-russia' : 'land land-neighbor'
            }
          />
        ))}
        {model.routes.map((d, index) => (
          <path key={index} d={d} className="route-halo" />
        ))}
        {model.routes.map((d, index) => (
          <path key={`arrow-${index}`} d={d} className="route" markerEnd={`url(#${markerId})`} />
        ))}
        {model.labels.map((label, index) =>
          label.leader ? (
            <line
              key={`leader-${index}`}
              x1={label.leader.x1}
              y1={label.leader.y1}
              x2={label.leader.x2}
              y2={label.leader.y2}
              className="leader"
            />
          ) : null,
        )}
        {model.pins.map((pin) => (
          <circle key={pin.id} cx={pin.x} cy={pin.y} r={4.2} className="pin" />
        ))}
        {model.labels.map((label, index) => (
          <text
            key={`${label.kind}-${index}`}
            x={label.x}
            y={label.y}
            textAnchor={label.anchor}
            className={label.kind === 'country' ? 'map-label country-label' : 'map-label city-label'}
            fontSize={label.kind === 'country' ? 12 : 16}
            letterSpacing={label.kind === 'country' ? 1.4 : 0}
            stroke={label.kind === 'city' ? '#f7f1e6' : 'none'}
            strokeWidth={label.kind === 'city' ? 3.5 : 0}
            paintOrder="stroke"
          >
            {label.text}
          </text>
        ))}
      </svg>
    </div>
  )
}
