// An actor's body: what it perceives, how it walks on dry land, how it sends commands and works with its hands.
// Every function takes the actor ({ bot, order, system, ... }); a running order's `abort` stops long routines.
const { Vec3 } = require('vec3')
const lib = require('../lib')
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
const MOD = 'spawn_9b5fbb9:'
const full = name => String(name).startsWith('mod:') ? MOD + String(name).slice(4) : String(name)
const round = v => Math.round(v * 10) / 10
const pos = v => [round(v.x), round(v.y), round(v.z)]
// ---------------------------------------------------------------- perception
function itemsOf (bot) {
  const out = {}
  for (const item of bot.inventory.items()) out[item.name] = (out[item.name] || 0) + item.count
  return out
}
const WATER = new Set(['water', 'bubble_column'])
const isWater = b => !!b && (WATER.has(b.name) || (b.getProperties && b.getProperties().waterlogged === true))
const passable = b => !!b && b.boundingBox === 'empty' && !isWater(b) && b.name !== 'lava' && !/fire|berry_bush|cactus|powder_snow/.test(b.name)
const solid = b => !!b && b.boundingBox === 'block' && b.name !== 'lava' && !/magma|campfire/.test(b.name)
function water (bot) {
  const e = bot.entity
  const feet = bot.blockAt(e.position.floored())
  const head = bot.blockAt(e.position.offset(0, 1.62, 0).floored())
  const meta = e.metadata && typeof e.metadata[1] === 'number' ? e.metadata[1] : null
  const headUnder = isWater(head)
  return { inWater: !!e.isInWater || isWater(feet), headUnder, air: meta === null ? null : Math.max(0, Math.round(meta / 3)) } // air as % of a full breath
}
function surface (bot, r = 8) {
  const o = bot.entity.position.floored(); const legend = {}; const letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'
  const rows = []; const heights = []
  for (let dz = -r; dz <= r; dz++) {
    let row = ''; const hrow = []
    for (let dx = -r; dx <= r; dx++) {
      if (dx === 0 && dz === 0) { row += '@'; hrow.push('0'); continue }
      let found = null
      for (let dy = 6; dy >= -8 && !found; dy--) {
        let b = null
        try { b = bot.blockAt(o.offset(dx, dy, dz)) } catch { b = null }
        if (b && !/^(air|cave_air|void_air)$/.test(b.name)) found = { b, y: o.y + dy }
      }
      if (!found) { row += ' '; hrow.push('.'); continue }
      const n = found.b.name
      if (!(n in legend)) legend[n] = letters[Object.keys(legend).length] || '?'
      row += legend[n]; hrow.push(String(found.y - (o.y - 1)))
    }
    rows.push(row); heights.push(hrow.join(','))
  }
  return { origin: [o.x - r, o.z - r], note: 'row = z from north, column = x from west; @ = you; heights are the top block relative to the ground under you', rows: rows.join('\n'), legend, heights: heights.join('\n') }
}
function findBlocks (bot, name, r = 32, n = 8) {
  // An exact id, or any block whose id contains the words given ("log", "wild_", "ore").
  const want = full(name).replace(/^minecraft:/, '')
  const found = bot.findBlocks({ matching: b => b && b.name.replace(/^minecraft:/, '').includes(want), maxDistance: r, count: Math.max(n, 1) })
  return found.map(p => [p.x, p.y, p.z, bot.blockAt(p).name, round(p.distanceTo(bot.entity.position))])
}
function nearby (bot, r = 16) {
  return Object.values(bot.entities).filter(e => e !== bot.entity && e.position.distanceTo(bot.entity.position) <= r)
    .sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position))
    .map(e => {
      let what = e.username || e.name || '?'
      if (e.name === 'item') { const it = e.getDroppedItem && e.getDroppedItem(); what = 'dropped ' + (it ? it.name + ' x' + it.count : 'item') }
      return [what, ...pos(e.position), round(e.position.distanceTo(bot.entity.position))]
    }).slice(0, 30)
}

// ---------------------------------------------------------------- dry-land paths
// A cell is where feet can stand: solid below, two clear cells (no water) at feet and head.
function standable (bot, x, y, z) {
  // Leaves hold up a block in the game, but a person does not walk about on a tree's crown.
  try { const floor = bot.blockAt(new Vec3(x, y - 1, z)); return solid(floor) && !/leaves/.test(floor.name) && passable(bot.blockAt(new Vec3(x, y, z))) && passable(bot.blockAt(new Vec3(x, y + 1, z))) } catch { return false }
}
function groundAt (bot, x, z, nearY) {
  for (let y = nearY + 2; y >= nearY - 4; y--) if (standable(bot, x, y, z)) return y
  return null
}
const LEAVES = /leaves/
/** What a body can haul itself up: vines and ladders. */
const climbable = b => !!b && /^(vine|ladder|scaffolding|cave_vines|cave_vines_plant|twisting_vines|twisting_vines_plant|weeping_vines|weeping_vines_plant)$/.test(b.name)
/** What standing at a cell takes: null where a person cannot stand, else the extra effort of it and the foliage to break
 * first. Leaves bear a person's weight but make poor footing, and leaves at leg or head height are pushed through. */
