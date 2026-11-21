// The actors' book of every way the world lets an item come into someone's hands, and the planner that picks a
// way an actor can actually take. Routes for one item compete; none is assumed to be the way.
//
//   craft    /survival craft <recipe>: a grid recipe; each slot takes any of its alternatives (tags expand to
//            many items); a 3x3 shape, or a shapeless recipe of five or more, needs a crafting table within 4.
//   process  /survival produce <recipe> at a station (kiln, bloomery, forge, anvil, loom) within 6: input in the
//            main hand, a second input in the offhand, fuel first if heated, then /survival collect.
//   hand     /techtree make <function>: tech-tree hand work, once the society has discovered it; inputs in the
//            main inventory, tools carried, some need a station, water or an animal nearby.
//   gather   the world itself: hand-gathered ground resources, fruit, mined rock and ore, broken blocks.
//
// Ids follow mineflayer's names: vanilla items bare ("stick"), the mod's with their namespace.
const fs = require('fs')
const path = require('path')
const MOD = 'spawn_9b5fbb9:'
const TECHTREE = path.join(__dirname, '..', '..', 'src', 'techtree', 'generated', 'data', 'spawn_9b5fbb9', 'techtree', 'techtree.json')
/** "minecraft:stick" and "stick" are one item; the mod's keep their namespace. */
// A bare name that is no vanilla item but is one of the mod's ("shell_beads") means the mod's.
let NAMES = null
const norm = id => {
  const s = String(id || '').replace(/^minecraft:/, '').replace(/^mod:/, MOD)
  return NAMES && !s.includes(':') && !NAMES[s] && NAMES[MOD + s] ? MOD + s : s
}

// What the world gives by hand, from the mod's gathering rules (GroundResourceBlock.completeGather) and the
// vanilla blocks people start with. Yields are a first guess: the book learns what really came of each block.
const SOURCES = [
  [MOD + 'wild_plants', { [MOD + 'plant_fiber']: 2 }],
  [MOD + 'loose_stones', { [MOD + 'loose_stone']: 3 }],
  [MOD + 'fallen_sticks', { stick: 2 }],
  // Seasons (0 spring, 1 summer, 2 autumn, 3 winter) as GroundResourceBlock.harvestable and FruitTreeBlock allow.
  [MOD + 'wild_flax', { [MOD + 'flax_fiber']: 2, [MOD + 'flax_seeds']: 2 }, [0, 1, 2]],
  [MOD + 'wild_yarrow', { [MOD + 'yarrow_leaves']: 2, [MOD + 'yarrow_seeds']: 2 }, [0, 1, 2]],
  [MOD + 'wild_bramble', { [MOD + 'blackberries']: 3, [MOD + 'bramble_seeds']: 1 }, [1, 2]],
  [MOD + 'wild_rice', { [MOD + 'rice_grain']: 2, [MOD + 'rice_seeds']: 2 }, [1, 2]],
  [MOD + 'wild_maize', { [MOD + 'maize']: 1, [MOD + 'maize_seeds']: 2 }, [1, 2]],
  [MOD + 'wild_wheat', { wheat: 1, wheat_seeds: 2 }, [0, 2]],
  [MOD + 'wild_carrot', { carrot: 2 }],
  [MOD + 'wild_potato', { potato: 2 }],
  [MOD + 'wild_beetroot', { beetroot: 1, beetroot_seeds: 2 }],
  [MOD + 'wild_pumpkin', { pumpkin_seeds: 2 }, [1, 2]],
  [MOD + 'wild_melon', { melon_slice: 2, melon_seeds: 1 }, [1, 2]],
  [MOD + 'fruit_tree', { apple: 6 }, [1, 2]],
  // Luxuries: what a shore gives, the dye and medicine plants of particular climates, and the stones worth working.
  [MOD + 'seashells', { [MOD + 'seashell']: 3 }],
  [MOD + 'murex_shells', { [MOD + 'murex']: 4 }],
  [MOD + 'pearl_oysters', { [MOD + 'pearl']: 1, [MOD + 'seashell']: 2 }],
  [MOD + 'wild_woad', { [MOD + 'woad_leaves']: 4 }, [0, 1, 2]],
  [MOD + 'wild_indigo', { [MOD + 'indigo_leaves']: 4 }],
  [MOD + 'wild_madder', { [MOD + 'madder_root']: 2 }, [0, 1, 2]],
  [MOD + 'wild_weld', { [MOD + 'weld']: 3 }, [0, 1, 2]],
  [MOD + 'wild_meadowsweet', { [MOD + 'meadowsweet']: 2 }, [0, 1, 2]],
  [MOD + 'wild_sphagnum', { [MOD + 'sphagnum']: 3 }],
  [MOD + 'wild_cinchona', { [MOD + 'cinchona_bark']: 2 }],
  [MOD + 'amber_sand', { [MOD + 'amber']: 1 }],
  [MOD + 'topaz_ore', { [MOD + 'topaz']: 1 }],
  ['lapis_ore', { lapis_lazuli: 4 }], ['deepslate_lapis_ore', { lapis_lazuli: 4 }],
  ['clay', { clay_ball: 400 }],
  ['sand', { sand: 1 }], ['red_sand', { red_sand: 1 }], ['gravel', { gravel: 1 }], ['dirt', { dirt: 1 }], ['grass_block', { dirt: 1 }],
  ['bamboo', { bamboo: 1 }], ['sugar_cane', { sugar_cane: 1 }], ['red_mushroom', { red_mushroom: 1 }], ['brown_mushroom', { brown_mushroom: 1 }]
]
// What an animal killed by hand leaves to eat (vanilla drops; the book learns what really came of each kill). A kill with
// a hunting weapon by a society that knows butchery leaves a carcass instead, which is not counted here.
const PREY = [['pig', { porkchop: 2 }], ['cow', { beef: 2 }], ['sheep', { mutton: 1 }], ['chicken', { chicken: 1 }], ['rabbit', { rabbit: 1 }]]
const PICKAXES = ['wooden_pickaxe', 'stone_pickaxe', 'iron_pickaxe', 'golden_pickaxe', 'diamond_pickaxe', 'netherite_pickaxe', MOD + 'knapped_stone_pickaxe', MOD + 'obsidian_pickaxe']

