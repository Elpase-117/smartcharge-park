import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { stationCoordinates, amapUri } from '../src/mapUtils.js'

const station = { id: 4, name: '小鹏（吾悦 & 广场）', amapName: '小鹏标准名称', longitude: '106.432610', latitude: '29.823147', operatorBrand: '小鹏', chargingMode: '超充/快充', availableChargers: 0, totalChargers: 4 }
test('only valid saved China coordinates can create markers', () => {
  assert.deepEqual(stationCoordinates(station), [106.43261, 29.823147])
  for (const s of [{}, { longitude: null, latitude: 29 }, { longitude: '', latitude: '' }, { longitude: 0, latitude: 0 }, { longitude: NaN, latitude: 29 }, { longitude: -100, latitude: 30 }]) assert.equal(stationCoordinates(s), null)
})
test('navigation URI encodes real origin and destination, fallback has no invented origin', () => {
  const url = new URL(amapUri(station, [106.4, 29.8]))
  assert.equal(url.pathname, '/navigation'); assert.equal(url.searchParams.get('mode'), 'car')
  assert.equal(url.searchParams.get('to'), '106.43261,29.823147,小鹏标准名称')
  assert.equal(url.searchParams.get('from'), '106.4,29.8,当前位置')
  assert.equal(url.searchParams.get('coordinate'), 'gaode'); assert.equal(url.searchParams.get('callnative'), '1')
  const fallback = new URL(amapUri(station)); assert.equal(fallback.pathname, '/marker'); assert.equal(fallback.searchParams.has('from'), false)
  assert.equal(amapUri({}), null)
})

// Execute the component's real setup functions with SDK/DOM doubles; no browser or provider quota is used.
async function component(options = {}) {
  const events = [], hooks = [], watches = [], made = [], routes = [], state = {}
  const ref = value => ({ value }), computed = fn => ({ get value() { return fn() } })
  const props = { stations: [station, { id: 9 }], selectedId: undefined }
  let exposed, sdkCalls = 0
  class Marker {
    constructor(config) { this.config = config; this.events = {}; made.push(this) }
    on(name, fn) { this.events[name] = fn }
    setLabel(label) { this.label = label }
    setzIndex(value) { this.z = value }
  }
  class MapDouble {
    constructor() { state.map = this }
    on() {} add() {} remove(items) { this.removed = items } setFitView(items) { this.fitted = items }
    setZoomAndCenter(zoom, position) { this.zoom = zoom; this.position = position }
    destroy() { this.destroyed = true }
  }
  class InfoWindow { close() {} setContent(content) { this.content = content } open() {} }
  class Driving {
    constructor(config) { this.config = config; routes.push(this) }
    search(from, to, callback) { this.from = from; this.to = to; callback('complete', { routes: [{ distance: 1200, time: 300 }] }) }
    clear() { this.cleared = true }
  }
  class Geolocation {
    getCurrentPosition(callback) { state.locationCalls = (state.locationCalls || 0) + 1; options.denied ? callback('error', {}) : callback('complete', { position: { lng: 106.4, lat: 29.8 }, location_type: 'html5' }) }
  }
  const sdk = { Map: MapDouble, Marker, InfoWindow, Driving, Geolocation, Pixel: class {} }
  const loader = { load: async () => { sdkCalls++; return sdk }, reset() {} }
  const document = { createElement: () => ({ appendChild() {}, addEventListener() {} }) }
  const window = { open: () => ({ opener: null, location: { replace(url) { state.opened = url } }, close() { state.blankClosed = true } }) }
  const source = readFileSync(new URL('../src/components/StationMap.vue', import.meta.url), 'utf8')
    .match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '').replaceAll('import.meta.env', 'environment')
  new Function('computed', 'nextTick', 'onBeforeUnmount', 'onMounted', 'ref', 'watch', 'defineProps', 'defineEmits', 'defineExpose', 'AMapLoader', 'amapUri', 'stationCoordinates', 'document', 'window', 'fetch', 'environment', 'setTimeout', source)(
    computed, async () => {}, fn => hooks.push(fn), fn => { state.mount = fn }, ref, (getter, fn) => watches.push(fn), () => props,
    () => (...args) => events.push(args), value => { exposed = value }, loader, amapUri, stationCoordinates, document, window,
    async () => ({ ok: !options.proxyFailed, json: async () => ({ configured: !options.proxyUnconfigured }) }),
    { VITE_AMAP_JS_KEY: options.noKey ? '' : 'sdk-test-placeholder' }, () => 0)
  await state.mount()
  return { events, made, routes, state, props, watches, hooks, exposed, window, sdkCalls }
}
test('missing JS key and failed proxy do not initialize SDK or break setup', async () => {
  assert.equal((await component({ noKey: true })).sdkCalls, 0)
  assert.equal((await component({ proxyFailed: true })).sdkCalls, 0)
  assert.equal((await component({ proxyUnconfigured: true })).sdkCalls, 0)
})
test('valid markers, marker click, card focus and search synchronize without geolocation', async () => {
  const c = await component(); assert.equal(c.made.length, 1); assert.equal(c.state.locationCalls, undefined)
  c.made[0].events.click(); assert.deepEqual(c.events[0], ['select', station])
  c.exposed.focus(station); assert.deepEqual(c.state.map.position, [106.43261, 29.823147]); assert.equal(c.made[0].z, 200)
  c.props.stations = []; c.watches[0](); assert.equal(c.state.map.removed.length, 1)
  c.hooks[0](); assert.equal(c.state.map.destroyed, true)
})
test('user-triggered Driving draws a route and clear on search removes it', async () => {
  const c = await component(); await c.exposed.planRoute(station)
  assert.equal(c.state.locationCalls, 1); assert.equal(c.routes.length, 1)
  assert.equal(c.routes[0].config.map, c.state.map); assert.deepEqual(c.routes[0].to, [106.43261, 29.823147])
  c.watches[0](); assert.equal(c.routes[0].cleared, true)
})
test('denied location is handled without inventing origin or drawing a route', async () => {
  const c = await component({ denied: true }); await c.exposed.planRoute(station); assert.equal(c.routes.length, 0)
  await c.exposed.navigate(station); assert.equal(c.state.opened, undefined); assert.equal(c.state.blankClosed, true)
})
test('navigation opens a correctly encoded URI only after active geolocation', async () => {
  const c = await component(); await c.exposed.navigate(station)
  assert.equal(new URL(c.state.opened).pathname, '/navigation'); assert.equal(c.state.locationCalls, 1)
  assert.equal(c.window._AMapSecurityConfig.securityJsCode, undefined)
  assert.equal(c.window._AMapSecurityConfig.serviceHost, 'http://127.0.0.1:8084/_AMapService')
})