function footing (bot, x, y, z) {
  let floor, feet, head
  try { floor = bot.blockAt(new Vec3(x, y - 1, z)); feet = bot.blockAt(new Vec3(x, y, z)); head = bot.blockAt(new Vec3(x, y + 1, z)) } catch { return null }
  if (!solid(floor)) return null
  let extra = LEAVES.test(floor.name) ? 3 : 0; const clear = []
  for (const b of [feet, head]) {
    if (passable(b)) continue
    if (!b || !LEAVES.test(b.name)) return null
    extra += 4; clear.push([b.position.x, b.position.y, b.position.z])
  }
  return { extra, clear }
}
/** A* over dry standing cells: steps up one block or down three, diagonals only past clear corners, up a vine or ladder
 * where there is one, and through or over low foliage when the open ground gives no way. */
function findPath (bot, goal, within = 1, limit = 9000) {
  const start = bot.entity.position.floored()
  // Each block is looked at once in a search and remembered as what it is to a walker:
  // 0 shut (water, fire, the unknown), 1 open, 2 solid, 3 leaves, 5 open and climbable.
  const kinds = new Map()
  const kind = (x, y, z) => {
    const k = ((x + 33000) * 66000 + (z + 33000)) * 512 + (y + 128)
    let v = kinds.get(k)
    if (v === undefined) {
      let b = null; try { b = bot.blockAt(new Vec3(x, y, z), false) } catch { b = null }
      v = !b ? 0 : passable(b) ? (climbable(b) ? 5 : 1) : solid(b) ? (LEAVES.test(b.name) ? 3 : 2) : 0
      kinds.set(k, v)
    }
    return v
  }
  const open1 = v => v === 1 || v === 5
  const NONE = []
  const foot = (x, y, z) => {
    const floor = kind(x, y - 1, z); if (floor !== 2 && floor !== 3) return null
    let extra = floor === 3 ? 3 : 0; let clear = NONE
    for (let yy = y; yy <= y + 1; yy++) { const v = kind(x, yy, z); if (open1(v)) continue; if (v !== 3) return null; extra += 4; clear = [...clear, [x, yy, z]] }
    return { extra, clear }
  }
  let sy = null
  for (let y = start.y + 1; y >= start.y - 3 && sy === null; y--) { const f = foot(start.x, y, start.z); if (f && !f.clear.length) sy = y }
  if (sy === null) sy = start.y
  const key = (x, z) => x + ',' + z
  const h = (x, z) => Math.hypot(x - goal.x, z - goal.z)
  const memo = new Map()
  // The best cell of a column that one step reaches: open ground before foliage, the higher before the lower.
  const stand = (x, z, nearY) => {
    const k = ((x + 33000) * 66000 + (z + 33000)) * 512 + (nearY + 128); const known = memo.get(k); if (known !== undefined) return known
    let best = null
    for (let y = nearY + 1; y >= nearY - 3; y--) { const f = foot(x, y, z); if (f && (!best || f.extra < best.extra)) { best = { y, extra: f.extra, clear: f.clear }; if (!f.extra) break } }
    memo.set(k, best); return best
  }
  // The open cells, kept as a heap with the most promising on top.
  const open = []
  const put = node => { let i = open.push(node) - 1; while (i > 0) { const up = (i - 1) >> 1; if (open[up].f <= node.f) break; open[i] = open[up]; i = up } open[i] = node }
  const take = () => {
    const top = open[0]; const last = open.pop()
    if (open.length) { let i = 0; for (;;) { let c = 2 * i + 1; if (c >= open.length) break; if (c + 1 < open.length && open[c + 1].f < open[c].f) c++; if (open[c].f >= last.f) break; open[i] = open[c]; i = c } open[i] = last }
    return top
  }
  const first = { x: start.x, y: sy, z: start.z, g: 0, f: h(start.x, start.z) }
  put(first)
  // A goal close by is not worth searching half the country for: the search is as wide as the errand is long.
  limit = Math.min(limit, Math.round(1500 + 10 * first.f * first.f))
  const came = new Map(); const best = new Map([[key(start.x, start.z), 0]])
  let explored = 0; let closest = first
  const push = (cur, node) => {
    const k = key(node.x, node.z)
    if (best.has(k) && best.get(k) <= node.g) return
    best.set(k, node.g); node.f = node.g + h(node.x, node.z); came.set(k, cur); put(node)
  }
  while (open.length && explored < limit) {
    const cur = take(); explored++
    if (best.get(key(cur.x, cur.z)) < cur.g) continue
    if (h(cur.x, cur.z) < h(closest.x, closest.z)) closest = cur
    if (h(cur.x, cur.z) <= within) return rebuild(cur, false)
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [1, -1], [-1, 1], [-1, -1]]) {
      const nx = cur.x + dx; const nz = cur.z + dz
      const to = stand(nx, nz, cur.y)
      if (to) {
        let extra = to.extra; let clear = to.clear; let ok = true
        if (to.y > cur.y) {
          // Stepping up needs the space over the head; leaves there are pushed aside like any others.
          const over = kind(cur.x, cur.y + 2, cur.z)
          if (!open1(over)) { if (over !== 3) ok = false; else { extra += 4; clear = [...clear, [cur.x, cur.y + 2, cur.z]] } }
        }
        if (ok && dx && dz) {
          // A corner is cut only where the body passes both cells beside it at the height it travels.
          const low = Math.min(cur.y, to.y); const high = Math.max(cur.y, to.y) + 1
          const free = (x, z) => { const side = stand(x, z, cur.y); if (!side || side.y > low) return false; for (let y = low; y <= high; y++) if (!open1(kind(x, y, z))) return false; return true }
          if (clear.length || !free(cur.x + dx, cur.z) || !free(cur.x, cur.z + dz)) ok = false
        }
        if (ok) push(cur, { x: nx, y: to.y, z: nz, g: cur.g + (dx && dz ? 1.414 : 1) + (to.y !== cur.y ? 0.5 : 0) + extra, clear: clear.length ? clear : undefined })
      }
      // Up a vine or a ladder: the column the feet are in must be climbable all the way, and the top clear to step off.
      if (dx && dz) continue
      if (kind(cur.x, cur.y, cur.z) !== 5 && kind(cur.x, cur.y + 1, cur.z) !== 5) continue
      for (let ny = cur.y + 2; ny <= cur.y + 10; ny++) {
        if (kind(cur.x, ny - 1, cur.z) !== 5 && kind(cur.x, ny - 2, cur.z) !== 5) break
        if (!open1(kind(cur.x, ny, cur.z)) || !open1(kind(cur.x, ny + 1, cur.z))) break
        const f = foot(nx, ny, nz)
        if (!f || f.clear.length) continue
        push(cur, { x: nx, y: ny, z: nz, g: cur.g + 1 + (ny - cur.y) * 2 + f.extra, climb: ny - cur.y }); break
      }
    }
  }
  return rebuild(closest, true)
  function rebuild (end, partial) {
    const cells = [end]; let k = key(end.x, end.z)
    while (came.has(k)) { const prev = came.get(k); cells.unshift(prev); k = key(prev.x, prev.z) }
    return { partial, cells, explored }
  }
}
/** Break the foliage in the way of a step. */
async function clearWay (actor, cell) {
  const bot = actor.bot
  for (const [x, y, z] of cell.clear || []) {
    if (actor.order && actor.order.abort) return false
    const b = bot.blockAt(new Vec3(x, y, z))
    if (!b || !LEAVES.test(b.name)) continue
    try { await bot.lookAt(b.position.offset(0.5, 0.5, 0.5), true); await withTimeout(bot.dig(b, true), 6000, 'clearing ' + b.name) } catch { try { bot.stopDigging() } catch {} return false }
  }
  return true
}
async function steer (actor, cell, ms = 3500) {
  const bot = actor.bot
  if (cell.clear && !(await clearWay(actor, cell))) return false
  if (cell.climb) ms += cell.climb * 900
  const end = Date.now() + ms
  const tx = cell.x + 0.5; const tz = cell.z + 0.5
  try {
    while (Date.now() < end) {
      if (actor.order && actor.order.abort) return false
      const here = bot.entity.position
      if (Math.hypot(here.x - tx, here.z - tz) < 0.35 && Math.abs(here.y - cell.y) < 1.2) return true
      await bot.lookAt(new Vec3(tx, here.y + 1.6, tz), true)
      bot.setControlState('forward', true)
      bot.setControlState('jump', cell.y > Math.floor(here.y + 0.01) || bot.entity.isInWater)
      await sleep(50)
    }
  } finally { bot.clearControlStates() }
  return false
}
/** Free a body caught in foliage: leaves inside the space it occupies (a crown it has stepped up under, a hedge it has
 * been pushed into) are broken. Answers whether anything was cleared. */
