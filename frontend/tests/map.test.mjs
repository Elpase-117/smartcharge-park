import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { computed, effectScope, nextTick, reactive, ref, watch } from 'vue'
import * as Vue from 'vue'
import { parse } from '@vue/compiler-sfc'
import { parse as parseTemplate, compile } from '@vue/compiler-dom'
import { stationCoordinates, amapUri, distanceMeters, nearestStation, formatDistance } from '../src/mapUtils.js'

const station = { id: 4, name: '小鹏（吾悦 & 广场）', amapName: '小鹏标准名称', longitude: '106.432610', latitude: '29.823147', operatorBrand: '小鹏', chargingMode: '超充/快充', availableChargers: 0, totalChargers: 4 }
const nearStation = { ...station, id: 8, name: '最近站点', longitude: 106.4001, latitude: 29.8 }
const farStation = { ...station, id: 9, name: '较远站点', longitude: 106.5, latitude: 29.9 }
const position = [106.4, 29.8]
const allStations = [station, nearStation, farStation, ...Array.from({ length: 7 }, (_, index) => ({ ...farStation, id: 20 + index }))]
// The global nearest station is deliberately absent; the filtered nearest is last.
const filteredStations = [farStation, allStations[3], station]

test('only valid saved China coordinates can create station markers', () => {
  assert.deepEqual(stationCoordinates(station), [106.43261, 29.823147])
  for (const invalid of [null, {}, { longitude: null, latitude: 29 }, { longitude: '', latitude: '' }, { longitude: 0, latitude: 0 }, { longitude: NaN, latitude: 29 }, { longitude: -100, latitude: 30 }]) assert.equal(stationCoordinates(invalid), null)
})

test('Haversine distance uses both real coordinates and rejects invalid points', () => {
  const north = distanceMeters([106, 29], [106, 30])
  assert.ok(Math.abs(north - 111195) < 150, `one degree of latitude was ${north} m`)
  const east = distanceMeters([106, 30], [107, 30])
  assert.ok(Math.abs(east - 96297) < 150, `one degree of longitude at 30° was ${east} m`)
  assert.equal(distanceMeters(position, position), 0)
  assert.equal(distanceMeters(position, [106.5, 29.9]), distanceMeters([106.5, 29.9], position))
  for (const invalid of [null, [], [106], [NaN, 29], [Infinity, 29], [181, 29], [106, 91]]) {
    assert.equal(distanceMeters(invalid, position), null)
    assert.equal(distanceMeters(position, invalid), null)
  }
})

test('nearest station is calculated rather than chosen by ID or list order', () => {
  const items = [{ id: 1 }, farStation, station, nearStation]
  assert.equal(nearestStation(items, position), nearStation)
  assert.equal(nearestStation([...items].reverse(), position), nearStation)
  assert.equal(nearestStation([nearStation, farStation], [106.5, 29.9]), farStation)
  assert.equal(nearestStation(items, null), farStation)
  assert.equal(nearestStation(items, [NaN, 29]), farStation)
  assert.equal(nearestStation([{ id: 1 }, { longitude: '', latitude: '' }], position), null)
  assert.equal(nearestStation([], position), null)
})

test('distance formatting provides rounded meters or one decimal kilometer', () => {
  for (const [meters, expected] of [[0, '0 m'], [519.6, '520 m'], [999, '999 m'], [1000, '1.0 km'], [1283.746, '1.3 km'], [8600, '8.6 km']]) assert.equal(formatDistance(meters), expected)
  for (const invalid of [null, undefined, NaN, Infinity, -1]) assert.equal(formatDistance(invalid), '')
})

test('navigation URI encodes real origin and destination; fallback invents no origin', () => {
  const url = new URL(amapUri(station, position))
  assert.equal(url.pathname, '/navigation')
  assert.equal(url.searchParams.get('mode'), 'car')
  assert.equal(url.searchParams.get('to'), '106.43261,29.823147,小鹏标准名称')
  assert.equal(url.searchParams.get('from'), '106.4,29.8,当前位置')
  assert.equal(url.searchParams.get('coordinate'), 'gaode')
  assert.equal(url.searchParams.get('callnative'), '1')
  const fallback = new URL(amapUri(station))
  assert.equal(fallback.pathname, '/marker')
  assert.equal(fallback.searchParams.has('from'), false)
  assert.equal(amapUri({}), null)
})

