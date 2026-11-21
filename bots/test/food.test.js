// Offline checks of how the actors' planner (commander/recipes.js) finds food in a season with nothing to pick.
// Run with: npm test
const test = require('node:test')
const assert = require('node:assert/strict')
const registry = require('../lib/registry')
const { Book, rank, MOD } = require('../commander/recipes')

function book () {
  const tables = require('prismarine-registry')('26.3'); registry.inject(tables, registry.load())
  const b = new Book(tables)
  // The server's cooking work, as /survival planning lists it: one raw cut at a heated kiln.
  b.add({ kind: 'process', id: 'cook_porkchop', input: 'porkchop', inputCount: 1, out: { cooked_porkchop: 1 }, station: 'kiln', heated: true, permitted: true })
  b.add({ kind: 'craft', id: MOD + 'kiln', slots: [[MOD + 'loose_stone'], [MOD + 'loose_stone'], [MOD + 'loose_stone'], ['clay_ball'], ['clay_ball']], out: { [MOD + 'kiln']: 1 } })
  return b
}
const view = ({ blocks = {}, creatures = {}, have = {}, season = 0 }) => ({
  season, have: it => have[it] || 0, nearest: b => blocks[b] ?? null, stationNear: () => null, waterNear: () => true, creatureNear: c => creatures[c] ?? null
})
const open = list => list.filter(w => w.cost < Infinity)

test('in spring a melon patch is no food, and says why', () => {
  const ways = rank(book(), 'melon_slice', 2, view({ blocks: { [MOD + 'wild_melon']: 10 } }))
  assert.equal(open(ways).length, 0)
  assert.equal(ways[0].why, 'out of season')
})

test('a pig in sight is raw pork by hand, and cooked pork by way of a kiln', () => {
  const world = view({ creatures: { pig: 20 }, blocks: { [MOD + 'loose_stones']: 8, clay: 15, [MOD + 'fallen_sticks']: 5 } })
  const raw = rank(book(), 'porkchop', 2, world)
  assert.equal(raw[0].route.kind, 'hunt')
  assert.ok(raw[0].cost < Infinity)
  const cooked = rank(book(), 'cooked_porkchop', 2, world)
  assert.equal(cooked[0].route.id, 'cook_porkchop')
  assert.ok(cooked[0].cost < Infinity, 'cooked pork is open: ' + cooked[0].why)
})

test('with no animal about, hunting is shut with the reason', () => {
  const ways = rank(book(), 'porkchop', 1, view({}))
  assert.equal(open(ways).length, 0)
  assert.equal(ways[0].why, 'no pig in sight')
})

test('what a kill really left replaces the guess', () => {
  const b = book()
  b.learnHunt('pig', { porkchop: 3, stick: 1 })
  assert.deepEqual(b.routesFor('porkchop').find(r => r.kind === 'hunt').out, { porkchop: 3 })
  b.learnHunt('pig', { stick: 2 }) // nothing to eat: the guess stands
  assert.deepEqual(b.routesFor('porkchop').find(r => r.kind === 'hunt').out, { porkchop: 3 })
})