class Book {
  constructor (registry) {
    this.registry = registry // mineflayer's merged item and block tables
    NAMES = registry.itemsByName
    this.routes = new Map() // item -> [route]
    this.yields = new Map() // block -> { item: count } learned from real work
    this.known = new Set() // tech-tree functions the society is known to have discovered
    this.unknown = new Set() // functions the server said are not yet discovered
    this.loaded = { craft: 0, process: 0, hand: 0, gather: 0 }
    for (const [block, out, seasons] of SOURCES) this.addSource(block, out, 'seeded', seasons)
    for (const [creature, out] of PREY) this.add({ kind: 'hunt', id: 'hunt:' + creature, creature, out })
  }
  add (route) {
    for (const item of Object.keys(route.out)) {
      if (!this.routes.has(item)) this.routes.set(item, [])
      const list = this.routes.get(item)
      if (!list.some(r => r.kind === route.kind && r.id === route.id)) list.push(route)
    }
    this.loaded[route.kind] = (this.loaded[route.kind] || 0) + 1
  }
  addSource (block, out, how, seasons) {
    this.add({ kind: 'gather', id: 'gather:' + block, block, out, how, seasons, tool: /_ore$|^stone$|deepslate|granite|diorite|andesite|limestone|sandstone|marble/.test(block) ? PICKAXES : null })
  }
  /** What one block of a kind really gave: replaces the guessed yield and adds the route if it was missing. */
  learnYield (block, gained) {
    if (!block || !gained || !Object.keys(gained).length) return
    this.yields.set(block, gained)
    const existing = [...this.routes.values()].flat().find(r => r.kind === 'gather' && r.block === block)
    if (existing) existing.out = { ...gained }
    else this.addSource(block, gained, 'learned')
    for (const item of Object.keys(gained)) { const list = this.routes.get(item) || []; if (existing && !list.includes(existing)) list.push(existing); this.routes.set(item, list) }
  }
  /** What one kill of a creature really left to eat. */
  learnHunt (creature, gained) {
    const food = Object.fromEntries(Object.entries(gained || {}).filter(([k]) => PREY.some(([, out]) => k in out)))
    if (!Object.keys(food).length) return
    const route = (this.routes.get(Object.keys(food)[0]) || []).find(r => r.kind === 'hunt' && r.creature === creature)
    if (route) route.out = food
  }
  /** Every route to an item. An item that is also a block (a log, sand, dirt) can always be had by taking that block. */
  routesFor (item) {
    const id = norm(item); const list = this.routes.get(id) || []
    if (!list.some(r => r.kind === 'gather' && r.block === id)) {
      const block = this.registry.blocksByName && this.registry.blocksByName[id]
      // A workstation is not had by pulling up one that stands: that only moves it, and takes it from whoever used it.
      if (block && !/^(air|water|lava|bedrock|barrier)$|_air$|portal|command_block|structure|spawner|infested|crafting_table|kiln|forge|bloomery|anvil|loom|furnace|chest|barrel|trough/.test(id) && block.diggable !== false) {
        const route = { kind: 'gather', id: 'gather:' + id, block: id, out: { [id]: 1 }, how: 'block', tool: /pickaxe/.test(String(block.material || '')) ? PICKAXES : null }
        list.push(route); this.routes.set(id, list)
      }
    }
    return list
  }
  /** Every item id the tag stands for, from a tech-tree resource's values (which may name vanilla tags). */
  expand (value) {
    const names = this.registry.itemsArray.map(i => i.name)
    if (!value.startsWith('#')) return [norm(value)]
    const tag = norm(value.slice(1))
    const pattern = {
      logs: /_(log|wood|stem|hyphae)$/, logs_that_burn: /_(log|wood)$/, planks: /_planks$/, wooden_slabs: /_slab$/, saplings: /_sapling$/, leaves: /_leaves$/, wool: /_wool$/,
      sand: /^(red_)?sand$/, coals: /^(coal|charcoal)$/, stone_crafting_materials: /^(cobblestone|blackstone|cobbled_deepslate)$/, dirt: /^(dirt|coarse_dirt|rooted_dirt|mud|grass_block|podzol|mycelium|moss_block)$/,
      flowers: /(dandelion|poppy|orchid|allium|azure|tulip|daisy|cornflower|lily|rose|peony|lilac|sunflower)/, fishes: /^(cod|salmon|tropical_fish|pufferfish)$/, meat: /^(beef|porkchop|mutton|chicken|rabbit)$/
    }[tag.replace(/^c:/, '')]
    return pattern ? names.filter(n => pattern.test(n)) : names.filter(n => n.endsWith(tag.split('/').pop()))
  }
  /** Read every grid and station recipe from the server's own planning pages, through one actor's eyes. */
  async loadServer (ask) {
    let offset = 0
    for (let guard = 0; guard < 400 && offset >= 0; guard++) {
      const page = await ask(`/survival planning ${offset}`, v => Array.isArray(v.craftingRecipes) && v.offset === offset)
      for (const r of page.craftingRecipes) {
        const slots = r.ingredientSlots.map(s => s.alternatives.map(norm))
        // A slot that names more than eight items is paged: fetch the rest so no way of filling it is missed.
        let next = r.nextAlternativeOffset
        for (let more = 0; next > 0 && more < 12; more++) {
          const info = await ask(`/survival recipeinfo ${r.recipe} ${next}`, v => v.recipe === r.recipe && v.alternativeOffset === next)
          if (!info.ingredientSlots) break
          info.ingredientSlots.forEach((s, i) => { if (slots[i]) for (const a of s.alternatives) if (!slots[i].includes(norm(a))) slots[i].push(norm(a)) })
          next = info.nextAlternativeOffset
        }
        if (!r.permitted) continue
        this.add({ kind: 'craft', id: r.recipe, out: { [norm(r.output)]: r.outputCount || 1 }, table: !!r.craftingTableRequired, slots })
      }
      for (const r of page.processRecipes) {
        this.add({ kind: 'process', id: r.recipe, out: { [norm(r.output)]: r.outputCount || 1 }, station: r.stationRequired || (r.forgeRequired ? 'forge' : 'kiln'), input: norm(r.input), inputCount: r.inputCount, second: r.secondInput ? norm(r.secondInput) : '', secondCount: r.secondCount || 0, heated: !!r.heated, manual: !!r.manual, safeWater: !!r.safeWaterRequired, permitted: r.skillAndCapacityPermit !== false, minimumSkill: r.minimumSkill, skill: r.skill })
      }
      offset = page.nextOffset
    }
  }
  /** The tech tree's hand functions, whose inputs are resource tags or items and whose means are tools, stations or water. */
  loadTechtree (file = TECHTREE) {
    if (!fs.existsSync(file)) return
    const tree = JSON.parse(fs.readFileSync(file, 'utf8'))
    const resources = new Map(tree.resources.map(r => [r.id, r]))
    const items = new Map(tree.items.map(i => [i.id, i]))
    const has = name => !!this.registry.itemsByName[name]
    const itemId = id => has(MOD + id) ? MOD + id : has(id) ? id : MOD + id
    const resolve = ref => {
      const [kind, id] = ref.split(':')
      if (kind === 'item') return [itemId(id)]
      if (kind === 'tag') { const r = resources.get(id); const values = r && r.tagValues ? r.tagValues.flatMap(v => this.expand(v)) : []; const own = items.get(id) ? [itemId(id)] : []; return [...new Set([...own, ...values])] }
      return null
    }
    for (const f of tree.functions) {
      if (f.mode !== 'hand') continue
      const inputs = []; const needs = []; let usable = true
      for (const i of f.inputs) {
        const alts = resolve(i.ref)
        if (alts) { if (!alts.length) usable = false; inputs.push({ alts, count: i.count || 1 }) } else needs.push(i.ref)
      }
      const tools = []; const stations = []
      for (const m of f.means) {
        const [kind, id] = m.ref.split(':')
        if (kind === 'station') stations.push(id)
        else if (kind === 'item' || kind === 'tag') tools.push(resolve(m.ref))
        else needs.push(m.ref)
      }
      const out = {}
      for (const o of f.outputs) { const alts = resolve(o.ref); if (alts && alts[0]) out[alts[0]] = o.count || 1 }
      if (!usable || !Object.keys(out).length) continue
      this.add({ kind: 'hand', id: f.id, name: f.name, out, inputs, tools, stations, needs, ticks: f.ticks || 100 })
    }
  }
  /** The loaded routes as plain data, to keep between runs of the same mod build. */
  save (file, key) { fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, JSON.stringify({ key, loaded: this.loaded, routes: [...new Set([...this.routes.values()].flat())].filter(r => r.kind === 'craft' || r.kind === 'process') })) }
  restore (file, key) {
    if (!fs.existsSync(file)) return false
    const data = JSON.parse(fs.readFileSync(file, 'utf8'))
    if (data.key !== key) return false
    for (const r of data.routes) this.add(r)
    return true
  }
  summary () { return { ...this.loaded, items: this.routes.size, learnedYields: this.yields.size, discovered: [...this.known] } }
}

