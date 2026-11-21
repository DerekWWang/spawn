// Commander eval host. Keeps the actors connected as ordinary players. Each actor has a small deterministic mind
// of its own (actor.js): it follows the commander's orders and policy, works out how to make what it is told to
// make (recipes.js), and looks after itself around those orders. One commander drives them over a local HTTP port.
// usage: node host.js <server.properties> <Name1,Name2,...> [port] [logDir]
const path = require('path')
const fs = require('fs')
const http = require('http')
const lib = require('../lib')
const B = require('./body')
const { Book, rank, norm } = require('./recipes')
const { createCrew } = require('./actor')
const { Vec3, round, pos, itemsOf } = B
const settings = lib.rcon.readProperties(fs.readFileSync(process.argv[2], 'utf8'))
const NAMES = process.argv[3].split(',')
const PORT = Number(process.argv[4] || 47100)
const LOGS = process.argv[5] || path.join(__dirname, 'logs')
fs.mkdirSync(LOGS, { recursive: true })
const LOG = path.join(LOGS, 'host-log.jsonl')
const raw = console.log
console.log = (...a) => { if (!/partial packet|Chunk size is/.test(String(a[0]))) raw(...a) }
const sleep = B.sleep
const record = entry => fs.appendFileSync(LOG, JSON.stringify({ t: new Date().toISOString(), ...entry }) + '\n')

// ---------------------------------------------------------------- the shared record of what was said
const heard = [] // { n, t, text } chat and broadcast lines everyone heard
let heardSeq = 0
function hear (text) {
  // Every actor hears the same chat line; keep one copy.
  const recent = heard.slice(-12)
  if (recent.some(h => h.text === text && Date.now() - h.at < 3000)) return
  heard.push({ n: ++heardSeq, at: Date.now(), text })
  if (heard.length > 300) heard.splice(0, heard.length - 300)
}
const alerts = [] // { n, actor, text }
let alertSeq = 0
const waiters = new Set()
function alert (actor, text) {
  alerts.push({ n: ++alertSeq, at: Date.now(), actor, text })
  if (alerts.length > 300) alerts.splice(0, alerts.length - 300)
  record({ bot: actor, alert: text })
  for (const wake of waiters) wake()
}