function setupSource(path) {
  return readFileSync(new URL(path, import.meta.url), 'utf8').match(/<script setup>([\s\S]*?)<\/script>/)[1]
    .replace(/^import .*$/gm, '').replaceAll('import.meta.env', 'environment')
}

function runSetup(path, bindings, names = []) {
  return new Function(...Object.keys(bindings), `${setupSource(path)}\nreturn { ${names.join(', ')} }`)(...Object.values(bindings))
}

async function flush() {
  // Await Vue watchers and SDK/API promise chains without real timers.
  for (let i = 0; i < 6; i++) { await nextTick(); await Promise.resolve() }
}

// Run real setup code with Vue reactivity and local SDK doubles. No browser
// permissions, network requests, provider quota, or additional test dependency.
async function component(t, options = {}) {
  const events = [], hooks = [], made = [], routes = [], pendingLocations = [], pendingRoutes = []
  const state = { locationCalls: 0, sdkCalls: 0, infoWindows: 0, maximumMarkers: 0 }
  const props = reactive({ activeStation: options.activeStation === undefined ? station : options.activeStation })
  const scope = effectScope()
  let exposed
  class Marker {
    constructor(config) { this.config = config; this.events = {}; made.push(this); if (config.map) this.setMap(config.map) }
    on(name, fn) { this.events[name] = fn }
    setLabel(label) { this.label = label }
    setzIndex(value) { this.z = value }
    setPosition(value) { this.config.position = value }
    setTitle(value) { this.config.title = value }
    setMap(value) { this.map?.remove(this); if (value) value.add(this) }
  }
  class MapDouble {
    constructor() { state.map = this; this.overlays = new Set(); this.events = {} }
    on(name, fn) { this.events[name] = fn }
    add(items) {
      for (const item of Array.isArray(items) ? items : [items]) { this.overlays.add(item); item.map = this }
      state.maximumMarkers = Math.max(state.maximumMarkers, this.overlays.size)
    }
    remove(items) { for (const item of Array.isArray(items) ? items : [items]) { this.overlays.delete(item); item.map = null } }
    setFitView(items) { this.fitted = items }
    setZoomAndCenter(zoom, value) { this.zoom = zoom; this.position = value }
    destroy() { this.destroyed = true; this.overlays.clear() }
  }
  class InfoWindow { constructor() { state.infoWindows++ } }
  class Driving {
    constructor(config) { this.config = config; routes.push(this) }
    search(from, to, callback) {
      this.from = from; this.to = to
      const finish = () => callback('complete', { routes: [{ distance: 1200, time: 300 }] })
      if (options.holdRoute) pendingRoutes.push(finish)
      else finish()
    }
    clear() { this.cleared = true }
  }
  class Geolocation {
    constructor(config) { state.geolocationConfig = config }
    getCurrentPosition(callback) {
      const index = state.locationCalls++
      const responses = options.locations || [{ status: 'complete', result: { position: { lng: 106.4, lat: 29.8 }, location_type: 'html5' } }]
      const response = responses[Math.min(index, responses.length - 1)]
      const finish = () => callback(response.status, response.result)
      if (options.holdLocation) pendingLocations.push(finish)
      else finish()
    }
  }
  const sdk = { Map: MapDouble, Marker, InfoWindow, Driving, Geolocation, Pixel: class {} }
  const loader = { load: async () => { state.sdkCalls++; return sdk }, reset() {} }
  const window = { open: () => ({ opener: null, location: { replace(url) { state.opened = url } }, close() { state.blankClosed = true } }) }
  const internals = scope.run(() => runSetup('../src/components/StationMap.vue', {
    computed, nextTick, onBeforeUnmount: fn => hooks.push(fn), onMounted: fn => { state.mount = fn }, ref, watch,
    defineProps: () => props, defineEmits: () => (...args) => events.push(args), defineExpose: value => { exposed = value },
    AMapLoader: loader, amapUri, stationCoordinates, window,
    fetch: async () => ({ ok: !options.proxyFailed, json: async () => ({ configured: !options.proxyUnconfigured }) }),
    environment: { VITE_AMAP_JS_KEY: options.noKey ? '' : 'sdk-test-placeholder' }, setTimeout: () => 1, clearTimeout: () => {}
  }, ['clearRoute', 'fallbackUrl', 'navigationUrl', 'routeSummary', 'message', 'status']))
  t.after(() => { for (const hook of hooks) hook(); scope.stop() })
  const mounting = state.mount()
  await flush()
  if (!options.holdLocation) { await mounting; await flush() }
  return { events, made, routes, state, props, exposed, internals, window, mounting,
    finishLocation: async () => { assert.ok(pendingLocations.length, 'a geolocation request must be pending'); pendingLocations.shift()(); await flush() },
    finishRoute: async () => { assert.ok(pendingRoutes.length, 'a route request must be pending'); pendingRoutes.shift()(); await flush() },
    markers: () => [...(state.map?.overlays || [])] }
}

