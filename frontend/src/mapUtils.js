export function stationCoordinates(station) {
  if (station?.longitude == null || station?.latitude == null || station.longitude === '' || station.latitude === '') return null
  const lng = Number(station.longitude), lat = Number(station.latitude)
  return Number.isFinite(lng) && Number.isFinite(lat) && lng >= 73 && lng <= 136 && lat >= 3 && lat <= 54 ? [lng, lat] : null
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