// ---------------------------------------------------------------- joining
const actors = {}
function join (name) {
  return new Promise((resolve, reject) => {
    const bot = lib.createBot({ host: '127.0.0.1', port: settings.port, username: name, hideErrors: true, commandTimeout: 10000 })
    const timer = setTimeout(() => reject(new Error(name + ' did not spawn')), 60000)
    const actor = { name, bot, order: null, queue: [], last: null, body: null, dead: false, gone: null, system: [], inWaterSince: 0, idleSince: Date.now(), lastHealth: null, pendingCare: null }
    bot.on('error', error => record({ bot: name, event: 'error', reason: String(error && error.message) }))
    bot.on('kicked', reason => { actor.gone = 'kicked ' + JSON.stringify(reason).slice(0, 200); record({ bot: name, event: 'kicked', reason }); alert(name, 'disconnected: ' + actor.gone) })
    bot.on('end', reason => { actor.gone = actor.gone || String(reason); record({ bot: name, event: 'end', reason: String(reason) }) })
    bot.on('death', () => {
      if (actor.dead) return
      actor.dead = true; record({ bot: name, event: 'death' })
      if (actor.order) actor.order.abort = 'died'
      actor.queue.length = 0; actor.pendingCare = null
      alert(name, 'DIED. ' + name + ' is gone for good and can take no further part.')
    })
    bot.on('health', () => {
      if (actor.dead) return
      if (actor.lastHealth !== null && bot.health < actor.lastHealth - 0.5) alert(name, `hurt: health ${round(actor.lastHealth)} -> ${round(bot.health)} of 20`)
      actor.lastHealth = bot.health
    })
    bot.on('message', (message, position) => {
      const text = lib.survival.fullText(message)
      if (!text.trim() || /^\s*\{/.test(text)) return
      const line = text.length > 20000 ? text.slice(0, 20000) : text
      if (position === 'chat') return hear(line)
      if (position === 'game_info') { actor.system.push({ at: Date.now(), text: '[bar] ' + line }); return }
      // System lines: command replies are taken from here; announcements (deaths, joins) are heard by all.
      actor.system.push({ at: Date.now(), text: line })
      if (actor.system.length > 200) actor.system.splice(0, actor.system.length - 200)
      if (lib.survival.isAnnouncement && lib.survival.isAnnouncement(message)) hear(line)
      else if (/ (died|drowned|starved|was slain|was killed|froze|burned|fell|withered|suffocated)/.test(line) && !/^\[/.test(line)) hear(line)
    })
    if (process.env.TRACE_MOVES) trace(name, bot)
    bot.once('spawn', () => { clearTimeout(timer); actors[name] = actor; resolve(actor) })
  })
}

/** For finding faults (TRACE_MOVES=1): a flight recorder. It keeps the last movement packets sent and the last packets
 * received that could move this player, asks the server each second where it has the player, and writes the lot to the
 * log the moment the two part by more than three blocks. */
let stageHand = null
function trace (name, bot) {
  const at = () => bot.entity ? pos(bot.entity.position) : null
  const sent = []; const got = []; let apart = 0; let reported = false
  const keep = (list, entry, n) => { list.push({ ms: Date.now() % 1000000, ...entry }); if (list.length > n) list.shift() }
  const write = bot._client.write.bind(bot._client)
  bot._client.write = (packet, data) => {
    if (/^(position|position_look|look|flying|teleport_confirm|steer_vehicle|vehicle_move|player_input|entity_action|player_loaded)$/.test(packet)) keep(sent, { p: packet, x: data.x === undefined ? undefined : round(data.x), y: data.y === undefined ? undefined : round(data.y), z: data.z === undefined ? undefined : round(data.z), g: data.onGround, id: data.teleportId, a: data.actionId }, 120)
    return write(packet, data)
  }
  const WATCH = /^(position|set_passengers|attach_entity|respawn|login|update_health|game_state_change|player_rotation|start_configuration|death_combat_event|camera|entity_velocity|entity_teleport|sync_entity_position|rel_entity_move|entity_move_look|explosion|player_abilities|bed|animation)$/
  bot._client.on('packet', (data, meta) => {
    if (!WATCH.test(meta.name) || !bot.entity) return
    const mine = data.entityId === bot.entity.id || data.vehicleId === bot.entity.id || (Array.isArray(data.passengers) && data.passengers.includes(bot.entity.id))
    if (/^(entity_|sync_entity|rel_entity|animation)/.test(meta.name) && !mine) return
    if (meta.name === 'set_passengers' && !mine && !(bot.vehicle && data.entityId === bot.vehicle.id)) return
    keep(got, { p: meta.name, id: data.entityId ?? data.teleportId, x: typeof data.x === 'number' ? round(data.x) : undefined, y: typeof data.y === 'number' ? round(data.y) : undefined, z: typeof data.z === 'number' ? round(data.z) : undefined, h: data.health, riders: data.passengers, was: at() }, 80)
  })
  for (const event of ['mount', 'dismount', 'sleep', 'wake', 'respawn', 'death', 'spawn']) bot.on(event, () => keep(got, { event, was: at() }, 80))
  const timer = setInterval(async () => {
    if (!stageHand || !actors[name] || actors[name].gone || !bot.entity) return
    const mine = bot.entity.position.clone(); let reply
    try { reply = await stageHand.send('data get entity ' + name + ' Pos') } catch { return }
    const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(reply); if (!m) return
    const now = bot.entity.position
    const gap = Math.min(Math.hypot(mine.x - m[1], mine.y - m[2], mine.z - m[3]), Math.hypot(now.x - m[1], now.y - m[2], now.z - m[3]))
    apart = gap > 3 ? apart + 1 : 0
    if (apart === 0 && reported) { reported = false; record({ bot: name, trace: 'together again', at: at() }) }
    if (apart === 2 && !reported) {
      reported = true
      const a = actors[name]; const feet = bot.blockAt(now); const under = bot.blockAt(now.offset(0, -0.2, 0))
      record({ bot: name, trace: 'APART', gap: round(gap), client: at(), server: [round(+m[1]), round(+m[2]), round(+m[3])], alive: bot.isAlive, health: bot.health, onGround: bot.entity.onGround, vehicle: bot.vehicle ? bot.vehicle.id : null, physics: bot.physicsEnabled, controls: ['forward', 'back', 'left', 'right', 'jump', 'sprint', 'sneak'].filter(c => bot.getControlState(c)), in: feet && feet.name, on: under && under.name, doing: a.order ? { task: a.order.task, progress: a.order.progress } : null, sent, got })
      alert(name, 'TRACE: client and server positions parted by ' + round(gap) + ' blocks')
    }
  }, 1000)
  bot.on('end', () => clearInterval(timer))
}

// ---------------------------------------------------------------- the actors' minds and their shared book
let book = null
let crew = null

// ---------------------------------------------------------------- what the commander sees
function summary (actor) {
  const bot = actor.bot
  if (actor.dead) return { name: actor.name, dead: true, last: actor.last || undefined }
  if (actor.gone) return { name: actor.name, disconnected: actor.gone }
  const w = B.water(bot)
  return {
    name: actor.name,
    pos: pos(bot.entity.position),
    health: round(bot.health),
    body: B.bodySummary(actor.body),
    water: w.inWater ? (w.headUnder ? `HEAD UNDER WATER, air ${w.air === null ? '?' : w.air + '%'}` : 'standing in water') : 'dry',
    held: bot.heldItem ? bot.heldItem.name : null,
    items: itemsOf(bot),
    ...crew.view(actor),
    last: actor.last || undefined
  }
}
const seen = { heard: 0, alerts: 0 }
function news () {
  const h = heard.filter(x => x.n > seen.heard).map(x => x.text)
  const a = alerts.filter(x => x.n > seen.alerts).map(x => `${x.actor}: ${x.text}`)
  seen.heard = heardSeq; seen.alerts = alertSeq
  return { heard: h.length ? h : undefined, alerts: a.length ? a : undefined }
}
function world () {
  const any = Object.values(actors).find(a => !a.dead && !a.gone) || Object.values(actors)[0]
  const t = any.bot.time
  return { day: t.day, timeOfDay: t.timeOfDay, note: 'timeOfDay 0 = sunrise, 6000 = noon, 12000 = sunset, 18000 = midnight; a day is 24000 ticks, 20 minutes of real time' }
}
/** What one person can see of the world for the planner: their own pockets and the blocks around them. */
function viewOf (a) { return crew.worldView(a) }
function describe (w) {
  const r = w.route
  const needs = r.slots ? r.slots.map(s => s.length > 4 ? s.slice(0, 4).join('/') + '/...' : s.join('/'))
    : r.inputs ? r.inputs.map(i => i.count + ' ' + i.alts.slice(0, 3).join('/') + (i.alts.length > 3 ? '/...' : ''))
      : r.input ? [r.inputCount + ' ' + r.input, r.second ? r.secondCount + ' ' + r.second : null].filter(Boolean)
        : r.block ? ['a ' + r.block + ' block'] : undefined
  const at = r.station || (r.table ? 'crafting_table' : undefined) || (r.stations && r.stations.length ? r.stations.join(',') : undefined)
  return { kind: r.kind, id: r.id, name: r.name, makes: r.out, needs, at, tools: r.tool ? 'a pickaxe' : r.tools && r.tools.length ? r.tools.map(t => t.slice(0, 2).join('/')) : undefined, open: w.cost !== Infinity, why: w.why }
}

// ---------------------------------------------------------------- the commander's interface
const LOOKS = {
  map: (a, { radius = 8 }) => B.surface(a.bot, Math.max(4, Math.min(16, +radius))),
  find: (a, { name, radius = 32, count = 8 }) => B.findBlocks(a.bot, name, Math.min(+radius, 64), Math.min(+count, 32)),
  nearby: (a, { radius = 16 }) => B.nearby(a.bot, Math.min(+radius, 48)),
  // The server lists every block; group them by kind, nearest first, so the answer stays short.
  survey: async (a, { radius }) => {
    const report = await a.bot.survival.resources(radius ? Math.max(1, Math.min(16, Math.round(+radius))) : undefined)
    const here = a.bot.entity.position; const groups = {}
    for (const r of report.resources || []) {
      const key = [r.identity, r.kind, r.method, r.harvestReady === false ? 'not ready' : ''].join('|')
      const d = Math.hypot(r.x + 0.5 - here.x, r.y - here.y, r.z + 0.5 - here.z)
      const g = groups[key] || (groups[key] = { what: r.identity, kind: r.kind, method: r.method, ready: r.harvestReady, host: r.host !== r.identity ? r.host : undefined, count: 0, nearest: null, d: Infinity })
      g.count++; if (d < g.d) { g.d = d; g.nearest = [r.x, r.y, r.z] }
    }
    const rest = { ...report }; delete rest.resources
    return { ...rest, resources: Object.values(groups).sort((x, y) => x.d - y.d).map(g => ({ ...g, d: undefined, distance: round(g.d) })) }
  },
  body: async a => B.readBody(a),
  inventory: a => ({ held: a.bot.heldItem ? a.bot.heldItem.name : null, hotbarSlot: a.bot.quickBarSlot, slots: a.bot.inventory.slots.map((s, i) => s ? [i, s.name, s.count] : null).filter(Boolean) }),
  climate: async a => a.bot.survival.climate(),
  column: (a, { x, z, top, bottom }) => { const out = []; for (let y = +top; y >= Math.max(+bottom, +top - 40); y--) { const b = a.bot.blockAt(new Vec3(+x, y, +z)); out.push(y + ':' + (b ? b.name : '?')) } return out },
  command: async (a, { line }) => B.command(a, line, 900),
  // What this person's society has made that counts: works of art, monuments, and what each kind is worth.
  score: async a => B.commandJson(a, '/survival score', v => 'culture' in v),
  // Every way this person could come by an item from where they stand, open ones first, and why the rest are shut.
  ways: (a, { name, count = 1 }) => {
    const item = norm(name)
    const ways = rank(book, item, +count, viewOf(a)).map(describe)
    return { item, have: itemsOf(a.bot)[item] || 0, ways: ways.length ? ways : 'no known way to make or find it' }
  }
}
const handlers = {
  async team () {
    return { world: world(), actors: NAMES.map(n => actors[n] ? summary(actors[n]) : { name: n, missing: true }), shared: { stations: crew.team.stations, avoid: Object.fromEntries(crew.team.avoid), water: crew.team.water || undefined, season: crew.team.season ? ['spring', 'summer', 'autumn', 'winter'][crew.team.season.value] : undefined, foodSeen: [...new Set([...crew.team.food.values()].map(f => f.block))], sitesSeen: Object.fromEntries([...crew.team.finds].map(([k, m]) => [k.replace(/^spawn_9b5fbb9:/, ''), m.size])) }, ...news() }
  },
  async order ({ actor: name, task, args, then, now }) {
    const a = actors[name]; if (!a) throw new Error('no person ' + name)
    if (a.dead) return { ok: false, why: name + ' is dead' }
    return crew.order(a, task, args, then, !!now)
  },
  async cancel ({ actor: name }) {
    const a = actors[name]; if (!a) throw new Error('no person ' + name)
    a.queue.length = 0
    if (a.order) { a.order.abort = 'cancelled by the commander'; a.order.resume = false }
    return { ok: true }
  },
  async policy ({ actor: name, set }) {
    const targets = !name || name === 'all' ? NAMES.map(n => actors[n]).filter(Boolean) : [actors[name]]
    if (!targets[0]) throw new Error('no person ' + name)
    const out = {}
    for (const a of targets) out[a.name] = set ? crew.setPolicy(a, set) : a.policy
    return out
  },
  async look ({ actor: name, what, ...args }) {
    const a = actors[name]; if (!a) throw new Error('no person ' + name)
    if (a.dead) throw new Error(name + ' is dead')
    if (!LOOKS[what]) throw new Error('look takes what = ' + Object.keys(LOOKS).join(', '))
    return LOOKS[what](a, args)
  },
  // Let time pass until any order ends, something happens to someone, or the time is up.
  async wait ({ seconds = 30 }) {
    const ms = Math.max(1, Math.min(+seconds, 90)) * 1000
    const startAlerts = alertSeq; const doneBefore = NAMES.map(n => actors[n] && actors[n].last && actors[n].last.n)
    await new Promise(resolve => {
      const timer = setTimeout(done, ms)
      function done () { clearTimeout(timer); waiters.delete(check); resolve() }
      function check () { if (alertSeq > startAlerts || NAMES.some((n, i) => actors[n] && actors[n].last && actors[n].last.n !== doneBefore[i])) setTimeout(done, 300) }
      waiters.add(check)
    })
    return handlers.team()
  }
}

;(async () => {
  if (process.env.TRACE_MOVES) stageHand = await lib.rcon.connect({ port: settings.rconPort, password: settings.rconPassword })
  for (const name of NAMES) { const a = await join(name); raw('joined', name, a.bot.entity.position.floored().toString(), a.bot.game.gameMode); await sleep(1500) }
  const first = Object.values(actors)[0]
  book = new Book(first.bot.registry)
  // The book is kept between runs, keyed by the server's recipe count and the mod jar it was read from.
  const ask = (line, shape) => B.commandJson(first, line, shape)
  const head = await ask('/survival planning 0', v => Array.isArray(v.craftingRecipes) && v.offset === 0)
  const jar = path.join(__dirname, '..', '..', 'build', 'libs', 'spawn_9b5fbb9-1.0.0.jar')
  const key = head.totalRecipes + ':' + (fs.existsSync(jar) ? fs.statSync(jar).mtimeMs : 0)
  const cache = path.join(__dirname, '.cache', 'book.json')
  if (!book.restore(cache, key)) { await book.loadServer(ask); book.save(cache, key); raw('book read from the server') } else raw('book restored from cache')
  book.loadTechtree()
  raw('book', JSON.stringify(book.summary()))
  // The people are one people: one scoreboard team, so they share one society, its knowledge and its culture score.
  // A stage hand's act at the start, like placing them on the beach; nothing the commander can do.
  try {
    const hand = stageHand || await lib.rcon.connect({ port: settings.rconPort, password: settings.rconPassword })
    await hand.send('team add people').catch(() => {})
    for (const name of NAMES) await hand.send('team join people ' + name)
    if (!stageHand) hand.close()
  } catch (error) { raw('could not put the actors on one team:', error.message) }
  // What the team has built is kept beside the log, so that a host started again on the same run still knows of it.
  crew = createCrew({ actors, alert, record, book, waiters, memory: path.join(LOGS, 'team-memory.json') })
  for (const a of Object.values(actors)) { crew.enroll(a); try { await B.readBody(a) } catch {} }
  const server = http.createServer((req, res) => {
    let body = ''
    req.on('data', chunk => { body += chunk })
    req.on('end', async () => {
      let reply; const started = Date.now(); let request = {}
      try {
        request = JSON.parse(body || '{}')
        if (!handlers[request.op]) throw new Error('no op ' + request.op)
        reply = { ok: true, result: await handlers[request.op](request.args || {}) }
      } catch (error) { reply = { ok: false, error: String((error && (error.message || error)) || 'failed') } }
      record({ op: request.op, args: request.args, ms: Date.now() - started, reply: request.op === 'look' && request.args && request.args.what === 'map' ? '(map)' : reply })
      res.end(JSON.stringify(reply))
    })
  })
  server.listen(PORT, '127.0.0.1', () => raw('host listening on', PORT))
})().catch(error => { raw('host failed:', error.stack); process.exit(1) })