test('missing JS key and unavailable proxy do not request location or initialize SDK', async t => {
  for (const options of [{ noKey: true }, { proxyFailed: true }, { proxyUnconfigured: true }]) {
    const c = await component(t, options)
    assert.equal(c.state.sdkCalls, 0)
    assert.equal(c.state.locationCalls, 0)
    assert.equal(c.made.length, 0)
  }
})

test('map initialization attempts location once and emits the real successful position', async t => {
  const c = await component(t)
  assert.equal(c.state.locationCalls, 1)
  assert.deepEqual(c.events.filter(event => event[0] === 'location-state').map(event => event[1]), ['loading', 'success'])
  assert.deepEqual(c.events.find(event => event[0] === 'location'), ['location', position])
  assert.equal(c.markers().length, 1)
  assert.equal(c.state.infoWindows, 0)
  assert.equal(c.state.geolocationConfig.showMarker, false)
  assert.equal(c.state.geolocationConfig.showCircle, false)
  assert.equal(c.state.geolocationConfig.panToLocation, false)
  assert.deepEqual(c.markers()[0].config.position, stationCoordinates(station))
  c.markers()[0].events.click()
  assert.deepEqual(c.events.find(event => event[0] === 'select'), ['select', station])
})

test('only activeStation supplies the marker across focus, changes and invalid selection', async t => {
  const c = await component(t)
  c.exposed.focus(station)
  assert.deepEqual(c.state.map.position, stationCoordinates(station))
  c.props.activeStation = nearStation
  await flush()
  assert.equal(c.markers().length, 1)
  assert.deepEqual(c.markers()[0].config.position, stationCoordinates(nearStation))
  c.markers()[0].events.click()
  assert.deepEqual(c.events.filter(event => event[0] === 'select').at(-1), ['select', nearStation])
  c.props.activeStation = { id: 20 }
  await flush()
  assert.equal(c.markers().length, 0)
  c.props.activeStation = farStation
  await flush()
  assert.equal(c.markers().length, 1)
  c.props.activeStation = null
  await flush()
  assert.equal(c.markers().length, 0)
  assert.equal(c.state.maximumMarkers, 1)
  assert.equal(c.state.locationCalls, 1)
})

test('routes and navigation share automatic location; clearing routes retains the marker', async t => {
  const c = await component(t)
  const marker = c.markers()[0]
  await c.exposed.planRoute(station)
  assert.equal(c.state.locationCalls, 1)
  assert.equal(c.routes.length, 1)
  assert.equal(c.routes[0].config.map, c.state.map)
  assert.deepEqual(c.routes[0].from, position)
  assert.deepEqual(c.routes[0].to, stationCoordinates(station))
  assert.ok(c.internals.routeSummary.value)
  c.internals.clearRoute()
  assert.equal(c.routes[0].cleared, true)
  assert.deepEqual(c.markers(), [marker])
  await c.exposed.navigate(station)
  const url = new URL(c.state.opened)
  assert.equal(url.pathname, '/navigation')
  assert.equal(url.searchParams.get('from'), '106.4,29.8,当前位置')
  assert.equal(c.state.locationCalls, 1)
  assert.deepEqual(c.markers(), [marker])
  assert.equal(c.window._AMapSecurityConfig.securityJsCode, undefined)
  assert.equal(c.window._AMapSecurityConfig.serviceHost, 'http://127.0.0.1:8084/_AMapService')
})