/**
 * Rank the routes to `count` of `item` for one actor, cheapest first. `world` answers what is at hand:
 * have(item), nearest(blockName) -> distance or null, stationNear(id) -> distance or null, waterNear() -> bool,
 * creatureNear(name) -> distance to the nearest one alive or last seen, or null.
 * A route whose cost is Infinity cannot be taken from here and now, and its reason says why.
 */
function rank (book, item, count, world, failed = new Set()) {
  const memo = new Map()
  const cost = (it, n, depth, stack) => {
    if (world.have(it) >= n) return 0
    if (depth > 4 || stack.includes(it)) return Infinity
    // One estimate per item, whatever the amount: the ranking needs an order, not a price.
    const key = it + '@' + depth
    if (memo.has(key)) return memo.get(key)
    memo.set(key, Infinity) // cycles count as impossible while they are being worked out
    let best = Infinity
    for (const r of book.routesFor(it)) if (!failed.has(r.kind + ':' + r.id)) best = Math.min(best, routeCost(r, it, n - world.have(it), depth + 1, [...stack, it]).cost)
    memo.set(key, best)
    return best
  }
  const cheapest = (alts, n, depth, stack) => {
    // Something already carried wins outright; otherwise weigh the alternatives, stopping at the first that is cheap.
    const held = alts.find(a => world.have(a) >= n)
    if (held) return { alt: held, cost: 0 }
    let best = { alt: alts[0], cost: Infinity }
    for (const a of alts) { const c = cost(a, n, depth, stack); if (c < best.cost) best = { alt: a, cost: c }; if (best.cost < 6) break }
    return best
  }
  const routeCost = (r, it, need, depth, stack) => {
    const made = r.out[it] || 1; const times = Math.max(1, Math.ceil(need / made))
    if (r.kind === 'gather') {
      if (r.seasons && world.season !== undefined && world.season !== null && !r.seasons.includes(world.season)) return { cost: Infinity, why: 'out of season' }
      const d = world.nearest(r.block)
      if (d === null) return { cost: Infinity, why: 'no ' + r.block + ' in sight' }
      const tool = r.tool ? cheapest(r.tool, 1, depth, stack) : { cost: 0 }
      return { cost: d / 6 + times * 3 + tool.cost, times, why: tool.cost === Infinity ? 'needs a pickaxe' : undefined }
    }
    if (r.kind === 'hunt') {
      const d = world.creatureNear ? world.creatureNear(r.creature) : null
      if (d === null) return { cost: Infinity, why: 'no ' + r.creature + ' in sight' }
      return { cost: d / 4 + times * 10, times }
    }
    if (r.kind === 'craft') {
      let c = 2 + times; const claim = {}
      for (const alts of r.slots) {
        const held = alts.find(a => world.have(a) - (claim[a] || 0) >= times)
        const pick = held ? { alt: held, cost: 0 } : cheapest(alts, times + 0, depth, stack)
        claim[pick.alt] = (claim[pick.alt] || 0) + times; c += pick.cost
        if (c === Infinity) return { cost: Infinity, why: 'cannot get any of ' + alts.slice(0, 4).join('/') }
      }
      if (r.table && world.stationNear('crafting_table') === null) { const t = cost('crafting_table', 1, depth, stack); if (t === Infinity) return { cost: Infinity, why: 'needs a crafting table' }; c += t + 2 }
      return { cost: c, times }
    }
    if (r.kind === 'process') {
      if (!r.permitted) return { cost: Infinity, why: 'skill or body not up to it' }
      const input = cost(r.input, r.inputCount * times, depth, stack)
      if (input === Infinity) return { cost: Infinity, why: 'cannot get ' + r.inputCount + ' ' + r.input }
      const second = r.second && !r.second.startsWith('@') ? cost(r.second, r.secondCount * times, depth, stack) : 0
      if (second === Infinity) return { cost: Infinity, why: 'cannot get ' + r.secondCount + ' ' + r.second }
      let c = 6 + times * 4 + input + second
      if (r.safeWater) c += 20
      if (world.stationNear(MOD + r.station) === null) { const s = cost(MOD + r.station, 1, depth, stack); if (s === Infinity) return { cost: Infinity, why: 'no ' + r.station + ' and cannot make one' }; c += s + 3 }
      if (r.heated) { const f = Math.min(cost('charcoal', 1, depth, stack), cost('stick', 20, depth, stack)); if (f === Infinity) return { cost: Infinity, why: 'no fuel' }; c += f }
      return { cost: c, times }
    }
    if (r.kind === 'hand') {
      if (book.unknown.has(r.id)) return { cost: Infinity, why: 'not yet discovered by the society' }
      if (r.needs.some(n => n !== 'env:water')) return { cost: Infinity, why: 'needs ' + r.needs.join(', ') }
      if (r.needs.includes('env:water') && !world.waterNear()) return { cost: Infinity, why: 'needs water nearby' }
      let c = 4 + times * (r.ticks / 40) + (book.known.has(r.id) ? 0 : 8) // undiscovered work is tried after recipes
      for (const i of r.inputs) c += cheapest(i.alts, i.count * times, depth, stack).cost
      for (const t of r.tools) c += cheapest(t, 1, depth, stack).cost
      for (const s of r.stations) if (world.stationNear(MOD + s) === null) c += cost(MOD + s, 1, depth, stack) + 3
      return { cost: c, times }
    }
    return { cost: Infinity, why: 'unknown route kind' }
  }
  const need = Math.max(0, count - world.have(norm(item)))
  return book.routesFor(item).map(r => {
    const tried = failed.has(r.kind + ':' + r.id)
    const c = tried ? { cost: Infinity, why: 'already failed for this goal' } : routeCost(r, norm(item), need, 0, [norm(item)])
    return { route: r, cost: c.cost, times: c.times, why: c.why }
  }).sort((a, b) => a.cost - b.cost)
}

module.exports = { Book, rank, norm, MOD, PICKAXES, PREY, TECHTREE }