async function unwedge (actor) {
  const bot = actor.bot; const p = bot.entity.position; let cleared = false
  for (let x = Math.floor(p.x - 0.3); x <= Math.floor(p.x + 0.3); x++) {
    for (let z = Math.floor(p.z - 0.3); z <= Math.floor(p.z + 0.3); z++) {
      for (let y = Math.floor(p.y); y <= Math.floor(p.y + 1.79); y++) {
        const b = bot.blockAt(new Vec3(x, y, z))
        if (!b || b.boundingBox !== 'block' || !LEAVES.test(b.name)) continue
        try { await bot.lookAt(b.position.offset(0.5, 0.5, 0.5), true); await withTimeout(bot.dig(b, true), 6000, 'clearing ' + b.name); cleared = true } catch { try { bot.stopDigging() } catch {} }
      }
    }
  }
  return cleared
}
/** Out of a pit: cut a step into the wall on the side where there is firm ground to stand on, and climb it. A wall of
 * rock (which needs a pickaxe) or one that would let water in is left alone. Answers whether a step was made and climbed. */
async function climbOut (actor) {
  const bot = actor.bot; const f = bot.entity.position.floored()
  if (groundAt(bot, f.x, f.z, f.y) === null) return false
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
    const x = f.x + dx; const z = f.z + dz
    if (standable(bot, x, f.y + 1, z) || standable(bot, x, f.y, z)) continue // not walled on this side
    const floor = bot.blockAt(new Vec3(x, f.y, z)); if (!solid(floor)) continue
    const cut = [new Vec3(x, f.y + 1, z), new Vec3(x, f.y + 2, z)].map(p => bot.blockAt(p)).filter(b => b && !passable(b))
    if (cut.some(b => !solid(b) || mineable(b) || floods(bot, b.position))) continue
    let ok = true
    for (const b of cut) { try { await bot.lookAt(b.position.offset(0.5, 0.5, 0.5), true); await withTimeout(bot.dig(b, true), 8000, 'cutting a step') } catch { try { bot.stopDigging() } catch {} ok = false; break } }
    if (!ok) continue
    if (await steer(actor, { x, y: f.y + 1, z })) { await settle(bot); return true }
  }
  return false
}
/** Walk to within `within` blocks of (x, z) on dry land. */
async function walk (actor, x, z, within = 1.5) {
  const bot = actor.bot
  const left = () => Math.hypot(bot.entity.position.x - x, bot.entity.position.z - z)
  // A long way is walked a stretch at a time: a search that stops short of the goal still ends nearer it, and from
  // there the land beyond is in sight. Only a stretch that gains nothing ends the walk.
  for (let attempt = 0, stalls = 0; attempt < 16 && stalls < 4; attempt++) {
    if (left() <= within + 0.5) return { arrived: true, pos: pos(bot.entity.position) }
    const was = left()
    const route = findPath(bot, { x: Math.floor(x), z: Math.floor(z) }, within)
    let failed = false
    for (const cell of route.cells.slice(1)) {
      if (actor.order && actor.order.abort) return { arrived: false, why: actor.order.abort, pos: pos(bot.entity.position) }
      if (!(await steer(actor, cell))) { failed = true; break }
    }
    // Not moved at all: the body may be caught with its head in a crown. Break out, then try again.
    if (failed && was - left() < 0.3 && await unwedge(actor)) { await settle(bot); continue }
    // Not moved and the search found no way: the body may be down a pit of its own digging. Cut a step and go on.
    if (route.partial && was - left() < 0.3 && route.cells.length <= 2 && await climbOut(actor)) continue
    const gained = was - left()
    if (!failed && route.partial && gained < 3) return { arrived: false, why: `no dry way found; stopped ${round(left())} blocks short`, pos: pos(bot.entity.position) }
    if (gained < 1) stalls++
  }
  const d = Math.hypot(bot.entity.position.x - x, bot.entity.position.z - z)
  return { arrived: d <= within + 0.5, why: d <= within + 0.5 ? undefined : `stuck ${round(d)} blocks short`, pos: pos(bot.entity.position) }
}
/** Wait (up to two seconds) for feet to come down from a step or jump, so where they are is where they stand. */
async function settle (bot) { const end = Date.now() + 2000; while (Date.now() < end && !bot.entity.onGround) await sleep(50); await sleep(100) }
/** Nothing solid over a cell for a few blocks: a walker on the surface can reach it, not only one in a cave. */
function openAbove (bot, x, y, z) { for (let dy = 2; dy <= 8; dy++) { const b = bot.blockAt(new Vec3(x, y + dy, z)); if (solid(b) && (dy <= 5 || !/leaves/.test(b.name))) return false } return true }
/** Nothing solid at all, leaves included, over a standing cell: what falls from above cannot land on whoever stands there. */
function clearOverhead (bot, x, y, z) { for (let dy = 2; dy <= 12; dy++) { const b = bot.blockAt(new Vec3(x, y + dy, z)); if (b && b.boundingBox === 'block') return false } return true }
/** The server's test for drinking from open water: a source block within two across and one up or down of the feet. */
function drinkable (bot, feet) {
  for (let dx = -2; dx <= 2; dx++) for (let dy = -1; dy <= 1; dy++) for (let dz = -2; dz <= 2; dz++) {
    const b = bot.blockAt(feet.offset(dx, dy, dz))
    if (b && b.name === 'water' && Number((b.getProperties && b.getProperties().level) ?? b.metadata) === 0) return true
  }
  return false
}
/** Nearest dry standing cell, for getting out of water. */
function nearestDry (bot, r = 16) {
  const o = bot.entity.position.floored(); let best = null; let edge = null
  for (let dx = -r; dx <= r; dx++) {
    for (let dz = -r; dz <= r; dz++) {
      const d = Math.hypot(dx, dz); if (best && d >= best.d) continue
      const y = groundAt(bot, o.x + dx, o.z + dz, o.y + 1)
      if (y === null) continue
      if (inland(bot, o.x + dx, y, o.z + dz)) best = { x: o.x + dx, y, z: o.z + dz, d }
      else if (!edge || d < edge.d) edge = { x: o.x + dx, y, z: o.z + dz, d }
    }
  }
  return best || edge
}
async function leaveWater (actor) {
  const bot = actor.bot; let dry = nearestDry(bot); let aimed = Date.now()
  const end = Date.now() + 30000
  try {
    while (Date.now() < end) {
      const w = water(bot)
      if (!w.inWater) break
      if (Date.now() - aimed > 2000) { dry = nearestDry(bot) || dry; aimed = Date.now() }
      bot.setControlState('jump', true)
      if (dry) { await bot.lookAt(new Vec3(dry.x + 0.5, bot.entity.position.y + 1.6, dry.z + 0.5), true); bot.setControlState('forward', true); bot.setControlState('sprint', false) }
      await sleep(80)
    }
  } finally { bot.clearControlStates() }
  // Out of the water is not far enough: go on to ground with no water beside it, or the edge pulls the body back in.
  if (!water(bot).inWater && dry) await walk(actor, dry.x + 0.5, dry.z + 0.5, 0.5)
  await sleep(300)
  return !water(bot).inWater
}