test('changing activeStation clears a previous route and retains only its new marker', async t => {
  const c = await component(t)
  await c.exposed.planRoute(station)
  c.props.activeStation = nearStation
  await flush()
  assert.equal(c.routes[0].cleared, true)
  assert.equal(c.markers().length, 1)
  assert.deepEqual(c.markers()[0].config.position, stationCoordinates(nearStation))
  assert.equal(c.state.maximumMarkers, 1)
})

test('denied location is cached across station changes, routes and navigation until explicit retry', async t => {
  const c = await component(t, { locations: [
    { status: 'error', result: {} },
    { status: 'complete', result: { position: { lng: 106.4, lat: 29.8 }, location_type: 'html5' } }
  ] })
  assert.deepEqual(c.events.filter(event => event[0] === 'location-state').map(event => event[1]), ['loading', 'failed'])
  assert.deepEqual(c.events.find(event => event[0] === 'location'), ['location', null])
  assert.equal(c.markers().length, 1)
  c.props.activeStation = nearStation
  await flush()
  await c.exposed.planRoute(nearStation)
  await c.exposed.navigate(nearStation)
  assert.equal(c.routes.length, 0)
  assert.equal(c.state.locationCalls, 1)
  assert.equal(c.state.opened, undefined)
  assert.equal(c.state.blankClosed, true)
  assert.equal(new URL(c.internals.fallbackUrl.value).pathname, '/marker')
  assert.equal(new URL(c.internals.fallbackUrl.value).searchParams.has('from'), false)
  await c.exposed.retryLocation()
  await flush()
  assert.equal(c.state.locationCalls, 2)
  assert.deepEqual(c.events.filter(event => event[0] === 'location-state').map(event => event[1]), ['loading', 'failed', 'loading', 'success'])
  assert.deepEqual(c.events.filter(event => event[0] === 'location').at(-1), ['location', position])
  await c.exposed.planRoute(nearStation)
  assert.equal(c.routes.length, 1)
  assert.equal(c.state.locationCalls, 2)
})

test('IP estimates and malformed geolocation never become a fake nearest-station origin', async t => {
  for (const result of [
    { position: { lng: 106.4, lat: 29.8 }, location_type: 'ip' },
    { position: { lng: NaN, lat: 29.8 }, location_type: 'html5' }
  ]) {
    const c = await component(t, { locations: [{ status: 'complete', result }] })
    assert.deepEqual(c.events.filter(event => event[0] === 'location').at(-1), ['location', null])
    assert.equal(c.events.filter(event => event[0] === 'location-state').at(-1)[1], 'failed')
    await c.exposed.planRoute(station)
    assert.equal(c.state.locationCalls, 1)
    assert.equal(c.routes.length, 0)
  }
})

test('a route requested during automatic location shares its pending request', async t => {
  const c = await component(t, { holdLocation: true })
  assert.equal(c.state.locationCalls, 1)
  const route = c.exposed.planRoute(station)
  await flush()
  assert.equal(c.state.locationCalls, 1)
  await c.finishLocation()
  await c.mounting
  await route
  assert.equal(c.routes.length, 1)
})

test('changing selection while geolocation is pending cancels the old destination route', async t => {
  const c = await component(t, { holdLocation: true })
  const route = c.exposed.planRoute(station)
  c.props.activeStation = nearStation
  await flush()
  await c.finishLocation()
  await route
  assert.equal(c.routes.length, 0)
  assert.equal(c.markers().length, 1)
  assert.deepEqual(c.markers()[0].config.position, stationCoordinates(nearStation))
})

test('same ID and coordinates with refreshed metadata preserve pending and completed routes', async t => {
  const c = await component(t, { holdRoute: true })
  const route = c.exposed.planRoute(station)
  await flush()
  assert.equal(c.routes.length, 1)
  c.props.activeStation = { ...station, name: '详情加载后的站点名称', longitude: Number(station.longitude), latitude: Number(station.latitude), availableChargers: 2 }
  await flush()
  assert.notEqual(c.routes[0].cleared, true)
  await c.finishRoute()
  await route
  const summary = c.internals.routeSummary.value
  assert.ok(summary)
  c.props.activeStation = { ...c.props.activeStation, address: '更新后的站点地址' }
  await flush()
  assert.notEqual(c.routes[0].cleared, true)
  assert.equal(c.internals.routeSummary.value, summary)
  c.internals.clearRoute()
  assert.equal(c.markers().length, 1)
  c.markers()[0].events.click()
  assert.equal(c.events.filter(event => event[0] === 'select').at(-1)[1].name, '详情加载后的站点名称')
  assert.equal(c.state.maximumMarkers, 1)
})

