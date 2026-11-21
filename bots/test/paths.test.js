// Offline checks of the actors' path-finder (commander/body.js) on small hand-built worlds.
// Run with: npm test
const test = require('node:test')
const assert = require('node:assert/strict')
const { Vec3 } = require('vec3')
const B = require('../commander/body')

/** A world of named blocks over a flat stone floor at y = 63, with a wall of bedrock round the edge so nothing leaves it. */
function world (size, blocks, feet) {
  const map = new Map()
  for (const [x, y, z, name] of blocks) map.set(x + ',' + y + ',' + z, name)
  const solidNames = /stone|bedrock|dirt|leaves|log/
  const bot = {
    entity: { position: new Vec3(feet[0] + 0.5, feet[1], feet[2] + 0.5) },
    blockAt (p) {
      const x = Math.floor(p.x); const y = Math.floor(p.y); const z = Math.floor(p.z)
      let name = map.get(x + ',' + y + ',' + z)
      if (!name) name = x < 0 || z < 0 || x >= size || z >= size ? 'bedrock' : y <= 63 ? 'stone' : 'air'
      return { name, position: new Vec3(x, y, z), boundingBox: solidNames.test(name) ? 'block' : 'empty', getProperties: () => ({}) }
    }
  }
  return bot
}
const cellsOf = route => route.cells.map(c => [c.x, c.y, c.z].join())
const column = (x, z, from, to, name) => { const out = []; for (let y = from; y <= to; y++) out.push([x, y, z, name]); return out }

test('open ground is walked straight across', () => {
  const route = B.findPath(world(12, [], [1, 64, 1]), { x: 9, z: 1 }, 0)
  assert.equal(route.partial, false)
  assert.equal(route.cells.length, 9)
  assert.ok(route.cells.every(c => c.y === 64 && !c.clear && !c.climb))
})

test('a hedge of leaves with no way round is pushed through, and the leaves in the way are named', () => {
  const hedge = []
  for (let z = 0; z < 8; z++) hedge.push([4, 64, z, 'oak_leaves'], [4, 65, z, 'oak_leaves'], [4, 66, z, 'oak_leaves'], [4, 67, z, 'oak_leaves'])
  const route = B.findPath(world(8, hedge, [1, 64, 3]), { x: 6, z: 3 }, 0)
  assert.equal(route.partial, false)
  const through = route.cells.find(c => c.x === 4)
  assert.ok(through && through.clear && through.clear.length === 2, 'the cell in the hedge lists two blocks to break')
  assert.deepEqual(through.clear.map(c => c[1]).sort(), [64, 65])
})

test('leaves are stepped over when they are low, and gone round when open ground is nearly as short', () => {
  // One leaf block in the way on an open floor: the route goes round it on the ground.
  const round = B.findPath(world(10, [[4, 64, 4, 'oak_leaves']], [1, 64, 4]), { x: 8, z: 4 }, 0)
  assert.ok(round.cells.every(c => c.y === 64 && !c.clear), 'no climbing on foliage where the ground is open')
  // A low hedge right across: stepping onto it and off again is the way.
  const hedge = []
  for (let z = 0; z < 8; z++) hedge.push([4, 64, z, 'oak_leaves'])
  const over = B.findPath(world(8, hedge, [1, 64, 3]), { x: 6, z: 3 }, 0)
  assert.equal(over.partial, false)
  assert.ok(over.cells.some(c => c.x === 4 && c.y === 65), 'steps onto the hedge')
})

test('no corner is cut between two blocks that stand at the height of the legs', () => {
  // Leaves east and south of the start: the diagonal between them is shut though the cell beyond is open.
  const bot = world(8, [[3, 64, 2, 'oak_leaves'], [2, 64, 3, 'oak_leaves']], [2, 64, 2])
  const route = B.findPath(bot, { x: 3, z: 3 }, 0)
  assert.equal(route.partial, false)
  const steps = cellsOf(route)
  assert.notEqual(steps[1], '3,64,3', 'the first step is not the blocked diagonal')
  for (let i = 1; i < route.cells.length; i++) {
    const a = route.cells[i - 1]; const b = route.cells[i]
    if (a.x !== b.x && a.z !== b.z) for (const [x, z] of [[b.x, a.z], [a.x, b.z]]) for (const y of [Math.min(a.y, b.y), Math.max(a.y, b.y) + 1]) assert.equal(bot.blockAt(new Vec3(x, y, z)).boundingBox, 'empty', `corner ${x},${y},${z} is clear`)
  }
})

test('a pit is left by the vine that hangs in it', () => {
  // A floor three blocks down inside a ring of stone, a vine up one side, the goal on the rim.
  const blocks = []
  for (let x = 0; x < 9; x++) for (let z = 0; z < 9; z++) if (!(x === 4 && z === 4)) blocks.push(...column(x, z, 64, 66, 'stone'))
  blocks.push(...column(4, 4, 64, 66, 'vine'))
  const bot = world(9, blocks, [4, 64, 4])
  const route = B.findPath(bot, { x: 7, z: 4 }, 0)
  assert.equal(route.partial, false)
  assert.equal(route.cells[1].climb, 3, 'the first move is a climb of three')
  assert.equal(route.cells[1].y, 67)
  // Without the vine there is no way out, and the route says so rather than pretending.
  const bare = world(9, blocks.filter(b => b[3] !== 'vine'), [4, 64, 4])
  const stuck = B.findPath(bare, { x: 7, z: 4 }, 0)
  assert.equal(stuck.partial, true)
  assert.equal(stuck.cells.length, 1)
})

