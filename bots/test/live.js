// Live check against a running Spawn server. The bot must be an operator, because it places a block,
// gives itself an item and summons an animal to see whether it can name them:
//
//   op SpawnTestBot          (in the server console or over RCON, once)
//   node test/live.js <port> [host]
//
// Exits 0 when every step passes.
const { createBot } = require('../lib')

const port = Number(process.argv[2] || process.env.SPAWN_PORT)
const host = process.argv[3] || process.env.SPAWN_HOST || '127.0.0.1'
if (!port) { console.error('usage: node test/live.js <port> [host]'); process.exit(2) }

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
async function until (what, check, timeout = 8000) {
  const end = Date.now() + timeout
  for (;;) {
    const value = await check()
    if (value) return value
    if (Date.now() > end) throw new Error('Timed out waiting for ' + what)
    await sleep(100)
  }
}
let passed = 0
const pass = (what, detail = '') => { passed++; console.log('PASS', what, detail) }
function expect (condition, what, detail = '') { if (!condition) throw new Error(what + (detail ? ': ' + detail : '')); pass(what, detail) }

const bot = createBot({ host, port, username: 'SpawnTestBot' })
bot.on('error', error => console.log('bot error:', error.message))
bot.on('kicked', reason => console.log('kicked:', JSON.stringify(reason).slice(0, 400)))
let placed = null
let placedBelow = null
const finish = code => { try { bot.quit() } catch {} ; setTimeout(() => process.exit(code), 300) }
setTimeout(() => { console.log('FAIL: the whole run took more than 90 s'); finish(1) }, 90000)

bot.once('spawn', async () => {
  try {
    expect(bot.spawnMod.verified, 'the server\'s ids match the bundled export', `${bot.spawnMod.added.blocks} blocks, ${bot.spawnMod.added.items} items, ${bot.spawnMod.added.entities} entity types`)
    expect(bot.game.gameMode === 'survival' || bot.game.gameMode === 'creative', 'joined the world as a player', `${bot.game.gameMode} at ${bot.entity.position.floored()}`)
    await bot.waitForChunksToLoad()

    const body = await bot.survival.query()
    expect(typeof body.hunger === 'number' && typeof body.thirst === 'number' && Array.isArray(body.clothing), 'reads its own body', `hunger ${body.hunger.toFixed(1)}, thirst ${body.thirst.toFixed(1)}, ${body.conditions}`)
    const climate = await bot.survival.climate()
    expect(typeof climate.temperatureC === 'number', 'reads the climate where it stands', `${climate.temperatureC} C, profile ${climate.profile}`)
    const weather = await until('a weather report', () => bot.survival.weather, 4000)
    expect(typeof weather.temperature === 'number' && weather.dimension.includes('overworld'), 'receives the mod\'s weather channel', `${weather.temperature.toFixed(1)} C, rain ${weather.rain}`)

    // A mod block: placed two blocks east at head height so it sits in air whatever the ground is.
    const spot = bot.entity.position.floored().offset(2, 1, 0)
    placed = spot
    bot.chat(`/setblock ${spot.x} ${spot.y} ${spot.z} spawn_9b5fbb9:kiln`)
    const kiln = await until('the kiln to appear (is the bot an operator? run: op SpawnTestBot)', () => { const b = bot.blockAt(spot); return b && b.name === 'spawn_9b5fbb9:kiln' && b })
    expect(kiln.displayName === 'Kiln' && kiln.boundingBox === 'block', 'names a mod block and knows it is solid', `${kiln.name}, state ${kiln.stateId}`)
    expect(bot.findBlock({ matching: bot.registry.blocksByName['spawn_9b5fbb9:kiln'].id, maxDistance: 6 })?.position.equals(spot), 'finds a mod block by name')

    // Walk into it: a second kiln below the first makes a wall two blocks east. The bot's own physics
    // must be stopped by the mod block's shape.
    const foot = spot.offset(0, -1, 0)
    placedBelow = foot
    bot.chat(`/setblock ${foot.x} ${foot.y} ${foot.z} spawn_9b5fbb9:kiln`)
    await until('the lower kiln', () => bot.blockAt(foot)?.name === 'spawn_9b5fbb9:kiln')
    await bot.look(-Math.PI / 2, 0) // east
    bot.setControlState('forward', true)
    await sleep(1500)
    bot.clearControlStates()
    await sleep(200)
    expect(bot.entity.position.x <= spot.x - 0.29, 'is stopped by a mod block it walks into', `x ${bot.entity.position.x.toFixed(2)}, kiln face at ${spot.x}`)

    // A mod item, and a mod action with it.
    bot.chat('/clear @s')
    await sleep(400)
    bot.chat('/give @s spawn_9b5fbb9:wool_jacket 1')
    const jacket = await until('the jacket', () => bot.inventory.items().find(item => item.name === 'spawn_9b5fbb9:wool_jacket'))
    expect(jacket.displayName === 'Wool Jacket' && jacket.stackSize === 1, 'names a mod item', `${jacket.name}, id ${jacket.type}`)
    await bot.equip(jacket, 'hand')
    const worn = await bot.survival.act('clothe')
    const dressed = await bot.survival.query()
    expect(dressed.clothing[2] === 'spawn_9b5fbb9:wool_jacket', 'puts the jacket on with a mod action', worn)
    const off = await bot.survival.act('undress')
    const bare = await bot.survival.query()
    expect(bare.clothing[2] === '' && off === 'Took off Wool Jacket', 'takes it off again', off)

    // A mod animal, kept still.
    const den = bot.entity.position.floored().offset(-4, 0, 0)
    bot.chat(`/summon spawn_9b5fbb9:brown_bear ${den.x} ${den.y} ${den.z} {NoAI:1b,Tags:["spawnbot_test"]}`)
    const bear = await until('the bear', () => Object.values(bot.entities).find(entity => entity.name === 'spawn_9b5fbb9:brown_bear'))
    expect(Math.abs(bear.height - 1.4) < 1e-6 && bear.position.distanceTo(bot.entity.position) < 8, 'names a mod animal and knows its size', `${bear.name}, ${bear.height} tall, ${bear.position.distanceTo(bot.entity.position).toFixed(1)} away`)

    // Walk: the body must cover ground under its own physics. Terrain differs from spawn to spawn,
    // so it hops as it goes and tries each compass direction until one is open.
    let walked = 0
    for (const yaw of [0, Math.PI / 2, Math.PI, -Math.PI / 2]) {
      const from = bot.entity.position.clone()
      await bot.look(yaw, 0)
      bot.setControlState('forward', true); bot.setControlState('jump', true)
      await sleep(2000)
      bot.clearControlStates()
      await sleep(300)
      walked = bot.entity.position.distanceTo(from)
      if (walked > 2) break
    }
    expect(walked > 2, 'walks under its own physics', `${walked.toFixed(1)} blocks in two seconds`)

    console.log(`\nAll ${passed} steps passed.`)
    await cleanup()
    finish(0)
  } catch (error) {
    console.log('FAIL', error.message)
    await cleanup().catch(() => {})
    finish(1)
  }
})

async function cleanup () {
  if (placed) bot.chat(`/setblock ${placed.x} ${placed.y} ${placed.z} minecraft:air`)
  if (placedBelow) bot.chat(`/setblock ${placedBelow.x} ${placedBelow.y} ${placedBelow.z} minecraft:air`)
  bot.chat('/kill @e[tag=spawnbot_test]')
  bot.chat('/clear @s')
  await sleep(600)
}