test('same-target metadata replacement preserves navigation while its location is pending', async t => {
  const c = await component(t, { holdLocation: true })
  const navigation = c.exposed.navigate(station)
  c.props.activeStation = { ...station, name: '详情加载后的站点名称', longitude: Number(station.longitude), latitude: Number(station.latitude) }
  await flush()
  assert.notEqual(c.state.blankClosed, true)
  await c.finishLocation()
  await navigation
  assert.notEqual(c.state.blankClosed, true)
  const url = new URL(c.state.opened)
  assert.equal(url.pathname, '/navigation')
  assert.equal(url.searchParams.get('to').split(',').slice(0, 2).join(','), stationCoordinates(station).join(','))
  assert.equal(c.state.locationCalls, 1)
  assert.equal(c.markers().length, 1)
})

test('a different ID or changed coordinates cancel an in-flight route', async t => {
  for (const destination of [{ ...station, id: 99 }, { ...station, longitude: 106.6 }]) {
    const c = await component(t, { holdRoute: true })
    const route = c.exposed.planRoute(station)
    await flush()
    assert.equal(c.routes.length, 1)
    c.props.activeStation = destination
    await flush()
    assert.equal(c.routes[0].cleared, true)
    await c.finishRoute()
    await route
    assert.equal(c.internals.routeSummary.value, '')
    assert.equal(c.markers().length, 1)
    assert.deepEqual(c.markers()[0].config.position, stationCoordinates(destination))
  }
})

test('a different ID or changed coordinates cancel pending navigation and close its blank tab', async t => {
  for (const destination of [{ ...station, id: 99 }, { ...station, longitude: 106.6 }]) {
    const c = await component(t, { holdLocation: true })
    const navigation = c.exposed.navigate(station)
    c.props.activeStation = destination
    await flush()
    assert.equal(c.state.blankClosed, true)
    await c.finishLocation()
    await navigation
    assert.equal(c.state.opened, undefined)
    assert.equal(c.internals.navigationUrl.value, '')
    assert.equal(c.markers().length, 1)
    assert.deepEqual(c.markers()[0].config.position, stationCoordinates(destination))
  }
})

function app(t, overrides = {}) {
  const calls = { searches: [], reservations: [], messages: [] }
  const api = {
    stations: async keyword => { calls.searches.push(keyword); return keyword ? filteredStations : allStations },
    station: async id => ({ ...[station, nearStation, farStation].find(item => item.id === id) }),
    reservationRules: async () => ({ maxDays: 7 }),
    availability: async () => [{ timeSlot: '10:00-12:00', remaining: 2, capacity: 2 }],
    reserve: async payload => { calls.reservations.push(payload); return { ...payload, reservationId: 11, orderNo: 'test-order', estimatedAmount: 20 } },
    ...overrides
  }
  const scope = effectScope()
  const messages = Object.fromEntries(['error', 'warning', 'success'].map(type => [type, value => calls.messages.push([type, value])]))
  const storage = new Map()
  const state = scope.run(() => runSetup('../src/App.vue', {
    computed, nextTick, onMounted: () => {}, reactive, ref, watch, ElMessage: messages, api,
    distanceMeters, formatDistance, nearestStation, stationCoordinates,
    localStorage: { getItem: key => storage.get(key) || null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) }
  }, ['stations', 'keyword', 'appliedKeyword', 'activeMapStation', 'userLocation', 'locationStatus', 'mapSelectionMode', 'recommendationTitle', 'activeDistance', 'selected', 'stationMap', 'reservation', 'reservationMaxDays', 'resultVisible', 'resultStation', 'result', 'availability', 'loadStations', 'receiveLocation', 'retryLocation', 'openStation', 'mapAction', 'logout', 'disabledReservationDate', 'loadReservationRules', 'loadAvailability', 'createReservation', 'formatLocalDate']))
  t.after(() => scope.stop())
  return { ...state, calls }
}