// ---------------------------------------------------------------- server commands
/** Send a command; answer with every system line that came back in the next moment, not just the first. */
async function command (actor, line, settleMs = 700) {
  const bot = actor.bot
  const text = line.trim().startsWith('/') ? line.trim() : '/' + line.trim()
  if (actor.dead) return 'You are dead and can do nothing.'
  const sent = Date.now()
  let first
  try { first = await bot.survival.command(text, undefined, 10000) } catch (error) { first = null; if (error.code === 'EDEAD') return 'You are dead and can do nothing.' }
  await sleep(settleMs)
  const lines = actor.system.filter(s => s.at >= sent - 50 && !s.text.startsWith('[bar]')).map(s => s.text)
  actor.system = actor.system.filter(s => s.at < sent - 50 || s.text.startsWith('[bar]'))
  if (first && !lines.some(l => first.startsWith(l.slice(0, 200)))) lines.unshift(first)
  else if (first) { const i = lines.findIndex(l => first.startsWith(l.slice(0, 200))); if (i >= 0) lines[i] = first }
  return lines.length ? lines.join('\n') : (first || '(no reply)')
}
async function commandJson (actor, line, shape) {
  const reply = await actor.bot.survival.command(line, message => { try { const v = JSON.parse(message); return shape(v) } catch { return false } }, 10000)
  return JSON.parse(reply)
}
async function job (actor) { return commandJson(actor, '/survival jobs', v => 'active' in v) }
/** Wait for the actor's server-timed job to finish. */
async function finishJob (actor, maxMs = 120000) {
  const end = Date.now() + maxMs; let last = null
  while (Date.now() < end) {
    if (actor.order && actor.order.abort) { await command(actor, '/survival cancel', 200); return { done: false, why: actor.order.abort } }
    await sleep(1200)
    try { last = await job(actor) } catch { continue }
    if (!last.active) { await resync(actor); return { done: true, result: last.last } }
    if (actor.order) actor.order.progress = `${last.operation} ${Math.round(100 * (last.progress || 0) / (last.required || 1))}%`
  }
  await command(actor, '/survival cancel', 200)
  return { done: false, why: 'job still unfinished after ' + Math.round(maxMs / 1000) + 's; cancelled', job: last }
}
async function readBody (actor) {
  const bot = actor.bot; const before = bot.entity.position.clone()
  const b = await bot.survival.query()
  actor.body = b; actor.bodyAt = Date.now()
  // The report says where the server has the body. A client walks by telling the server where it is, and when the
  // server stops agreeing every command is judged from a place the client is not: count how long the two have differed.
  if (Array.isArray(b.position) && bot.entity) {
    const [x, y, z] = b.position; const gap = Math.min(before.distanceTo(new Vec3(x, y, z)), bot.entity.position.distanceTo(new Vec3(x, y, z)))
    actor.serverAt = b.position; actor.apart = gap > 3 ? (actor.apart || 0) + 1 : 0; actor.gap = gap
  }
  return b
}
/** Put the client back where the server has the body, and stand still there. */
function comeBack (actor) {
  const bot = actor.bot; if (!actor.serverAt || !bot.entity) return null
  const was = pos(bot.entity.position)
  bot.clearControlStates(); bot.entity.position.set(actor.serverAt[0], actor.serverAt[1], actor.serverAt[2]); bot.entity.velocity.set(0, 0, 0)
  actor.apart = 0; actor.index = null
  return { was, now: pos(bot.entity.position) }
}
function bodySummary (b) {
  if (!b) return null
  const pick = {}
  for (const k of ['hunger', 'thirst', 'temperature', 'fatigue', 'health', 'recreation', 'burden']) if (k in b) pick[k] = typeof b[k] === 'number' ? round(b[k]) : b[k]
  if (b.conditions) pick.conditions = b.conditions
  if (b.capacities) pick.capacities = b.capacities
  return pick
}

