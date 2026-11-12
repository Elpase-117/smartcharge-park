export function stationCoordinates(station) {
  if (station?.longitude == null || station?.latitude == null || station.longitude === '' || station.latitude === '') return null
  const lng = Number(station.longitude), lat = Number(station.latitude)
  return Number.isFinite(lng) && Number.isFinite(lat) && lng >= 73 && lng <= 136 && lat >= 3 && lat <= 54 ? [lng, lat] : null
}

function validPosition(position) {
  if (!Array.isArray(position) || position.length < 2 ||
      position.slice(0, 2).some(value => value == null || value === '' || typeof value === 'boolean')) return null
  const [longitude, latitude] = position.map(Number)
  return Number.isFinite(longitude) && Number.isFinite(latitude) &&
    longitude >= -180 && longitude <= 180 && latitude >= -90 && latitude <= 90
    ? [longitude, latitude] : null
}

export function distanceMeters(origin, destination) {
  const from = validPosition(origin), to = validPosition(destination)
  if (!from || !to) return null
  const radians = degrees => degrees * Math.PI / 180
  const latitudeDelta = radians(to[1] - from[1])
  const longitudeDelta = radians(to[0] - from[0])
  const haversine = Math.sin(latitudeDelta / 2) ** 2 +
    Math.cos(radians(from[1])) * Math.cos(radians(to[1])) * Math.sin(longitudeDelta / 2) ** 2
  return 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, Math.max(0, haversine))))
}

export function nearestStation(stations, position) {
  if (!Array.isArray(stations)) return null
  const origin = validPosition(position)
  let closest = null, shortest = Infinity
  for (const station of stations) {
    const destination = stationCoordinates(station)
    if (!destination) continue
    if (!origin) return station
    const distance = distanceMeters(origin, destination)
    if (distance < shortest) { closest = station; shortest = distance }
  }
  return closest
}

export function formatDistance(meters) {
  if (meters == null || meters === '' || typeof meters === 'boolean') return ''
  const distance = Number(meters)
  if (!Number.isFinite(distance) || distance < 0) return ''
  return distance < 1000 ? `${Math.round(distance)} m` : `${(distance / 1000).toFixed(1)} km`
}

export function amapUri(station, origin = null) {
  const destination = stationCoordinates(station)
  if (!destination) return null
  const title = station.amapName || station.name
  const params = new URLSearchParams({ src: 'SmartChargePark', coordinate: 'gaode', callnative: '1' })
  if (origin) {
    params.set('from', `${origin[0]},${origin[1]},当前位置`)
    params.set('to', `${destination.join(',')},${title}`)
    params.set('mode', 'car')
    return `https://uri.amap.com/navigation?${params}`
  }
  params.set('position', destination.join(','))
  params.set('name', title)
  return `https://uri.amap.com/marker?${params}`
}