test('home retains its full list, selects the true nearest station, and uses honest fallback labels', async t => {
  const a = app(t)
  await a.loadStations()
  assert.equal(a.stations.value.length, 10)
  assert.equal(a.activeMapStation.value.id, station.id)
  assert.equal(a.recommendationTitle.value, '推荐站点')
  assert.equal(a.activeDistance.value, '')
  a.receiveLocation(position)
  assert.equal(a.activeMapStation.value.id, nearStation.id)
  assert.equal(a.recommendationTitle.value, '距您最近')
  assert.equal(a.activeDistance.value, formatDistance(distanceMeters(position, stationCoordinates(nearStation))))
  a.receiveLocation(null)
  assert.equal(a.activeMapStation.value.id, station.id)
  assert.equal(a.recommendationTitle.value, '推荐站点')
  assert.equal(a.activeDistance.value, '')
})

test('search excludes the global closest, selects the nearest filtered result, and clear restores all ten and their nearest', async t => {
  const a = app(t)
  await a.loadStations()
  a.receiveLocation(position)
  assert.equal(a.activeMapStation.value.id, nearStation.id)
  await a.openStation(farStation)
  assert.equal(a.mapSelectionMode.value, 'manual')
  a.keyword.value = '  吾悦  '
  await a.loadStations()
  assert.deepEqual(a.calls.searches, ['', '吾悦'])
  assert.equal(a.stations.value.length, 3)
  assert.equal(a.stations.value.some(item => item.id === nearStation.id), false)
  assert.equal(a.stations.value[2].id, station.id)
  assert.equal(a.appliedKeyword.value, '吾悦')
  assert.equal(a.mapSelectionMode.value, 'auto')
  assert.equal(a.activeMapStation.value.id, station.id)
  assert.equal(a.recommendationTitle.value, '搜索结果中距您最近')
  a.keyword.value = ''
  await flush()
  assert.deepEqual(a.calls.searches, ['', '吾悦', ''])
  assert.equal(a.stations.value.length, 10)
  assert.deepEqual(a.stations.value.map(item => item.id), allStations.map(item => item.id))
  assert.equal(a.appliedKeyword.value, '')
  assert.equal(a.activeMapStation.value.id, nearStation.id)
  assert.equal(a.recommendationTitle.value, '距您最近')
})

test('clearing a pending first search supersedes it and ignores its later filtered response', async t => {
  for (const location of [position, null]) {
    const queries = []
    let finishSearch
    const a = app(t, { stations: query => {
      queries.push(query)
      return query ? new Promise(resolve => { finishSearch = resolve }) : Promise.resolve(allStations)
    } })
    await a.loadStations()
    a.receiveLocation(location)
    a.keyword.value = '吾悦'
    const search = a.loadStations()
    await flush()
    assert.equal(a.appliedKeyword.value, '')
    a.keyword.value = ''
    await flush()
    assert.deepEqual(queries, ['', '吾悦', ''])
    assert.equal(a.stations.value.length, 10)
    finishSearch(filteredStations)
    await search
    await flush()
    assert.equal(a.keyword.value, '')
    assert.equal(a.appliedKeyword.value, '')
    assert.deepEqual(a.stations.value.map(item => item.id), allStations.map(item => item.id))
    assert.equal(a.activeMapStation.value.id, location ? nearStation.id : station.id)
    assert.equal(a.mapSelectionMode.value, 'auto')
    assert.equal(a.recommendationTitle.value, location ? '距您最近' : '推荐站点')
    if (!location) assert.equal(a.activeDistance.value, '')
  }
})

test('logout during a pending search does not fetch a blank query or restore late results', async t => {
  const queries = []
  let finishSearch
  const a = app(t, { stations: query => {
    queries.push(query)
    return query ? new Promise(resolve => { finishSearch = resolve }) : Promise.resolve(allStations)
  } })
  await a.loadStations()
  a.receiveLocation(position)
  a.keyword.value = '吾悦'
  const search = a.loadStations()
  await flush()
  a.logout()
  await flush()
  assert.deepEqual(queries, ['', '吾悦'])
  finishSearch(filteredStations)
  await search
  await flush()
  assert.deepEqual(queries, ['', '吾悦'])
  assert.equal(a.keyword.value, '')
  assert.equal(a.appliedKeyword.value, '')
  assert.deepEqual(a.stations.value, [])
  assert.equal(a.activeMapStation.value, null)
  assert.equal(a.userLocation.value, null)
})