// ---------------------------------------------------------------- hands
async function pickup (actor, r = 5, center) {
  const bot = actor.bot; const c = center || bot.entity.position.clone(); let visited = 0
  for (let round = 0; round < 3; round++) {
    const drops = Object.values(bot.entities).filter(e => e.name === 'item' && e.position.distanceTo(c) <= r)
    if (!drops.length) break
    for (const drop of drops) {
      if (actor.order && actor.order.abort) return visited
      await walk(actor, drop.position.x, drop.position.z, 0.6); visited++
    }
    await sleep(500)
  }
  return visited
}
const withTimeout = (promise, ms, what) => Promise.race([promise, new Promise((_, reject) => setTimeout(() => reject(new Error(what + ' timed out')), ms))])
/** Within the server's work range of a block: its centre within four blocks of the feet (kept a little inside). */
const inReach = (bot, p, reach) => bot.entity.position.distanceTo(p.offset(0.5, 0.5, 0.5)) <= reach
/** Get within reach of a block from whichever dry standing cell near it can be walked to, nearest first. */
async function approach (actor, p, reach = 3.7, prefer = null) {
  const bot = actor.bot
  if (inReach(bot, p, reach) && (!prefer || prefer(bot.entity.position.floored()))) return true
  // One search says whether the place can be come at at all. Where it cannot, say so at once: trying each cell beside
  // it in turn is the same failed search many times over, with the body standing idle meanwhile.
  if (!prefer && bot.entity.position.distanceTo(p.offset(0.5, 0.5, 0.5)) > reach + 3) {
    const route = findPath(bot, { x: p.x, z: p.z }, Math.max(1, reach - 1.5))
    if (route.partial) {
      const end = route.cells[route.cells.length - 1]
      if (Math.hypot(end.x - p.x, end.z - p.z) > reach + 2) return false
    }
    for (const cell of route.cells.slice(1)) {
      if (actor.order && actor.order.abort) return false
      if (inReach(bot, p, reach - 0.2)) break
      if (!(await steer(actor, cell))) break
    }
    await settle(bot)
    if (inReach(bot, p, reach + 0.2)) return true
  }
  const here = bot.entity.position
  const cells = []
  for (let dx = -3; dx <= 3; dx++) for (let dz = -3; dz <= 3; dz++) for (let dy = -2; dy <= 2; dy++) {
    const c = { x: p.x + dx, y: p.y + dy, z: p.z + dz }
    if (Math.hypot(dx + 0, dy, dz) > reach - 0.5 || !standable(bot, c.x, c.y, c.z)) continue
    cells.push({ ...c, d: Math.hypot(c.x + 0.5 - here.x, c.z + 0.5 - here.z) + Math.abs(c.y - here.y) + (prefer && !prefer(c) ? 25 : 0) })
  }
  cells.sort((a, b) => a.d - b.d)
  for (const c of cells.slice(0, 4)) {
    if (actor.order && actor.order.abort) return false
    await walk(actor, c.x + 0.5, c.z + 0.5, 0.5); await settle(bot)
    if (inReach(bot, p, reach + 0.2)) return true
  }
  return inReach(bot, p, reach + 0.2)
}
// Blocks the server takes only as timed work: hand-gathered ground resources and fruit trees.
const GROUND = /(^|:)(loose_stones|wild_plants|fallen_sticks|seashells|murex_shells|pearl_oysters|wild_[a-z]+)$/
/** Pickaxe material without a block entity: the server freezes breaking it by hand and asks for /survival mine. */
function mineable (block) { return !!block && /pickaxe/.test(String(block.material || '')) && !/chest|furnace|barrel|hopper|kiln|forge|bloomery|anvil|loom/.test(block.name) }
function gainsBetween (before, after) {
  const gained = {}
  for (const k of Object.keys(after)) if ((after[k] || 0) > (before[k] || 0)) gained[k] = after[k] - (before[k] || 0)
  return gained
}
const firstLine = reply => String(reply).split(String.fromCharCode(10))[0]
/** The server starts timed work with these words and refuses it with anything else. */
const started = reply => /^(Started|Hunting started|Took up)/.test(firstLine(reply))
/** Take one block by whatever means the world asks for it: timed hand-gathering, timed mining, or breaking it. */
async function work (actor, p) {
  const bot = actor.bot
  const block = bot.blockAt(p)
  if (!block || /^(air|cave_air)$/.test(block.name)) return { ok: false, why: 'nothing there' }
  // What is cut from a tree leaves what stood on it to fall (physics/Collapse): work it from open sky, never from under the crown.
  const tree = /_(log|wood|stem|hyphae)$/.test(block.name)
  const safe = tree ? c => clearOverhead(bot, c.x, c.y, c.z) : null
  if (!(await approach(actor, p, 3.7, safe))) return { ok: false, why: 'could not get within reach of ' + block.name, unreachable: true }
  const before = itemsOf(bot)
  let reply = null
  const timed = GROUND.test(block.name) ? 'gather' : /fruit_tree|coffee_bush|tea_bush/.test(block.name) ? 'harvest' : mineable(block) ? 'mine' : null
  if (timed) {
    reply = await command(actor, `/survival ${timed} ${p.x} ${p.y} ${p.z}`, 300)
    if (!started(reply)) return { ok: false, block: block.name, server: firstLine(reply) }
    const end = await finishJob(actor)
    if (!end.done) return { ok: false, why: end.why, server: reply }
  } else {
    // As long as the block should take by hand or with the tool held, and a little more; not a blind minute.
    let expected = 5000; try { expected = bot.digTime(block) } catch {}
    try { await bot.lookAt(p.offset(0.5, 0.5, 0.5), true); await withTimeout(bot.dig(block, true), Math.min(60000, expected * 1.5 + 3000), 'breaking ' + block.name) } catch (error) { try { bot.stopDigging() } catch {} return { ok: false, block: block.name, why: error.message } }
  }
  // Let whatever the cut brought down land before going under it for the drops.
  await sleep(tree ? 2500 : 500)
  await pickup(actor, 4, p.offset(0.5, 0, 0.5)); await resync(actor)
  const now = bot.blockAt(p)
  const gained = gainsBetween(before, itemsOf(bot))
  return { ok: !now || now.name !== block.name || Object.keys(gained).length > 0, block: block.name, method: timed || 'break', gained, server: reply || undefined }
}
const SIDES = [[0, -1, 0], [1, 0, 0], [-1, 0, 0], [0, 0, 1], [0, 0, -1], [0, 1, 0]]
async function place (actor, x, y, z, itemName) {
  const bot = actor.bot; const p = new Vec3(x, y, z)
  const item = bot.inventory.items().find(i => i.name === full(itemName) || i.name === itemName)
  if (!item) return { ok: false, why: 'has no ' + itemName }
  const existing = bot.blockAt(p)
  if (existing && existing.boundingBox === 'block') return { ok: false, why: 'occupied by ' + existing.name }
  let ref = null; let face = null
  for (const [dx, dy, dz] of SIDES) {
    const b = bot.blockAt(p.offset(dx, dy, dz))
    if (b && b.boundingBox === 'block') { ref = b; face = new Vec3(-dx, -dy, -dz); break }
  }
  if (!ref) return { ok: false, why: 'nothing solid beside that cell to place against' }
  const feet = bot.entity.position
  if (Math.abs(feet.x - (x + 0.5)) < 0.9 && Math.abs(feet.z - (z + 0.5)) < 0.9 && feet.y > y - 1.8 && feet.y < y + 1) await walk(actor, x + 2.5, z + 0.5, 0.8)
  await approach(actor, p, 4)
  await bot.equip(item, 'hand')
  try { await withTimeout(bot.placeBlock(ref, face), 6000, 'place') } catch { /* the update may lag the placement */ }
  await sleep(300); await resync(actor)
  const now = bot.blockAt(p)
  return { ok: !!now && now.name !== 'air' && now.name !== (existing && existing.name), now: now && now.name }
}
async function hold (actor, name) {
  await resync(actor)
  const item = actor.bot.inventory.items().find(i => i.name === full(name) || i.name === name)
  if (!item) return { ok: false, why: 'has no ' + name }
  await actor.bot.equip(item, 'hand'); await sleep(250)
  await resync(actor)
  const held = actor.bot.heldItem
  return held && held.name === item.name ? { ok: true, holding: item.name } : { ok: false, why: 'could not take ' + name + ' in hand (holding ' + (held ? held.name : 'nothing') + ')' }
}

