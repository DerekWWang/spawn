// The actors' own minds: dumb, deterministic, and diligent. Each one follows the commander's orders and policy,
// and between and around those orders looks after itself: it gets out of water, drinks and eats when its policy
// says so (pausing the order and picking it up again after), steps away from whatever is hurting it, remembers
// what made anyone sick, skips a step that fails rather than giving up its queue, and when it has nothing to do
// works at its standing job. `make` is how it comes to hold an item: it weighs every way the world offers
// (recipes.js) and works down the cheapest it can actually take, trying the next when one fails.
const fs = require('fs')
const B = require('./body')
const { rank, norm, MOD, PREY: PREY_TABLE } = require('./recipes')
const { Vec3, sleep, round, pos, itemsOf } = B

// The base drives cannot be switched off. Policy only moves their thresholds (within LIMITS) and sets preferences.
const DEFAULT_POLICY = {
  drinkBelow: 45, drinkUntil: 90, // thirst, 0-100: go and drink below the first, drink up to the second
  eatBelow: 45, eatUntil: 80, // hunger, 0-100
  water: 'safe-first', // 'safe-first': a carried pot, else open water (if open water has made someone ill, only once
  // thirst is below CRITICAL); 'open': drink open water whatever is known
  foods: null, // preferred foods, best first; null = best by what is known. Foods that made someone ill come last.
  home: null, // [x, z]: where to go when idle and nothing else to do
  roam: 40, // how far from home an idle actor may be before it walks back
  idle: null, // a standing order { task, args } run again and again whenever nothing else is queued
  onFail: 'skip' // when a queued step fails: 'skip' to the next step, or 'stop' the queue
}
const LIMITS = { drinkBelow: [25, 80], drinkUntil: [60, 100], eatBelow: [25, 80], eatUntil: [50, 100] }
/** How far back a known station is worth walking to rather than building another: a bench of four planks is not worth a long walk; a loom or a kiln is. */
const far = name => /crafting_table$/.test(name) ? 48 : 160
function remembered (file) { try { const saved = JSON.parse(fs.readFileSync(file, 'utf8')); return saved && saved.stations && typeof saved.stations === 'object' ? saved.stations : {} } catch { return {} } }
const CRITICAL = 20 // below this, thirst or hunger outranks everything but breath and danger, and caution gives way
// Drives in order: a higher one may interrupt a lower one, never the other way round.
const RANK = { swim: 5, flee: 4, drink_critical: 3, eat_critical: 3, drink: 2, eat: 2, pickup: 1, go_home: 1 }
const CARE = new Set(['drink', 'feed', 'eat', 'leave_water', 'flee', 'go_home', 'pickup'])