test('search without location uses the first valid result and displays no invented distance', async t => {
  const a = app(t, { stations: async () => [{ id: 1 }, farStation, nearStation] })
  a.keyword.value = '吾悦'
  await a.loadStations()
  assert.equal(a.activeMapStation.value.id, farStation.id)
  assert.equal(a.recommendationTitle.value, '推荐站点')
  assert.equal(a.activeDistance.value, '')
})

test('manual selection opens details and survives a later automatic location result', async t => {
  const a = app(t)
  await a.loadStations()
  await a.openStation(farStation)
  assert.equal(a.activeMapStation.value.id, farStation.id)
  assert.equal(a.selected.value.id, farStation.id)
  assert.equal(a.mapSelectionMode.value, 'manual')
  assert.equal(a.recommendationTitle.value, '当前查看')
  a.receiveLocation(position)
  assert.equal(a.activeMapStation.value.id, farStation.id)
  assert.equal(a.recommendationTitle.value, '当前查看')
  const mapCalls = []
  a.stationMap.value = { focus: item => mapCalls.push(item.id) }
  await a.mapAction('focus', nearStation)
  assert.deepEqual(mapCalls, [nearStation.id])
  assert.equal(a.activeMapStation.value.id, nearStation.id)
  assert.equal(a.selected.value, null)
})

test('explicit location retry returns a manual selection to automatic nearest recommendation', async t => {
  const a = app(t)
  await a.loadStations()
  await a.openStation(farStation)
  let retries = 0
  a.stationMap.value = { retryLocation: () => { retries++; a.receiveLocation(position) } }
  a.retryLocation()
  assert.equal(retries, 1)
  assert.equal(a.mapSelectionMode.value, 'auto')
  assert.equal(a.activeMapStation.value.id, nearStation.id)
  assert.equal(a.recommendationTitle.value, '距您最近')
})

test('a slower earlier search cannot replace newer successful search results', async t => {
  const pending = []
  const a = app(t, { stations: keyword => new Promise(resolve => pending.push({ keyword, resolve })) })
  a.receiveLocation(position)
  a.keyword.value = '较早'
  const earlier = a.loadStations()
  a.keyword.value = '较新'
  const newer = a.loadStations()
  pending[1].resolve([nearStation])
  await newer
  pending[0].resolve([farStation])
  await earlier
  assert.equal(a.appliedKeyword.value, '较新')
  assert.deepEqual(a.stations.value.map(item => item.id), [nearStation.id])
  assert.equal(a.activeMapStation.value.id, nearStation.id)
})

test('reservation dates follow refreshed 7 → 3 → 7 rules and reset an out-of-range choice', async t => {
  const maxDays = [7, 3, 7]
  const a = app(t, { reservationRules: async () => ({ maxDays: maxDays.shift() }) })
  const date = offset => { const value = new Date(); value.setHours(0, 0, 0, 0); value.setDate(value.getDate() + offset); return value }
  assert.equal(a.disabledReservationDate(date(1)), true)
  await a.loadReservationRules()
  assert.equal(a.disabledReservationDate(date(-1)), true)
  assert.equal(a.disabledReservationDate(date(7)), false)
  assert.equal(a.disabledReservationDate(date(8)), true)
  a.reservation.reservationDate = a.formatLocalDate(date(6))
  a.reservation.timeSlot = '10:00-12:00'
  await a.loadReservationRules()
  assert.equal(a.reservationMaxDays.value, 3)
  assert.equal(a.disabledReservationDate(date(3)), false)
  assert.equal(a.disabledReservationDate(date(4)), true)
  assert.equal(a.reservation.reservationDate, a.formatLocalDate(date(1)))
  assert.equal(a.reservation.timeSlot, '')
  await a.loadReservationRules()
  assert.equal(a.reservationMaxDays.value, 7)
  assert.equal(a.disabledReservationDate(date(7)), false)
})