/** Blocks in the order a person on the ground can best get at them: near, and at about the height of their hands. */
function byReach (bot, positions) {
  const f = bot.entity.position
  const score = p => Math.hypot(p.x + 0.5 - f.x, p.z + 0.5 - f.z) + Math.max(0, p.y - f.y - 1) * 4 + Math.max(0, f.y - p.y - 2) * 2
  return [...positions].sort((a, b) => score(a) - score(b))
}
/** One sweep of the land around the feet: the nearest block of every kind, for planning without a scan per kind. */
function scanIndex (bot, r = 40, down = 14, up = 14) {
  const o = bot.entity.position.floored(); const index = new Map()
  for (let dx = -r; dx <= r; dx++) {
    for (let dz = -r; dz <= r; dz++) {
      const flat = Math.hypot(dx, dz); if (flat > r) continue
      for (let dy = -down; dy <= up; dy++) {
        let b; try { b = bot.blockAt(o.offset(dx, dy, dz), false) } catch { b = null }
        if (!b || b.name === 'air' || b.name === 'cave_air') continue
        const d = Math.hypot(flat, dy); const seen = index.get(b.name)
        if (!seen || d < seen.d) index.set(b.name, { d, p: [o.x + dx, o.y + dy, o.z + dz] })
      }
    }
  }
  return index
}
/** Whoever wants to hear of every sweep (the crew, to remember food it has seen). */
const onScan = { hook: null }
/** Would taking this block let water in? A block with water beside or above it is not dug. */
function floods (bot, p) {
  for (const [dx, dy, dz] of [[1, 0, 0], [-1, 0, 0], [0, 0, 1], [0, 0, -1], [0, 1, 0]]) if (isWater(bot.blockAt(p.offset(dx, dy, dz)))) return true
  return false
}
/** A standing cell with no water within a block of it: somewhere to be, not the brink of the lake. */
function inland (bot, x, y, z) {
  if (!standable(bot, x, y, z)) return false
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) for (const dy of [-1, 0]) if (isWater(bot.blockAt(new Vec3(x + dx, y + dy, z + dz)))) return false
  return true
}
/** The index, kept for a few seconds per actor. */
function nearIndex (actor, ttl = 15000) {
  if (!actor.index || Date.now() - actor.index.at > ttl) { actor.index = { at: Date.now(), map: scanIndex(actor.bot) }; if (onScan.hook) onScan.hook(actor, actor.index.map) }
  return actor.index.map
}
/** Ask the server for the whole inventory again. The client's own record drifts (a placed block not taken off,
 * stacks the mod merges), and every count and every choice of what to hold rests on it. */
async function resync (actor) {
  const bot = actor.bot
  if (!bot._syncWindow || !bot.inventory) return false
  try { await Promise.race([bot._syncWindow(bot.inventory), sleep(2500)]); actor.syncedAt = Date.now(); return true } catch { return false }
}
module.exports = { climbOut, unwedge, clearOverhead, comeBack, footing, climbable, clearWay, onScan, floods, inland, resync, byReach, scanIndex, nearIndex,  started, firstLine, inReach, GROUND, Vec3, sleep, full, round, pos, MOD, itemsOf, isWater, passable, solid, water, surface, findBlocks, nearby, standable, groundAt, findPath, steer, walk, settle, openAbove, drinkable, nearestDry, leaveWater, command, commandJson, job, finishJob, readBody, bodySummary, pickup, approach, gainsBetween, work, place, hold, mineable }