test('water and long drops are still not walked into', () => {
  const blocks = []
  for (let z = 0; z < 8; z++) blocks.push([4, 63, z, 'water'])
  const wet = B.findPath(world(8, blocks, [1, 64, 3]), { x: 6, z: 3 }, 0)
  assert.equal(wet.partial, true, 'a channel right across with no bridge is not crossed')
  assert.ok(wet.cells.every(c => c.x < 4))
})

test('a body that the server holds somewhere else notices on the second reading and goes back there', async () => {
  const bot = world(8, [], [2, 64, 2])
  bot.entity.velocity = new Vec3(0.1, 0, 0)
  bot.clearControlStates = () => { bot.cleared = true }
  let report = { hunger: 80, thirst: 80, position: [2.5, 64, 2.5] }
  bot.survival = { query: async () => report }
  const actor = { bot }
  await B.readBody(actor)
  assert.equal(actor.apart, 0)
  report = { ...report, position: [40.5, 70, -12.5] }
  await B.readBody(actor)
  assert.equal(actor.apart, 1, 'one reading apart may be a step in between')
  await B.readBody(actor)
  assert.equal(actor.apart, 2)
  const moved = B.comeBack(actor)
  assert.deepEqual(moved.now, [40.5, 70, -12.5])
  assert.deepEqual([bot.entity.position.x, bot.entity.position.y, bot.entity.position.z], [40.5, 70, -12.5])
  assert.equal(bot.entity.velocity.x, 0)
  assert.equal(actor.apart, 0)
  assert.ok(bot.cleared)
  // A report without a position (an older server) changes nothing.
  report = { hunger: 80, thirst: 80 }
  await B.readBody(actor)
  assert.equal(actor.apart, 0)
})

test('a crown overhead is no place to fell a tree from', () => {
  const bot = world(8, [[3, 70, 3, 'oak_leaves'], [5, 64, 3, 'oak_log']], [3, 64, 3])
  assert.equal(B.clearOverhead(bot, 3, 64, 3), false, 'leaves six above')
  assert.equal(B.clearOverhead(bot, 4, 64, 3), true)
})

test('a body with its head in a crown breaks the leaves it is caught in', async () => {
  const bot = world(8, [[3, 65, 3, 'oak_leaves'], [4, 66, 3, 'oak_leaves']], [3, 64, 3])
  const dug = []
  bot.lookAt = async () => {}
  bot.dig = async b => { dug.push(b.position.toString()) }
  bot.entity.position = new Vec3(3.5, 64, 3.5)
  assert.equal(await B.unwedge({ bot }), true)
  assert.deepEqual(dug, [new Vec3(3, 65, 3).toString()], 'only the leaves inside the body, not those beside it')
  bot.entity.position = new Vec3(6.5, 64, 6.5)
  assert.equal(await B.unwedge({ bot }), false)
})

test('a one-block pit two deep has no walking way out (climbOut is what gets a body out)', () => {
  const blocks = []
  for (let x = 0; x < 8; x++) for (let z = 0; z < 8; z++) if (!(x === 4 && z === 4)) blocks.push([x, 64, z, 'dirt'], [x, 65, z, 'dirt'])
  const route = B.findPath(world(8, blocks, [4, 64, 4]), { x: 7, z: 4 }, 0)
  assert.equal(route.partial, true)
  assert.equal(route.cells.length, 1)
})

test('a step down under a low bough is not taken blind: the bough is named to push aside, or a wall is gone round', () => {
  // A ledge one block up (y 64 to 66 of dirt at x 0..3), the walk going east and dropping to the floor at x 4,
  // with leaves at the old head height over the first cell below the drop (the shape that stopped Ada west of camp).
  const ledge = []
  for (let x = 0; x <= 3; x++) for (let z = 0; z < 6; z++) ledge.push([x, 64, z, 'dirt'])
  const bough = [...ledge]; for (let z = 0; z < 6; z++) bough.push([4, 66, z, 'oak_leaves'])
  const under = B.findPath(world(8, bough, [2, 65, 2]), { x: 6, z: 2 }, 0)
  assert.equal(under.partial, false)
  const drop = under.cells.find(c => c.x === 4)
  assert.ok(drop && drop.y === 64 && drop.clear && drop.clear.some(([x, y]) => x === 4 && y === 66), 'the leaves at the old head height are listed to break: ' + JSON.stringify(drop))
  // A beam of solid wood there instead, with a gap at z 5: the route goes round by the gap.
  const beam = [...ledge]; for (let z = 0; z < 5; z++) beam.push([4, 66, z, 'oak_log'])
  const round = B.findPath(world(8, beam, [2, 65, 2]), { x: 6, z: 2 }, 0)
  assert.equal(round.partial, false)
  assert.ok(round.cells.filter(c => c.x === 4).every(c => c.z === 5), 'drops down only where the head is clear: ' + JSON.stringify(round.cells.map(c => [c.x, c.y, c.z])))
})