function createCrew ({ actors, alert, record, book, waiters, memory }) {
  // stations: every workstation anyone has set down or seen, by kind, so that nobody builds a second for want of looking.
  const team = { wet: [], herds: new Map(), stations: remembered(memory), avoid: new Map(), water: null, shores: [], food: new Map(), season: null, notes: [] } // shared knowledge
  const wake = () => { for (const w of waiters) w() }
  const note = (actor, text) => { team.notes.push(`${actor.name}: ${text}`); if (team.notes.length > 30) team.notes.shift(); alert(actor.name, text) }

  // ------------------------------------------------------------ what an actor knows of the world at hand
  const have = (bot, item) => itemsOf(bot)[norm(item)] || 0
  function nearestExact (bot, name, r = 48) {
    const want = norm(name)
    const p = bot.findBlocks({ matching: b => b && b.name === want, maxDistance: r, count: 1 })[0]
    return p ? { p, d: p.distanceTo(bot.entity.position) } : null
  }
  function stationAt (actor, name, r = 32) {
    const seen = nearestExact(actor.bot, name, r)
    if (seen) { remember(name, seen.p); return seen }
    return null
  }
  function remember (name, p) {
    const list = team.stations[name] || (team.stations[name] = [])
    if (list.some(q => q[0] === p.x && q[1] === p.y && q[2] === p.z)) return
    list.push([p.x, p.y, p.z]); if (list.length > 12) list.shift(); keep()
  }
  function forget (name, p) {
    const list = team.stations[name]; if (!list) return
    team.stations[name] = list.filter(q => !(q[0] === p.x && q[1] === p.y && q[2] === p.z)); if (!team.stations[name].length) delete team.stations[name]; keep()
  }
  function keep () { if (memory) try { fs.writeFileSync(memory, JSON.stringify({ stations: team.stations })) } catch {} }
  /** The nearest station of a kind that the team knows of, however far the eye cannot see (within a morning's walk). */
  function knownStation (actor, name, max = far(name)) {
    const here = actor.bot.entity.position; let best = null
    for (const q of team.stations[name] || []) { const p = new Vec3(q[0], q[1], q[2]); const d = p.distanceTo(here); if (d <= max && (!best || d < best.d)) best = { p, d } }
    return best
  }
  function worldView (actor, fresh = false) {
    const bot = actor.bot
    if (fresh) actor.index = null
    const index = B.nearIndex(actor)
    const near = (name, r) => { const e = index.get(name); return e && e.d <= r ? e.d : null }
    return {
      season: team.season ? team.season.value : undefined,
      have: it => have(bot, it),
      nearest: block => near(block, 48),
      stationNear: id => { const d = near(norm(id), 32); if (d !== null) return d; const known = knownStation(actor, norm(id)); return known ? known.d : null },
      waterNear: () => near('water', 24) !== null,
      creatureNear: name => { const seen = quarry(actor, name); return seen ? seen.d + (seen.live ? 0 : 12) : null }
    }
  }
  // ------------------------------------------------------------ animals
  const HUNTED = new Set(PREY_TABLE.map(([c]) => c))
  /** Note every animal worth eating that anyone can see, so that a hungry actor knows where the herds were. */
  function sightings (actor) {
    const bot = actor.bot; if (!bot.entity) return
    for (const e of Object.values(bot.entities)) {
      if (!HUNTED.has(e.name) || e.position.distanceTo(bot.entity.position) > 64) continue
      team.herds.set(e.name + '@' + Math.round(e.position.x / 8) + ',' + Math.round(e.position.z / 8), { creature: e.name, p: [e.position.x, e.position.y, e.position.z], at: Date.now() })
    }
    while (team.herds.size > 60) team.herds.delete(team.herds.keys().next().value)
  }
  /** The nearest animal of a kind: one in sight, else where one was seen in the last ten minutes. */
  function quarry (actor, name, skip = new Set()) {
    const bot = actor.bot; if (!bot.entity) return null
    sightings(actor)
    const here = bot.entity.position; let best = null
    for (const e of Object.values(bot.entities)) {
      if (e.name !== name || skip.has(e.id) || B.isWater(bot.blockAt(e.position.floored()))) continue
      const d = e.position.distanceTo(here); if (d <= 64 && (!best || d < best.d)) best = { e, d, live: true }
    }
    if (best) return best
    for (const [key, h] of team.herds) {
      if (h.creature !== name) continue
      if (Date.now() - h.at > 600000) { team.herds.delete(key); continue }
      const d = Math.hypot(h.p[0] - here.x, h.p[2] - here.z); if (d <= 128 && (!best || d < best.d)) best = { p: h.p, d, key, live: false }
    }
    return best
  }

  // Every sweep of the land by anyone adds the food sources in it to what the team remembers.
  let foodBlocks = null
  const isFoodBlock = name => {
    if (!foodBlocks) foodBlocks = new Set(FORAGE.flatMap(f => book.routesFor(f).filter(r => r.kind === 'gather').map(r => r.block)))
    return foodBlocks.has(name)
  }
  B.onScan.hook = (actor, index) => { const w = index.get('water'); if (w) { const k = Math.round(w.p[0] / 16) + ',' + Math.round(w.p[2] / 16); if (!team.wet.some(q => q.k === k)) { team.wet.push({ k, p: w.p }); if (team.wet.length > 16) team.wet.shift() } } for (const [name, e] of index) if (isFoodBlock(name)) { team.food.set(name + '@' + e.p.join(), { block: name, p: e.p }); if (team.food.size > 60) team.food.delete(team.food.keys().next().value) } }

  // ------------------------------------------------------------ making an item
  async function makeItem (actor, item, count, goal, depth = 0) {
    const bot = actor.bot; item = norm(item)
    if (have(bot, item) >= count) return true
    if (depth > 5) { goal.trace.push(`${item}: too many steps deep`); return false }
    for (let attempt = 0; attempt < 6; attempt++) {
      if (actor.order && actor.order.abort) return false
      await B.resync(actor)
      if (have(bot, item) >= count) return true
      const ranked = rank(book, item, count, worldView(actor, attempt > 0), goal.failed)
      const best = ranked[0]
      if (!best || best.cost === Infinity) {
        goal.trace.push(`${item} x${count}: no way open` + (ranked.length ? ' (' + ranked.slice(0, 4).map(r => `${r.route.kind} ${r.route.id}: ${r.why || 'unavailable'}`).join('; ') + ')' : ' (no known way to make or find it)'))
        return false
      }
      const r = best.route; const tag = `${r.kind}:${r.id}`
      actor.order && (actor.order.progress = `${item} by ${tag}`)
      goal.trace.push(`${item} x${count}: trying ${tag}`)
      let why
      try { why = await ROUTES[r.kind](actor, r, item, count, goal, depth) } catch (error) { why = String(error && (error.message || error)) }
      if (have(bot, item) >= count) { goal.trace.push(`${item}: have ${have(bot, item)} by ${tag}`); return true }
      goal.failed.add(tag); goal.trace.push(`${item}: ${tag} failed: ${why || 'made too few'}`)
    }
    return false
  }
  /** Get one of several interchangeable items: one already held, else the cheapest to come by. */
  async function makeAny (actor, alts, count, goal, depth) {
    const held = alts.find(a => have(actor.bot, a) >= count)
    if (held) return held
    const view = worldView(actor)
    const order = alts.map(a => ({ a, c: (rank(book, a, count, view, goal.failed)[0] || { cost: Infinity }).cost })).sort((x, y) => x.c - y.c)
    for (const { a, c } of order) { if (c === Infinity) break; if (await makeItem(actor, a, count, goal, depth + 1)) return a }
    return null
  }
  /** A station or table to work at: one in sight, else one the team knows of and can walk back to, else one made and
   * set down beside the actor. */
  async function ensureStation (actor, name, goal, depth) {
    let at = stationAt(actor, name)
    for (let back = 0; !at && back < 3; back++) {
      const known = knownStation(actor, name); if (!known) break
      if (actor.order && actor.order.abort) return null
      goal.trace.push(`going back to the ${name} at ${known.p}`)
      await B.walk(actor, known.p.x + 0.5, known.p.z + 0.5, 3)
      at = stationAt(actor, name)
      if (at) break
      // Close enough to see the place and it is not there: it is gone. Too far to see: the way is shut, and one is made here.
      const there = actor.bot.blockAt(known.p)
      if (there && there.name !== name) { forget(name, known.p); goal.trace.push(`the ${name} at ${known.p} is gone`); continue }
      goal.trace.push(`could not get back to the ${name} at ${known.p}`); break
    }
    if (!at) {
      goal.trace.push(`no ${name} in sight or known within reach: making one`)
      if (!(await makeItem(actor, name, 1, goal, depth + 1))) return null
      const placed = await placeBeside(actor, name)
      if (!placed) { goal.trace.push(`could not set down a ${name}`); return null }
      at = stationAt(actor, name, 8)
      if (at) goal.trace.push(`set down a ${name} at ${at.p}`)
    }
    if (!at) return null
    // A station left behind while fetching things may be a long way back: walk most of the way, then step up to it, and say so if it cannot be reached.
    if (at.p.distanceTo(actor.bot.entity.position) > 10) await B.walk(actor, at.p.x + 0.5, at.p.z + 0.5, 3)
    if (at.p.distanceTo(actor.bot.entity.position) > 3.5) await B.approach(actor, at.p, 3.2)
    if (at.p.distanceTo(actor.bot.entity.position) > 5.5) { goal.trace.push(`could not get back to the ${name} at ${at.p}`); return null }
    return at
  }
  async function placeBeside (actor, item) {
    const bot = actor.bot; const o = bot.entity.position.floored()
    for (const [dx, dz] of [[2, 0], [0, 2], [-2, 0], [0, -2], [2, 1], [1, 2], [-2, 1], [1, -2], [2, 2], [-2, -2]]) {
      for (const dy of [0, 1, -1]) {
        const c = o.offset(dx, dy, dz); const cell = bot.blockAt(c); const below = bot.blockAt(c.offset(0, -1, 0))
        if (cell && cell.boundingBox === 'empty' && !B.isWater(cell) && B.solid(below)) { const r = await B.place(actor, c.x, c.y, c.z, item); if (r.ok) return c }
      }
    }
    return null
  }
  async function shore (actor) {
    const bot = actor.bot; const here = bot.entity.position.floored()
    const cells = []
    for (let dx = -24; dx <= 24; dx++) for (let dz = -24; dz <= 24; dz++) cells.push([here.x + dx, here.z + dz, Math.hypot(dx, dz)])
    cells.sort((p, q) => p[2] - q[2])
    for (const [cx, cz] of cells) for (let fy = here.y + 8; fy >= here.y - 14; fy--) if (B.standable(bot, cx, fy, cz) && B.openAbove(bot, cx, fy, cz) && B.drinkable(bot, new Vec3(cx, fy, cz))) return { x: cx, y: fy, z: cz }
    return null
  }
  async function toShore (actor) {
    const s = await shore(actor); if (!s) return { ok: false, why: 'no dry footing beside standing water within 24 blocks' }
    const bot = actor.bot
    await B.walk(actor, s.x + 0.5, s.z + 0.5, 0.4); await B.settle(bot)
    if (!B.drinkable(bot, bot.entity.position.floored())) { await B.steer(actor, s, 4000); await B.settle(bot) }
    return B.drinkable(bot, bot.entity.position.floored()) ? { ok: true, at: [s.x, s.y, s.z] } : { ok: false, why: 'could not reach the shore at ' + [s.x, s.y, s.z].join(',') }
  }

  const ROUTES = {
    /** Kill an animal by hand and take up what it leaves: walk to it by the paths, then close and strike. */
    async hunt (actor, r, item, count, goal, depth) {
      const bot = actor.bot; const skip = new Set(); let reason = ''
      for (let kill = 0; have(bot, item) < count && kill < 4; kill++) {
        if (actor.order && actor.order.abort) return actor.order.abort
        let seen = quarry(actor, r.creature, skip)
        if (seen && !seen.live) {
          // Only remembered: go to where the herd was and look again.
          goal.trace.push(`going to where a ${r.creature} was seen at ${seen.p.map(Math.round).join(',')}`)
          await B.walk(actor, seen.p[0], seen.p[2], 4); team.herds.delete(seen.key)
          seen = quarry(actor, r.creature, skip)
        }
        if (!seen || !seen.live) return reason || 'no ' + r.creature + ' in sight'
        const target = seen.e; const before = itemsOf(bot)
        const end = Date.now() + 60000; let hits = 0; let last = null
        try {
          while (Date.now() < end && bot.entities[target.id]) {
            if (actor.order && actor.order.abort) return actor.order.abort
            const p = target.position; last = p.clone(); const d = bot.entity.position.distanceTo(p)
            if (B.isWater(bot.blockAt(p.floored()))) { await sleep(500); if (Date.now() > end - 45000) break; continue }
            if (d > 5) { const w = await B.walk(actor, p.x, p.z, 1.5); if (!w.arrived && bot.entity.position.distanceTo(target.position) > 5) { reason = 'could not get near the ' + r.creature + ': ' + w.why; break } continue }
            // Close: face it and strike, stepping in when it is just out of reach.
            await bot.lookAt(p.offset(0, (target.height || 0.9) / 2, 0), true)
            if (bot.entity.position.offset(0, 1.62, 0).distanceTo(p.offset(0, (target.height || 0.9) / 2, 0)) <= 2.9) { bot.setControlState('forward', false); bot.attack(target); hits++; await sleep(550) } else {
              const ahead = bot.blockAt(bot.entity.position.offset(Math.sin(-bot.entity.yaw), -0.5, -Math.cos(bot.entity.yaw)).floored())
              bot.setControlState('forward', !B.isWater(ahead)); bot.setControlState('jump', !!bot.entity.isCollidedHorizontally); await sleep(100)
            }
          }
        } finally { bot.clearControlStates() }
        if (bot.entities[target.id]) { skip.add(target.id); reason = reason || `the ${r.creature} got away after ${hits} blows`; continue }
        await sleep(700)
        await B.pickup(actor, 6, last || bot.entity.position.clone()); await B.resync(actor)
        const gained = B.gainsBetween(before, itemsOf(bot))
        book.learnHunt(r.creature, gained)
        goal.trace.push(`killed a ${r.creature} in ${hits} blows: ${JSON.stringify(gained)}`)
        if (!Object.keys(gained).length) reason = 'killed a ' + r.creature + ' but found nothing it left'
      }
      return have(bot, item) >= count ? null : reason || 'too few ' + item
    },
    async gather (actor, r, item, count, goal, depth) {
      const bot = actor.bot; const tried = new Set(); let fails = 0; let reason = ''
      if (r.tool) { const tool = await makeAny(actor, r.tool, 1, goal, depth); if (!tool) return 'needs a pickaxe'; await B.hold(actor, tool) }
      while (have(bot, item) < count && fails < 5) {
        if (actor.order && actor.order.abort) return actor.order.abort
        const found = B.byReach(bot, bot.findBlocks({ matching: b => b && b.name === r.block, maxDistance: 48, count: 32 }).filter(p => !tried.has(p.toString()) && !B.floods(bot, p)))
        if (!found.length) return reason || 'no more ' + r.block + ' in sight'
        const p = found[0]; tried.add(p.toString())
        const w = await B.work(actor, p)
        if (w.ok && w.gained) book.learnYield(r.block, w.gained)
        if (!w.ok) { fails++; reason = w.server || w.why || 'failed' }
      }
      return have(bot, item) >= count ? null : reason
    },
    async craft (actor, r, item, count, goal, depth) {
      const bot = actor.bot; const made = r.out[item] || 1
      const times = Math.max(1, Math.ceil((count - have(bot, item)) / made))
      // The bench comes first: making one uses up planks, and it must not take them from this recipe's own supplies.
      if (r.table && !(await ensureStation(actor, 'crafting_table', goal, depth))) return 'no crafting table to work at'
      for (let round = 0; round < 3; round++) {
        // Choose each slot's filling: what is carried first, then the cheapest way to come by the rest.
        const need = {}
        for (const alts of r.slots) {
          const held = alts.find(a => have(bot, a) - (need[a] || 0) >= times)
          let pick = held
          if (!pick) { pick = await makeAny(actor, alts, (need[alts[0]] || 0) + times, goal, depth); if (!pick) return 'cannot get any of ' + alts.slice(0, 4).join('/') }
          need[pick] = (need[pick] || 0) + times
        }
        let short = false
        for (const [it, n] of Object.entries(need)) if (have(bot, it) < n && !(await makeItem(actor, it, n, goal, depth + 1))) short = true
        if (short) continue
        if (Object.entries(need).every(([it, n]) => have(bot, it) >= n)) break
      }
      if (r.table && !(await ensureStation(actor, 'crafting_table', goal, depth))) return 'no crafting table to work at'
      for (let i = 0; i < times; i++) {
        if (actor.order && actor.order.abort) return actor.order.abort
        const reply = await B.command(actor, `/survival craft ${r.id}`, 300)
        if (!B.started(reply)) return B.firstLine(reply)
        const end = await B.finishJob(actor)
        if (!end.done) return end.why
      }
      await sleep(300)
      return null
    },
    async process (actor, r, item, count, goal, depth) {
      const bot = actor.bot; const made = r.out[item] || 1
      const times = Math.max(1, Math.ceil((count - have(bot, item)) / made))
      if (r.safeWater) return 'needs an open pot of boiled water in the offhand'
      for (let i = 0; i < times; i++) {
        // The station comes first for the same reason: a loom takes cordage, a kiln clay, and the charge must still be there after.
        if (!(await ensureStation(actor, MOD + r.station, goal, depth))) return 'no ' + r.station + ' to work at'
        // Work of this kind left unfinished at the station is taken up again before anything new is fetched for it.
        let reply = await B.command(actor, `/survival produce ${r.id}`, 400)
        const resumed = /^Took up/.test(B.firstLine(reply))
        if (resumed) goal.trace.push(`took up the unfinished ${r.id}`)
        if (!resumed && !B.started(reply)) {
        // Fetching may lead far from the station, and getting back to it may cost something carried: look again once there.
        for (let round = 0; round < 3; round++) {
          if (!(await makeItem(actor, r.input, r.inputCount, goal, depth + 1))) return 'no ' + r.input
          if (r.second && !(await makeItem(actor, r.second, r.secondCount, goal, depth + 1))) return 'no ' + r.second
          if (!(await ensureStation(actor, MOD + r.station, goal, depth))) return 'no ' + r.station + ' to work at'
          await B.resync(actor)
          if (have(bot, r.input) >= r.inputCount && (!r.second || have(bot, r.second) >= r.secondCount)) break
        }
        for (let tryFuel = 0; tryFuel < 4; tryFuel++) {
          await B.hold(actor, r.input)
          if (r.second) { const s = bot.inventory.items().find(x => x.name === r.second); if (s) await bot.equip(s, 'off-hand') }
          reply = await B.command(actor, `/survival produce ${r.id}`, 400)
          const heat = /needs (\d+) heat ticks and the fire holds (\d+)/.exec(reply)
          if (!heat) break
          const want = Number(heat[1]) - Number(heat[2])
          const fuel = await fuelUp(actor, r.station, want, goal, depth)
          if (!fuel) return 'no fuel for the ' + r.station + ': ' + B.firstLine(reply)
        }
        }
        if (!B.started(reply)) return B.firstLine(reply)
        // Stay by the station until it holds the product, then take it.
        let away = 0
        const end = Date.now() + 15 * 60 * 1000
        while (Date.now() < end) {
          if (actor.order && actor.order.abort) return actor.order.abort
          await sleep(3000)
          const st = await B.command(actor, '/survival station', 200)
          // "Output 1 some:thing" when done; "Output 0 minecraft:air" while the work goes on.
          if (/Output [1-9]\d* /.test(st)) break
          // Pulled away (to drink, to eat) or never quite there: go back to the work, which waits for its worker.
          if (!/^Station /.test(B.firstLine(st))) { if (++away > 20 || !(await ensureStation(actor, MOD + r.station, goal, depth))) return 'lost the ' + r.station + ': ' + B.firstLine(st); await B.command(actor, `/survival produce ${r.id}`, 300) }
        }
        const got = await B.command(actor, '/survival collect', 300); await B.resync(actor)
        if (!/Collected/.test(got)) return B.firstLine(got)
      }
      return null
    },
    async hand (actor, r, item, count, goal, depth) {
      const bot = actor.bot; const made = r.out[item] || 1
      const times = Math.min(64, Math.max(1, Math.ceil((count - have(bot, item)) / made)))
      for (const i of r.inputs) if (!(await makeAny(actor, i.alts, i.count * times, goal, depth))) return 'no ' + i.alts.slice(0, 3).join('/')
      for (const t of r.tools) if (!(await makeAny(actor, t, 1, goal, depth))) return 'no tool ' + t.slice(0, 3).join('/')
      for (const s of r.stations) if (!(await ensureStation(actor, MOD + s, goal, depth))) return 'no ' + s + ' nearby'
      if (r.needs.includes('env:water')) { const w = await toShore(actor); if (!w.ok) return 'needs water: ' + w.why }
      const sent = Date.now()
      const reply = await B.command(actor, `/techtree make ${r.id} ${times}`, 500)
      if (/not discovered/.test(reply)) { book.unknown.add(r.id); return B.firstLine(reply) }
      if (/Cannot do|busy/.test(reply)) return B.firstLine(reply)
      book.known.add(r.id)
      const end = Date.now() + (r.ticks * times / 20) * 4000 + 60000
      while (Date.now() < end) {
        if (actor.order && actor.order.abort) { await B.command(actor, '/techtree stop', 200); return actor.order.abort }
        await sleep(1500)
        const said = actor.system.filter(s => s.at >= sent).map(s => s.text).join('\n')
        if (/Finished: |stopped|wasted/.test(said)) { const m = /(Finished: [^\n]*|[^\n]*stopped[^\n]*|[^\n]*wasted[^\n]*)/.exec(said); return /Finished/.test(m[0]) ? null : m[0] }
        if (have(bot, item) >= count) return null
      }
      return 'no word that the work finished'
    }
  }
  async function fuelUp (actor, station, heatTicks, goal, depth) {
    // charcoal and coal 8000 each, sticks 80, a log 96000 but only in a kiln
    const options = [['charcoal', 8000], ['coal', 8000], ...(station === 'kiln' ? [['oak_log', 96000], ['jungle_log', 96000], ['birch_log', 96000], ['spruce_log', 96000]] : []), ['stick', 80]]
    for (const [fuel, each] of options) {
      const n = Math.max(1, Math.min(64, Math.ceil(heatTicks / each)))
      if (have(actor.bot, fuel) < n && !(fuel === 'stick' || fuel.endsWith('_log') ? await makeItem(actor, fuel, n, goal, depth + 1) : false)) continue
      for (let k = 0; k < n; k++) { await B.hold(actor, fuel); const reply = await B.command(actor, '/survival fuel', 200); if (!/Heat reserve/.test(reply)) break }
      return true
    }
    return false
  }

  // ------------------------------------------------------------ tasks the commander can order
  const ORDERS = {
    async go ({ x, z, within = 1.5 }) { return B.walk(this, +x, +z, +within) },
    async go_to ({ name, within = 2 }) { const o = actors[name]; if (!o) return { ok: false, why: 'no such person ' + name }; const p = o.bot.entity.position; return B.walk(this, p.x, p.z, +within) },
    async make ({ item, count = 1 }) {
      const goal = { failed: new Set(), trace: [] }; const id = resolveItem(this.bot, item)
      if (!id.ok) return id
      const before = itemsOf(this.bot)
      const ok = await makeItem(this, id.item, Math.min(+count, 640), goal, 0)
      return { ok, item: id.item, have: have(this.bot, id.item), also: id.also, gained: B.gainsBetween(before, itemsOf(this.bot)), trace: goal.trace.slice(-25) }
    },
    async gather ({ what, count = 1, radius = 32, x, y, z }) {
      const bot = this.bot; const problems = []; const tried = new Set(); const total = {}
      if (x !== undefined) { const w = await B.work(this, new Vec3(+x, +y, +z)); if (w.ok) book.learnYield(w.block, w.gained); return w }
      let fails = 0
      for (let i = 0; i < Math.min(+count, 64) && fails < 6; i++) {
        if (this.order.abort) break
        const found = B.byReach(bot, B.findBlocks(bot, what, +radius, 32).filter(f => !tried.has(f.slice(0, 3).join(','))).map(f => new Vec3(f[0], f[1], f[2])).filter(p => !B.floods(bot, p))).map(p => [p.x, p.y, p.z])
        if (!found.length) { problems.push(`no more ${what} within ${radius} blocks`); break }
        const [fx, fy, fz] = found[0]; tried.add([fx, fy, fz].join(','))
        this.order.progress = `${what} ${i + 1}/${count} at ${fx},${fy},${fz}`
        const r = await B.work(this, new Vec3(fx, fy, fz))
        if (r.gained) for (const [k, v] of Object.entries(r.gained)) total[k] = (total[k] || 0) + v
        // Count down on the order itself, so that a paused gather taken up again goes on for what is left.
        if (r.ok) { book.learnYield(r.block, r.gained); this.order.args.count = Math.max(0, (+this.order.args.count || 1) - 1) }
        // One that cannot be reached or worked is passed over for the next nearest; it does not use up the count.
        else { problems.push(`${fx},${fy},${fz}: ${r.why || r.server || 'failed'}`); fails++; i-- }
      }
      return { ok: Object.keys(total).length > 0, gained: total, problems: problems.length ? problems.slice(-4) : undefined }
    },
    async dig ({ x, y, z }) { const w = await B.work(this, new Vec3(+x, +y, +z)); if (w.ok) book.learnYield(w.block, w.gained); return w },
    async craft ({ recipe, times = 1 }) {
      const before = itemsOf(this.bot); let made = 0
      for (let i = 0; i < Math.min(+times, 16); i++) {
        if (this.order.abort) break
        const reply = await B.command(this, `/survival craft ${recipe}`, 300)
        if (!B.started(reply)) return { ok: made > 0, made, server: B.firstLine(reply), hint: 'the make task finds a recipe and its ingredients by itself', gained: B.gainsBetween(before, itemsOf(this.bot)) }
        const end = await B.finishJob(this); if (!end.done) return { ok: false, made, why: end.why }
        made++
      }
      await sleep(300)
      return { ok: true, made, gained: B.gainsBetween(before, itemsOf(this.bot)) }
    },
    async drink ({ until }) { return drink(this, until === undefined ? this.policy.drinkUntil : +until) },
    async eat ({ item }) { return item ? eat(this, item) : feed(this, this.policy.eatUntil) },
    async feed ({ until }) { return feed(this, until === undefined ? this.policy.eatUntil : +until) },
    async place ({ item, x, y, z }) { const r = await B.place(this, +x, +y, +z, item); if (r.ok) stationAt(this, norm(item), 8); return r },
    async build ({ item, cells }) {
      const out = []
      for (const [x, y, z] of [...cells].sort((a, b) => a[1] - b[1])) { if (this.order.abort) break; const r = await B.place(this, x, y, z, item); out.push([x, y, z, r.ok ? 'ok' : r.why || 'now ' + r.now]) }
      return { ok: out.some(c => c[3] === 'ok'), cells: out }
    },
    async hunt ({ creature, seconds = 40 }) {
      const bot = this.bot; const end = Date.now() + Math.min(+seconds, 90) * 1000; let hits = 0; let target = null
      const pick = () => Object.values(bot.entities).filter(e => e.name === creature || e.name === norm(creature)).sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position))[0]
      try {
        while (Date.now() < end && !this.order.abort) {
          if (!target || !bot.entities[target.id]) { if (target) break; target = pick(); if (!target) return { ok: false, why: 'no ' + creature + ' in sight' } }
          const d = bot.entity.position.offset(0, 1.62, 0).distanceTo(target.position.offset(0, 0.3, 0))
          if (B.isWater(bot.blockAt(target.position.floored()))) { bot.clearControlStates(); await sleep(400); continue }
          await bot.lookAt(target.position.offset(0, 0.3, 0), true)
          if (d <= 3) { bot.setControlState('forward', false); bot.attack(target); hits++; await sleep(500) } else {
            const ahead = bot.blockAt(bot.entity.position.offset(Math.sin(-bot.entity.yaw), 0, -Math.cos(bot.entity.yaw)).floored())
            bot.setControlState('forward', !B.isWater(ahead)); bot.setControlState('jump', !!bot.entity.isCollidedHorizontally); await sleep(80)
          }
        }
      } finally { bot.clearControlStates() }
      await sleep(800)
      const before = itemsOf(bot); await B.pickup(this, 6)
      return { ok: target ? !bot.entities[target.id] : false, hits, killed: target ? !bot.entities[target.id] : false, picked: B.gainsBetween(before, itemsOf(bot)) }
    },
    async give ({ item, n = 1, to }) {
      const other = actors[to]; if (!other) return { ok: false, why: 'no such person ' + to }
      const bot = this.bot; const it = bot.inventory.items().find(i => i.name === norm(item))
      if (!it) return { ok: false, why: 'has no ' + item }
      const p = other.bot.entity.position; const went = await B.walk(this, p.x, p.z, 2)
      if (!went.arrived) return { ok: false, why: 'could not reach ' + to + ': ' + went.why }
      await bot.lookAt(other.bot.entity.position.offset(0, 1.2, 0), true)
      await bot.toss(it.type, null, Math.min(+n, it.count)); await sleep(1500)
      other.pickupSoon = Date.now()
      return { ok: true }
    },
    async hold ({ item }) { return B.hold(this, item) },
    async use ({ item, seconds = 1.8 }) {
      if (item) { const h = await B.hold(this, item); if (!h.ok) return h }
      this.bot.activateItem(); await sleep(Math.min(+seconds, 10) * 1000); this.bot.deactivateItem()
      return { ok: true, held: this.bot.heldItem ? this.bot.heldItem.name : null }
    },
    async use_on ({ x, y, z, item }) {
      if (item) { const h = await B.hold(this, item); if (!h.ok) return h }
      const p = new Vec3(+x, +y, +z); const b = this.bot.blockAt(p)
      if (!b) return { ok: false, why: 'nothing there' }
      await B.approach(this, p)
      try { await Promise.race([this.bot.activateBlock(b), sleep(4000)]) } catch (error) { return { ok: false, why: error.message } }
      await sleep(400); return { ok: true, block: b.name }
    },
    async act ({ action, target }) { const reply = await B.command(this, `/survival act ${action}${target ? ' ' + target : ''}`); return { ok: !/must|cannot|Need|require|fail|Already/i.test(B.firstLine(reply)), server: reply } },
    async command ({ line }) { const reply = await B.command(this, line); return { ok: true, server: reply } },
    async say ({ text }) { if (!text || String(text).startsWith('/')) return { ok: false, why: 'say takes plain words' }; this.bot.chat(String(text).slice(0, 250)); return { ok: true } },
    async follow ({ name, seconds = 30 }) {
      const end = Date.now() + Math.min(+seconds, 120) * 1000
      while (Date.now() < end && !this.order.abort) { const o = actors[name]; if (!o) break; const p = o.bot.entity.position; if (p.distanceTo(this.bot.entity.position) > 3) await B.walk(this, p.x, p.z, 2.5); await sleep(500) }
      return { ok: true, pos: pos(this.bot.entity.position) }
    },
    async wait ({ seconds = 10 }) { const end = Date.now() + Math.min(+seconds, 120) * 1000; while (Date.now() < end && !this.order.abort) await sleep(250); return { ok: true } },
    // self-care, also run by the actor itself
    async leave_water () { return { ok: await B.leaveWater(this) } },
    async flee ({ from }) {
      const bot = this.bot; const e = from && bot.entities[from]
      const away = e ? bot.entity.position.minus(e.position) : new Vec3(1, 0, 0)
      const len = Math.hypot(away.x, away.z) || 1
      return B.walk(this, bot.entity.position.x + away.x / len * 12, bot.entity.position.z + away.z / len * 12, 2)
    },
    async pickup ({ radius = 5 }) { const before = itemsOf(this.bot); await B.pickup(this, Math.min(+radius, 12)); return { ok: true, picked: B.gainsBetween(before, itemsOf(this.bot)) } },
    async go_home () { const h = this.policy.home; return h ? B.walk(this, h[0], h[1], 3) : { ok: false, why: 'no home set' } }
  }

  function resolveItem (bot, name) {
    const raw = norm(name); const names = bot.registry.itemsByName
    if (names[raw]) { const also = !raw.includes(':') && names[MOD + raw] ? MOD + raw : raw.startsWith(MOD) && names[raw.slice(MOD.length)] ? raw.slice(MOD.length) : undefined; return { ok: true, item: raw, also: also ? `${also} is a different item of the same name` : undefined } }
    if (names[MOD + raw]) return { ok: true, item: MOD + raw }
    const close = Object.keys(names).filter(n => n.includes(raw)).slice(0, 8)
    return { ok: false, why: `no item called ${name}` + (close.length ? '; did you mean ' + close.join(', ') : '') }
  }

  async function drink (actor, until) {
    const bot = actor.bot; const start = await B.readBody(actor)
    if (start.thirst >= until) return { ok: true, thirst: round(start.thirst), note: 'not thirsty' }
    let reply = ''; let sips = 0; let from = null
    const sip = async () => { reply = await B.command(actor, '/survival act drink', 300); const ok = !/Need|Already|cannot|Open|dead/i.test(B.firstLine(reply)); if (ok) sips++; return ok }
    // Safe water first: a carried pot.
    for (const pot of bot.inventory.items().filter(i => /pot$/.test(i.name))) {
      if (!(await B.hold(actor, pot.name)).ok) continue
      for (let i = 0; i < 8; i++) { if ((await B.readBody(actor)).thirst >= until || !(await sip())) break; from = pot.name }
      if (sips) break
    }
    // Then open water from dry footing. Water known to sicken waits until thirst is critical: dehydration kills first.
    // Water known to sicken is put off a little, never refused: thirst kills sooner than the sickness.
    const waryBelow = Math.max(CRITICAL + 5, actor.policy.drinkBelow - 15)
    const wary = actor.policy.water !== 'open' && team.water && start.thirst >= waryBelow
    if (!sips && wary) return { ok: false, why: `open water made people ill (${team.water}); putting it off until thirst is below ${waryBelow} (now ${round(start.thirst)})`, retryIn: 30 }
    if (!sips) {
      let s = await toShore(actor)
      // No water in sight: go back to the nearest place someone drank before.
      // No water in sight: go back to the nearest place someone drank before, else to water anyone has seen.
      const here = bot.entity.position; const by = p => Math.hypot(p[0] - here.x, p[2] - here.z)
      const places = [...team.shores.map(p => ({ p, drank: true })), ...team.wet.map(w => ({ p: w.p, drank: false }))].sort((a, b) => (by(a.p) + (a.drank ? 0 : 16)) - (by(b.p) + (b.drank ? 0 : 16)))
      for (const place of places.slice(0, 3)) {
        if (s.ok || (actor.order && actor.order.abort)) break
        await B.walk(actor, place.p[0] + 0.5, place.p[2] + 0.5, place.drank ? 1 : 4)
        s = await toShore(actor)
      }
      if (!s.ok) return { ok: false, why: s.why, retryIn: 60 }
      if (bot.heldItem && /pot$/.test(bot.heldItem.name)) bot.setQuickBarSlot((bot.quickBar + 1) % 9)
      for (let i = 0; i < 14; i++) { if (actor.order && actor.order.abort) break; if ((await B.readBody(actor)).thirst >= until || !(await sip())) break; from = 'open water at ' + s.at.join(',') }
      if (sips && !team.shores.some(k => k.join() === s.at.join())) { team.shores.push(s.at); if (team.shores.length > 8) team.shores.shift() }
    }
    const b = await B.readBody(actor)
    if (sips && /open water/.test(from || '')) watchSickness(actor, 'open water', start.conditions)
    return { ok: b.thirst > start.thirst, sips, from, thirst: [round(start.thirst), round(b.thirst)], server: B.firstLine(reply), conditions: b.conditions }
  }
  const RAW = /^(beef|porkchop|mutton|chicken|rabbit|cod|salmon|tropical_fish|pufferfish|rotten_flesh|spider_eye|poisonous_potato)$/
  const EDIBLE = /berries|apple|carrot|potato|bread|stew|soup|melon_slice|cooked_|maize|beetroot$|cookie|pie|honey_bottle|dried_kelp/
  const isFood = (bot, name) => !!((bot.registry.foodsByName || {})[name] || EDIBLE.test(name) || RAW.test(name))
  /** Carried foods, best first: the commander's preferences, then ordinary food, then raw flesh, then anything that
   * made someone ill (only when hunger is critical). */
  function foodsHeld (actor, hunger = 100) {
    const bot = actor.bot; const held = [...new Set(bot.inventory.items().map(i => i.name))].filter(n => isFood(bot, n))
    const pref = (actor.policy.foods || []).map(norm)
    const score = n => (pref.includes(n) ? pref.indexOf(n) - 100 : 0) + (RAW.test(n) ? 50 : 0) + (team.avoid.has(n) ? 200 : 0)
    return held.filter(n => (!team.avoid.has(n) && !RAW.test(n)) || hunger < CRITICAL).sort((a, b) => score(a) - score(b))
  }
  async function eat (actor, item, until) {
    const bot = actor.bot; const start = await B.readBody(actor); let eaten = []
    for (let n = 0; n < 6; n++) {
      const now = await B.readBody(actor)
      if (until !== undefined && now.hunger >= until) break
      const food = item ? norm(item) : foodsHeld(actor, now.hunger)[0]
      if (!food) break
      const h = await B.hold(actor, food); if (!h.ok) return { ok: eaten.length > 0, eaten, why: h.why }
      const reply = await B.command(actor, '/survival act eat', 200)
      if (!/started/i.test(reply)) { if (!eaten.length) return { ok: false, server: B.firstLine(reply) }; break }
      bot.activateItem(); await sleep(2200); bot.deactivateItem(); await sleep(400)
      watchSickness(actor, food, now.conditions); eaten.push(food)
      if (item || until === undefined) break
    }
    const after = await B.readBody(actor)
    if (!eaten.length) return { ok: false, why: 'carries nothing to eat' }
    return { ok: after.hunger > start.hunger, eaten, hunger: [round(start.hunger), round(after.hunger)], conditions: after.conditions }
  }
  // What a hungry person looks for, cheapest first; the planner finds where and how (gather, harvest, craft, cook).
  const FORAGE = ['apple', 'carrot', 'potato', 'melon_slice', 'sweet_berries', MOD + 'blackberries', 'beetroot', 'bread', 'mushroom_stew', MOD + 'maize', 'cooked_beef', 'cooked_porkchop', 'cooked_mutton', 'cooked_chicken', 'cooked_cod', 'cooked_salmon']
  const RAW_MEAT = PREY_TABLE.map(([, out]) => Object.keys(out)[0])
  async function season (actor) {
    if (team.season && Date.now() - team.season.at < 180000) return team.season.value
    const reply = await B.command(actor, '/survival calendar', 300)
    const i = ['Spring', 'Summer', 'Autumn', 'Winter'].findIndex(s => reply.includes(s))
    if (i >= 0) team.season = { at: Date.now(), value: i }
    return i >= 0 ? i : undefined
  }
  /** The hunger drive: eat what is carried; with nothing carried, find or make food (in season, near, remembered,
   * or by looking further afield), and when hunger is critical and there is still none, catch an animal. */
  async function feed (actor, until) {
    const bot = actor.bot; const b = await B.readBody(actor)
    if (foodsHeld(actor, b.hunger).length) return eat(actor, undefined, until)
    await season(actor)
    const goal = { failed: new Set(), trace: [] }; const went = []
    const tryHere = async () => {
      const view = worldView(actor, true)
      // Raw meat is a meal only for the starving: cooked first wherever a kiln can be had.
      const menu = (await B.readBody(actor)).hunger < CRITICAL ? [...FORAGE, ...RAW_MEAT] : FORAGE
      const options = menu.map(f => ({ f, c: (rank(book, f, 1, view, goal.failed)[0] || { cost: Infinity }).cost })).filter(o => o.c < Infinity).sort((x, y) => x.c - y.c)
      for (const { f } of options.slice(0, 3)) {
        if (actor.order && actor.order.abort) return false
        if (await makeItem(actor, f, 2, goal, 1)) return true
      }
      return foodsHeld(actor, b.hunger).length > 0
    }
    let found = await tryHere()
    // Food someone has seen before, in season: go there and look again.
    for (let leg = 0; !found && leg < 2; leg++) {
      const spot = rememberedFood(actor, went); if (!spot) break
      went.push(spot.key); goal.trace.push(`going to ${spot.block} seen at ${spot.p.join(',')}`)
      await B.walk(actor, spot.p[0] + 0.5, spot.p[2] + 0.5, 2); found = await tryHere()
    }
    // Nothing known: look further afield, a leg at a time.
    for (let leg = 0; !found && leg < 3; leg++) {
      if (actor.order && actor.order.abort) break
      const a = (actor.exploreHeading = ((actor.exploreHeading ?? Math.random() * 6.28) + 2.1) % 6.28)
      const here = bot.entity.position
      goal.trace.push(`looking for food towards ${Math.round(here.x + Math.cos(a) * 32)},${Math.round(here.z + Math.sin(a) * 32)}`)
      await B.walk(actor, here.x + Math.cos(a) * 32, here.z + Math.sin(a) * 32, 3); found = await tryHere()
    }
    record({ bot: actor.name, feedTrace: goal.trace })
    if (!found) return { ok: false, why: 'found no food', trace: goal.trace.slice(-8), retryIn: 60 }
    const meal = await eat(actor, undefined, until)
    return { ...meal, found: goal.trace.filter(t => /have|hunting|going/.test(t)).slice(-3) }
  }
  /** Food sources anyone has seen, in season and nearest first, that this search has not already been to. */
  function rememberedFood (actor, skip) {
    const here = actor.bot.entity.position; const s = team.season ? team.season.value : undefined
    const spots = []
    for (const [key, { block, p }] of team.food) {
      const route = [...book.routes.values()].flat().find(r => r.kind === 'gather' && r.block === block)
      if (route && route.seasons && s !== undefined && !route.seasons.includes(s)) continue
      if (skip.includes(key)) continue
      spots.push({ block, p, key, d: Math.hypot(p[0] - here.x, p[2] - here.z) })
    }
    return spots.sort((a, b) => a.d - b.d)[0]
  }
  /** Note anything that left someone sick within half a minute of taking it, so that nobody takes it again unbidden. */
  function watchSickness (actor, what, before) {
    const was = JSON.stringify(before || '')
    setTimeout(async () => {
      try {
        const b = await B.readBody(actor); const now = JSON.stringify(b.conditions || '')
        if (now !== was && /POISON|SICK|ILL|NAUSEA|DIARRH|INFECT/i.test(now)) {
          if (what === 'open water') { team.water = 'open water sickened ' + actor.name; note(actor, `fell ill (${now.slice(0, 80)}) after drinking open water`) }
          else { team.avoid.set(what, actor.name); note(actor, `fell ill (${now.slice(0, 80)}) after eating ${what}; nobody will eat it again unless ordered to`) }
        }
      } catch {}
    }, 25000)
  }

  // ------------------------------------------------------------ running orders
  let orderSeq = 0
  function startOrder (actor, order) {
    const fn = ORDERS[order.task]
    if (!fn) return { ok: false, why: 'unknown task ' + order.task + '; tasks: ' + Object.keys(ORDERS).join(', ') }
    if (actor.dead) return { ok: false, why: actor.name + ' is dead' }
    if (actor.gone) return { ok: false, why: actor.name + ' is disconnected' }
    const run = { task: order.task, args: order.args || {}, started: Date.now(), progress: null, abort: null, n: ++orderSeq, by: order.by || 'commander', care: CARE.has(order.task) && order.by === 'self', rank: order.rank || 0, drive: order.drive, resume: false }
    actor.order = run
    record({ bot: actor.name, order: run.task, args: run.args, by: run.by })
    ;(async () => {
      let result
      try { result = await fn.call(actor, run.args) } catch (error) { result = { ok: false, why: String(error && (error.message || error)) } }
      if (!result || typeof result !== 'object') result = { ok: !!result }
      if (run.abort) { result.interrupted = run.abort; if (result.ok === undefined) result.ok = false }
      try { actor.bot.clearControlStates() } catch {}
      const entry = { n: run.n, task: run.task, args: run.args, by: run.by, seconds: Math.round((Date.now() - run.started) / 1000), result }
      if (actor.order === run) actor.order = null
      actor.idleSince = Date.now()
      record({ bot: actor.name, done: run.task, by: run.by, result })
      if (run.resume) {
        // Paused, not finished: put it back at the head of the queue to take up again.
        actor.queue.unshift({ task: run.task, args: run.args, by: run.by })
        actor.history.push({ ...entry, paused: run.abort })
      } else {
        actor.history.push(entry)
        if (run.by !== 'self') actor.last = entry
        if (result.ok === false && run.by === 'commander' && actor.policy.onFail === 'stop' && actor.queue.length) { entry.droppedQueue = actor.queue.length; actor.queue = actor.queue.filter(q => q.by === 'self') }
      }
      if (run.care && run.drive) actor.retryAt[run.drive.replace('_critical', '')] = Date.now() + 1000 * ((result.ok === false && result.retryIn) || (result.ok === false ? 30 : 0))
      if (actor.history.length > 12) actor.history.splice(0, actor.history.length - 12)
      wake()
    })()
    return { ok: true, started: run.task }
  }
  /** Interrupt the running order for self-care; a commander's or standing order is taken up again afterwards. */
  function pause (actor, reason) {
    const run = actor.order
    if (!run) return
    run.abort = 'paused: ' + reason
    run.resume = !run.care
  }
  /** Ask for a drive's care. It waits in its own slot ahead of the queue; a stronger drive displaces a weaker one,
   * whether it is waiting or already running. */
  function selfCare (actor, drive, task, args, why) {
    const level = RANK[drive] || 1
    const running = actor.order && actor.order.care ? actor.order.rank || 1 : 0
    const waiting = actor.pendingCare ? actor.pendingCare.rank || 1 : 0
    if (level <= running || level <= waiting) return false
    actor.pendingCare = { task, args, by: 'self', rank: level, drive }
    record({ bot: actor.name, self: task, drive, why })
    pause(actor, why)
    return true
  }
  // ------------------------------------------------------------ the mind's loop
  async function live (actor) {
    const bot = actor.bot
    let lastBody = 0; const warned = {}; let lastHurtAt = 0; let prevHealth = bot.health
    actor.retryAt = {}
    // Breath: whenever the body is in water it swims up, ten times a second, whatever else it is doing; the
    // swim drive then takes it to dry land. Nothing waits for an order to let go.
    setInterval(() => {
      if (actor.dead || actor.gone || !bot.entity) return
      const w = B.water(bot)
      if (w.inWater) { bot.setControlState('jump', true); actor.swimming = true } else if (actor.swimming) { actor.swimming = false; bot.setControlState('jump', false) }
    }, 100)
    // Danger: whatever strikes the body is moved away from at once.
    bot.on('health', () => {
      const was = prevHealth; prevHealth = bot.health
      if (actor.dead || bot.health >= was - 0.5) return
      const threat = Object.values(bot.entities).filter(e => e !== bot.entity && e.type !== 'player' && e.name !== 'item' && e.position.distanceTo(bot.entity.position) < 7).sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position))[0]
      const feet = bot.blockAt(bot.entity.position.floored())
      const burning = feet && /fire|lava|campfire|magma/.test(feet.name)
      if ((threat && !(actor.order && actor.order.task === 'hunt')) || burning) {
        if (Date.now() - lastHurtAt < 3000) return
        lastHurtAt = Date.now()
        const what = threat ? threat.name : feet.name
        alert(actor.name, `hurt by ${what} (health ${round(was)} -> ${round(bot.health)}); getting away`)
        selfCare(actor, 'flee', 'flee', { from: threat ? threat.id : undefined }, 'hurt by ' + what)
      }
    })
    for (;;) {
      await sleep(300)
      if (actor.dead || actor.gone || !bot.entity) continue
      if (!actor.syncedAt || Date.now() - actor.syncedAt > 20000) await B.resync(actor)
      if (actor.pendingCare && !actor.order) { const care = actor.pendingCare; actor.pendingCare = null; startOrder(actor, care); continue }
      const w = B.water(bot)
      if (w.inWater) {
        if (!actor.inWaterSince) actor.inWaterSince = Date.now()
        if (Date.now() - actor.inWaterSince > 800 && !(actor.order && actor.order.task === 'leave_water') && !(actor.pendingCare && actor.pendingCare.task === 'leave_water')) {
          if (!warned.water || Date.now() - warned.water > 10000) { warned.water = Date.now(); alert(actor.name, w.headUnder ? `head under water (air ${w.air === null ? '?' : w.air + '%'}); swimming out` : 'in water; swimming out') }
          if (actor.order && actor.order.care) actor.order.abort = 'swam out of the water first'
          selfCare(actor, 'swim', 'leave_water', {}, 'in water')
        }
        continue
      }
      actor.inWaterSince = 0
      // Thirst and hunger, read every few seconds. Critical outranks ordinary, and both outrank every order.
      if (Date.now() - lastBody > 4000) {
        lastBody = Date.now()
        let b
        try { b = await B.readBody(actor); warned.out = false } catch (error) { if (error.code === 'EUNCONSCIOUS' && !warned.out) { warned.out = true; alert(actor.name, 'unconscious') } continue }
        // Two readings running with the server holding the body somewhere else: the client has lost its place. It goes
        // back to where the body is, and whatever it was doing starts again from there.
        if (actor.apart >= 2) {
          const gap = round(actor.gap); const moved = B.comeBack(actor)
          if (moved) {
            record({ bot: actor.name, lostPlace: true, gap, client: moved.was, server: moved.now, doing: actor.order ? actor.order.task : null })
            alert(actor.name, `lost its place: the server had it ${gap} blocks from where it thought it stood; back at ${moved.now.join(', ')}`)
            pause(actor, 'lost its place')
            continue
          }
        }
        for (const need of ['hunger', 'thirst']) {
          const v = b[need]; const level = v < 10 ? 'critical' : v < 25 ? 'low' : null
          if (level && warned[need] !== level) alert(actor.name, `${need} ${level} (${round(v)})`)
          warned[need] = level
        }
        const conditions = JSON.stringify(b.conditions || '')
        if (warned.conditions !== undefined && conditions !== warned.conditions && !/^"?Stable"?$/.test(conditions)) alert(actor.name, 'condition: ' + conditions.slice(0, 200))
        warned.conditions = conditions
        const p = actor.policy; const ready = drive => Date.now() >= (actor.retryAt[drive] || 0)
        const needs = []
        if (b.thirst < p.drinkBelow && ready('drink')) needs.push({ drive: b.thirst < CRITICAL ? 'drink_critical' : 'drink', task: 'drink', args: { until: p.drinkUntil }, v: b.thirst })
        if (b.hunger < p.eatBelow && ready('eat')) needs.push({ drive: b.hunger < CRITICAL ? 'eat_critical' : 'eat', task: 'feed', args: { until: p.eatUntil }, v: b.hunger })
        needs.sort((x, y) => (RANK[y.drive] - RANK[x.drive]) || (x.v - y.v)) // the stronger drive, then the lower need
        for (const n of needs) if (selfCare(actor, n.drive, n.task, n.args, `${n.task === 'drink' ? 'thirst' : 'hunger'} ${round(n.v)}`)) break
        if (needs.length) continue
      }
      if (actor.pickupSoon && !actor.order) { actor.pickupSoon = 0; startOrder(actor, { task: 'pickup', args: { radius: 4 }, by: 'self', rank: 1 }); continue }
      // Nothing running: the next queued step, else the standing job, else home.
      if (!actor.order && !actor.pendingCare) {
        if (actor.queue.length) { startOrder(actor, actor.queue.shift()); continue }
        if (Date.now() - actor.idleSince < 1500) continue
        if (actor.policy.idle) { startOrder(actor, { ...actor.policy.idle, by: 'standing' }); continue }
        const h = actor.policy.home
        if (h && Math.hypot(bot.entity.position.x - h[0], bot.entity.position.z - h[1]) > actor.policy.roam) startOrder(actor, { task: 'go_home', args: {}, by: 'self', rank: 1 })
      }
    }
  }

  // ------------------------------------------------------------ the commander's handles
  function order (actor, task, args, then) {
    // A new order replaces the old one; self-care that is under way finishes first.
    if (actor.order && !actor.order.care) { actor.order.abort = 'replaced by a new order'; actor.order.resume = false }
    actor.queue = (then || []).map(o => ({ task: o.task, args: o.args || {}, by: 'commander' }))
    if (!ORDERS[task]) return { ok: false, why: 'unknown task ' + task }
    actor.queue.unshift({ task, args: args || {}, by: 'commander' })
    return { ok: true, queued: actor.queue.length, note: actor.order ? 'starts as soon as the current step lets go' : undefined }
  }
  function setPolicy (actor, changes) {
    const p = { ...actor.policy }
    for (let [k, v] of Object.entries(changes || {})) {
      if (!(k in DEFAULT_POLICY)) throw new Error('no policy ' + k + '; policies: ' + Object.keys(DEFAULT_POLICY).join(', ') + ' (drinking, eating, swimming out of water and getting away from harm are not optional)')
      if (LIMITS[k]) { const [lo, hi] = LIMITS[k]; v = Math.max(lo, Math.min(hi, Number(v))) }
      if (k === 'water' && !['safe-first', 'open'].includes(v)) throw new Error("water is 'safe-first' or 'open'")
      p[k] = v
    }
    actor.policy = p
    return p
  }
  function enroll (actor) { actor.policy = { ...DEFAULT_POLICY }; actor.queue = []; actor.history = []; actor.idleSince = Date.now(); live(actor) }
  function view (actor) {
    const o = actor.order
    return {
      doing: o ? { task: o.task, args: o.args, by: o.by, for: Math.round((Date.now() - o.started) / 1000) + 's', progress: o.progress || undefined } : 'idle for ' + Math.round((Date.now() - actor.idleSince) / 1000) + 's',
      queued: actor.queue.length ? actor.queue.map(q => q.task + (q.by === 'commander' ? '' : ` (${q.by})`)) : undefined,
      selfCare: actor.history.filter(h => h.by !== 'commander').slice(-3).map(h => `${h.task}${h.paused ? ' paused' : ''}: ${h.result && h.result.ok === false ? 'failed ' + (h.result.why || h.result.server || '') : 'ok'}`.slice(0, 160))
    }
  }
  return { ORDERS, DEFAULT_POLICY, team, worldView, enroll, order, setPolicy, view, startOrder }
}

module.exports = { createCrew, DEFAULT_POLICY }