test('reservation payload is preserved and duplicate clicks or success-dialog resubmission are suppressed', async t => {
  let complete
  const payloads = []
  const a = app(t, { reserve: payload => { payloads.push(payload); return new Promise(resolve => { complete = resolve }) } })
  await a.openStation(station)
  a.reservation.timeSlot = '10:00-12:00'
  const first = a.createReservation()
  await a.createReservation()
  assert.equal(payloads.length, 1)
  assert.deepEqual(payloads[0], { stationId: station.id, reservationDate: a.reservation.reservationDate, timeSlot: '10:00-12:00' })
  complete({ ...payloads[0], reservationId: 11, orderNo: 'test-order', estimatedAmount: 20 })
  await first
  assert.equal(a.resultVisible.value, true)
  assert.equal(a.result.value.reservationId, 11)
  assert.equal(a.resultStation.value.id, station.id)
  await a.createReservation()
  assert.equal(payloads.length, 1)
})

// Compile the actual slot template so disabled state and labels are tested together.
const { descriptor: appDescriptor } = parse(readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8'))
function findSlotTemplate(node) {
  if (node.tag === 'div' && node.props?.some(prop => prop.name === 'class' && prop.value?.content === 'slot-list')) return node.loc.source
  for (const child of node.children || []) {
    const source = findSlotTemplate(child)
    if (source) return source
  }
}
const slotTemplate = findSlotTemplate(parseTemplate(appDescriptor.template.content))
assert.ok(slotTemplate, 'the reservation slot template must exist')
const renderSlots = new Function('Vue', compile(slotTemplate, { mode: 'function' }).code)(Vue)
function renderedSlot(item) {
  const state = { availability: [item], reservation: { timeSlot: '' } }
  function findButton(node) {
    if (node?.type === 'button') return node
    for (const child of (Array.isArray(node) ? node : node?.children) || []) {
      const button = findButton(child)
      if (button) return button
    }
  }
  function text(node) {
    if (typeof node === 'string') return node
    if (Array.isArray(node)) return node.map(text).join('')
    return node?.children ? text(node.children) : ''
  }
  const button = findButton(renderSlots(state, []))
  assert.ok(button, 'a slot must render a native button')
  return { state, button, label: text(button) }
}

test('server-expired slots are disabled and say 已过时段 regardless of their remaining capacity', () => {
  for (const remaining of [2, 0]) {
    const { button, label } = renderedSlot({ timeSlot: '09:00-10:00', capacity: 2, remaining, expired: true })
    assert.equal(button.props.disabled, true)
    assert.match(label, /已过时段/)
    assert.doesNotMatch(label, /剩余|已满/)
  }
})

test('full slots that have not expired are disabled and say 已满', () => {
  const { button, label } = renderedSlot({ timeSlot: '14:00-15:00', capacity: 2, remaining: 0, expired: false })
  assert.equal(button.props.disabled, true)
  assert.match(label, /已满/)
  assert.doesNotMatch(label, /已过时段/)
})

test('non-expired slots with capacity remain selectable and show their real remaining capacity', () => {
  const { state, button, label } = renderedSlot({ timeSlot: '19:00-20:00', capacity: 2, remaining: 2, expired: false })
  assert.equal(button.props.disabled, false)
  assert.match(label, /剩余 2 \/ 2/)
  button.props.onClick()
  assert.equal(state.reservation.timeSlot, '19:00-20:00')
})

test('a refreshed server-expired flag clears the previously selected slot', async t => {
  let expired = false
  const a = app(t, { availability: async () => [{ timeSlot: '09:00-10:00', capacity: 2, remaining: 2, expired }] })
  await a.openStation(station)
  a.reservation.timeSlot = '09:00-10:00'
  expired = true
  await a.loadAvailability()
  assert.equal(a.reservation.timeSlot, '')
  assert.equal(a.availability.value[0].remaining, 2)
  assert.equal(a.availability.value[0].expired, true)
})

test('an already expired selection never sends a create request from the frontend', async t => {
  const a = app(t, { availability: async () => [{ timeSlot: '09:00-10:00', capacity: 2, remaining: 2, expired: true }] })
  await a.openStation(station)
  a.reservation.timeSlot = '09:00-10:00'
  await a.createReservation()
  assert.equal(a.calls.reservations.length, 0)
  assert.equal(a.reservation.timeSlot, '')
  assert.deepEqual(a.calls.messages.at(-1), ['warning', '该预约时段已开始或已结束，请选择其他时段'])
})
