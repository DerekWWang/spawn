// Offline checks: encodings, the handshake's channel list, and merging the mod's ids into mineflayer's tables.
// Run with: npm test
const test = require('node:test')
const assert = require('node:assert/strict')
const { varint, string, Reader } = require('../lib/wire')
const neoforge = require('../lib/neoforge')
const registry = require('../lib/registry')
const { decodeWeather, parseJson, fullText } = require('../lib/survival')
const { MOD_CHANNELS, VERSION } = require('../lib')

const tables = () => require('prismarine-registry')(VERSION)

test('varints and strings survive a round trip', () => {
  for (const value of [0, 1, 127, 128, 300, 35723, 2 ** 31 - 1]) assert.equal(new Reader(varint(value)).varint(), value)
  assert.deepEqual([...varint(300)], [0xac, 0x02])
  const reader = new Reader(Buffer.concat([string('spawn_9b5fbb9:kiln'), string('é')]))
  assert.equal(reader.string(), 'spawn_9b5fbb9:kiln')
  assert.equal(reader.string(), 'é')
  assert.equal(reader.remaining, 0)
  assert.throws(() => new Reader(Buffer.from([0x05, 0x41])).string(), RangeError)
})

test('the channel list a bot sends decodes to what was declared', () => {
  const declared = { play: [...MOD_CHANNELS.play, ...neoforge.NEOFORGE_PLAY], configuration: neoforge.NEOFORGE_CONFIGURATION }
  assert.deepEqual(neoforge.decodeChannels(neoforge.encodeChannels(declared)), declared)
  // First bytes: two phases, then PLAY (ordinal 1) with nine channels.
  assert.deepEqual([...neoforge.encodeChannels(declared).subarray(0, 3)], [2, 1, 9])
  assert.throws(() => neoforge.encodeChannels({ login: [] }), /Unknown connection phase/)
})

test('the handshake answers the query once and acknowledges the two steps that wait', () => {
  const { EventEmitter } = require('node:events')
  const client = new EventEmitter()
  client.state = 'configuration'
  const sent = []
  client.write = (name, params) => sent.push([name, params.channel, params.data.length])
  const session = neoforge.attach(client, MOD_CHANNELS)
  const payload = (channel, data = Buffer.alloc(0)) => client.emit('custom_payload', { channel, data })
  payload('neoforge:register', Buffer.from([0]))
  payload('neoforge:register', Buffer.from([0]))
  assert.equal(sent.length, 1)
  assert.equal(sent[0][1], 'neoforge:register')
  payload('neoforge:frozen_registry', Buffer.concat([string('minecraft:item'), varint(2), varint(0), string('minecraft:air'), varint(1709), string('spawn_9b5fbb9:wool_jacket'), varint(0)]))
  let announced = null
  client.once('neoforge_registries', registries => { announced = registries })
  payload('neoforge:frozen_registry_sync_completed')
  payload('neoforge:extensible_enum_data', Buffer.from([0]))
  assert.deepEqual(sent.slice(1), [['custom_payload', 'neoforge:frozen_registry_sync_completed', 0], ['custom_payload', 'neoforge:extensible_enum_ack', 0]])
  assert.equal(announced.get('minecraft:item').get('spawn_9b5fbb9:wool_jacket'), 1709)
  assert.equal(session.registries.get('minecraft:item').size, 2)
})

test('an export made for another Minecraft version is refused before anything is merged', () => {
  const data = registry.load()
  const mc = tables()
  const before = mc.blocksArray.length
  assert.throws(() => registry.inject(mc, { ...data, vanillaStates: data.vanillaStates - 1 }), /different Minecraft versions/)
  assert.equal(mc.blocksArray.length, before)
  assert.equal(mc.blocksByName['spawn_9b5fbb9:kiln'], undefined)
})

test('the bundled export merges into the 26.3 tables and names mod content', () => {
  const data = registry.load()
  const mc = tables()
  const added = registry.inject(mc, data)
  assert.deepEqual(added, { blocks: data.blocks.length, items: data.items.length, entities: data.entities.length })
  const kiln = mc.blocksByName['spawn_9b5fbb9:kiln']
  assert.equal(mc.blocksByStateId[kiln.defaultState], kiln)
  assert.equal(kiln.boundingBox, 'block')
  assert.deepEqual(kiln.shapes, [[0, 0, 0, 1, 1, 1]])
  const Block = require('prismarine-block')(mc)
  const seen = Block.fromStateId(kiln.defaultState, 0)
  assert.equal(seen.name, 'spawn_9b5fbb9:kiln')
  assert.equal(seen.displayName, 'Kiln')
  assert.equal(seen.material, 'mineable/pickaxe')
  // Every mod state is named, and vanilla states are untouched.
  for (const block of data.blocks) for (let s = block.minStateId; s <= block.maxStateId; s++) assert.equal(mc.blocksByStateId[s].name, block.name)
  assert.equal(Block.fromStateId(mc.blocksByName.stone.defaultState, 0).name, 'stone')
  const Item = require('prismarine-item')(mc)
  const jacket = new Item(mc.itemsByName['spawn_9b5fbb9:wool_jacket'].id, 1)
  assert.equal(jacket.name, 'spawn_9b5fbb9:wool_jacket')
  assert.equal(jacket.stackSize, 1)
  assert.equal(mc.entitiesByName['spawn_9b5fbb9:brown_bear'].height, 1.4)
  // Every bot in a process shares these tables: a second merge of the same export is a no-op,
  // and a different export is refused.
  const size = mc.blocksArray.length
  assert.deepEqual(registry.inject(tables(), data), added)
  assert.equal(tables().blocksArray.length, size)
  assert.throws(() => registry.inject(tables(), { ...data, items: data.items.slice(1) }), /different registry export/)
})

test('a block whose states change its shape keeps one shape per state', () => {
  const data = registry.load()
  const shaped = data.blocks.find(block => block.shapes.some(shape => JSON.stringify(shape) !== JSON.stringify(block.shapes[0])))
  if (!shaped) return // nothing in the mod varies its shape by state
  const mc = tables()
  registry.inject(mc, data)
  const Block = require('prismarine-block')(mc)
  for (let i = 0; i < shaped.shapes.length; i++) assert.deepEqual(Block.fromStateId(shaped.minStateId + i, 0).shapes, shaped.shapes[i])
})

test('every mod block state keeps its collision shape when a bot loads its block library', () => {
  const data = registry.load()
  const mc = tables()
  registry.inject(mc, data)
  // Each bot builds its own block library after the merge, and that rebuilds shapes from the shared table.
  for (let bot = 0; bot < 2; bot++) {
    const Block = require('prismarine-block')(mc)
    for (const block of data.blocks) {
      for (let i = 0; i < block.shapes.length; i++) {
        assert.deepEqual(Block.fromStateId(block.minStateId + i, 0).shapes, block.shapes[i], `${block.name} state ${i}`)
      }
    }
  }
  assert.deepEqual(require('prismarine-block')(mc).fromStateId(mc.blocksByName.stone.defaultState, 0).shapes, [[0, 0, 0, 1, 1, 1]])
})

test('the login check accepts matching ids and explains every kind of mismatch', () => {
  const data = registry.load()
  const mc = tables()
  registry.inject(mc, data)
  const server = () => new Map([
    ['minecraft:block', new Map([...mc.blocksArray.map(b => [b.name.includes(':') ? b.name : 'minecraft:' + b.name, b.id])])],
    ['minecraft:item', new Map([...mc.itemsArray.map(i => [i.name.includes(':') ? i.name : 'minecraft:' + i.name, i.id])])],
    ['minecraft:entity_type', new Map([...mc.entitiesArray.map(e => [e.name.includes(':') ? e.name : 'minecraft:' + e.name, e.id])])]
  ])
  assert.deepEqual(registry.verify(mc, data, server()), [])

  const shifted = server()
  shifted.get('minecraft:item').set('spawn_9b5fbb9:wool_jacket', 9999)
  assert.match(registry.verify(mc, data, shifted).join('\n'), /wool_jacket has id 9999 on the server but \d+ in the registry export/)

  const added = server()
  added.get('minecraft:block').set('othermod:widget', 5000)
  assert.match(registry.verify(mc, data, added).join('\n'), /othermod:widget is on the server but not in the registry export/)

  const removed = server()
  removed.get('minecraft:entity_type').delete('spawn_9b5fbb9:lion')
  assert.match(registry.verify(mc, data, removed).join('\n'), /spawn_9b5fbb9:lion is in the registry export but not on the server/)

  const vanilla = server()
  vanilla.get('minecraft:item').set('minecraft:stone', 7)
  assert.match(registry.verify(mc, data, vanilla).join('\n'), /1 vanilla entries of minecraft:item have different ids/)

  const silent = server()
  silent.delete('minecraft:block')
  assert.match(registry.verify(mc, data, silent).join('\n'), /did not send its minecraft:block ids/)
})

test('the weather report decodes', () => {
  const calendar = Buffer.alloc(8); calendar.writeBigInt64BE(4714n)
  const ints = Buffer.alloc(12); ints.writeInt32BE(224, 0); ints.writeInt32BE(73, 4); ints.writeInt32BE(-22, 8)
  const floats = Buffer.alloc(12); floats.writeFloatBE(12.5, 0); floats.writeFloatBE(0.75, 4); floats.writeFloatBE(6, 8)
  const weather = decodeWeather(Buffer.concat([string('minecraft:overworld'), string('tectonic'), calendar, ints, floats, Buffer.from([1])]))
  assert.deepEqual(weather, { dimension: 'minecraft:overworld', profile: 'tectonic', calendar: 4714, position: { x: 224, y: 73, z: -22 }, temperature: 12.5, rain: 0.75, wind: 6, snow: true })
})

test('replies with Java NaN and Infinity still parse', () => {
  assert.deepEqual(parseJson('{"a":NaN,"b":[1,Infinity,-Infinity],"c":"NaN stays in text","d":2.5}'), { a: null, b: [1, null, null], c: 'NaN stays in text', d: 2.5 })
})

test('a long reply is read whole, not cut at 4,096 characters', () => {
  const long = '{"a":"' + 'x'.repeat(6000) + '"}'
  assert.equal(fullText({ json: { text: long }, toString: () => long.slice(0, 4096) }), long)
  assert.equal(fullText({ json: { text: 'Took off ', extra: [{ text: 'Wool Jacket' }, '!'] }, toString: () => '' }), 'Took off Wool Jacket!')
  assert.equal(fullText({ json: { translate: 'commands.give.success' }, toString: () => 'Gave 1 item' }), 'Gave 1 item')
})

// ---------------------------------------------------------------------------------------------------
// bot.survival against a stand-in bot, and the RCON client against a local socket. No server needed.
const { EventEmitter, once } = require('node:events')
const net = require('node:net')
const survival = require('../lib/survival')
const rcon = require('../lib/rcon')

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
const chatLine = text => ({ json: { text }, toString: () => text })

/** A stand-in for a mineflayer bot: it records what is sent, and the test plays the server. */
function fakeBot (options = {}) {
  const bot = new EventEmitter()
  bot._client = new EventEmitter()
  bot.time = { age: null }
  bot.sent = []
  bot.chat = text => { bot.sent.push(text); bot.emit('sent', text) }
  bot.hear = (text, position = 'system') => bot.emit('message', chatLine(text), position, null)
  survival.install(bot, { commandSettle: 0, commandLimit: false, ...options })
  return bot
}
/** Wait until the bot has sent `count` commands in all. */
async function sends (bot, count) { while (bot.sent.length < count) await once(bot, 'sent') }

const body = hunger => `{"entity":"0-0","recreation":50.0,"hunger":${hunger},"thirst":NaN,"temperature":37.0,"health":20.0,"conditions":"sound","calendar":4714,"schemaVersion":6,"skills":{},"service":{},"anatomy":{"HEAD":1.0},"clothing":["","",""]}`
const CLIMATE = '{"profile":"tectonic","regional":true,"temperatureC":12.5,"roof":false,"nearFire":false,"rain":0.0,"windMetersPerSecond":3.0,"snow":false,"windShelter":0.2}'
const RESOURCES = '{"schemaVersion":1,"resources":[{"id":"spawn_9b5fbb9:loose_stones","action":"gather"}]}'
const DEAD = 'The dead take no part in the world.'

test('only system chat answers a command', async () => {
  const bot = fakeBot()
  const reply = bot.survival.act('drink')
  await sends(bot, 1)
  assert.deepEqual(bot.sent, ['/survival act drink'])
  bot.hear('<Ada> You drink from the stream', 'chat')
  bot.hear('Overloaded: carrying 61 kg', 'game_info')
  bot.emit('message', chatLine('a line with no position at all'))
  const joined = { translate: 'multiplayer.player.joined', json: { translate: 'multiplayer.player.joined', with: ['Busy042'] }, toString: () => 'Busy042 joined the game' }
  assert.equal(survival.isAnnouncement(joined), true)
  assert.equal(survival.isAnnouncement({ json: { translate: 'command.unknown.command' } }), false)
  bot.emit('message', joined, 'system', null)
  bot.emit('message', { json: { translate: 'death.attack.starve', with: ['Mortal'] }, toString: () => 'Mortal starved to death' }, 'system', null)
  bot.hear('You drink from the stream')
  assert.equal(await reply, 'You drink from the stream')

  // A caller's own predicate is shown the text of system lines, and nothing else.
  const shown = []
  const picked = bot.survival.command('survival craft spawn_9b5fbb9:knap', text => { shown.push(text); return text.startsWith('Knapping') })
  await sends(bot, 2)
  assert.equal(bot.sent[1], '/survival craft spawn_9b5fbb9:knap')
  bot.hear('Knapping, says a player', 'chat')
  bot.hear('It begins to rain')
  bot.hear('Knapping: 6 s')
  assert.equal(await picked, 'Knapping: 6 s')
  assert.deepEqual(shown, ['It begins to rain', 'Knapping: 6 s'])

  // A predicate that throws rejects its own command and nothing more.
  const broken = bot.survival.command('/survival calendar', () => { throw new Error('bad predicate') })
  await sends(bot, 3)
  bot.hear('Day 12')
  await assert.rejects(broken, /bad predicate/)
})

test('each JSON report is taken only by the call that asked for it', async () => {
  const bot = fakeBot()
  const climate = bot.survival.climate()
  await sends(bot, 1)
  bot.hear(body(71.5)); bot.hear(RESOURCES); bot.hear('{"profile": not json'); bot.hear('Day 12')
  bot.hear(CLIMATE)
  assert.deepEqual([(await climate).profile, (await climate).roof], ['tectonic', false])

  const query = bot.survival.query()
  await sends(bot, 2)
  bot.hear(CLIMATE); bot.hear(RESOURCES); bot.hear('{"error":"A living server humanoid is required"}')
  bot.hear(body(71.5))
  assert.deepEqual([(await query).hunger, (await query).thirst], [71.5, null])

  const resources = bot.survival.resources(16)
  await sends(bot, 3)
  assert.equal(bot.sent[2], '/survival resources 16')
  bot.hear(CLIMATE); bot.hear(body(71.5))
  bot.hear(RESOURCES)
  assert.equal((await resources).resources.length, 1)
  await assert.rejects(bot.survival.resources(40), /radius is a whole number from 1 to 16/)
  assert.equal(bot.sent.length, 3)

  // The climate's bare error object is the climate's answer, and a failed survey is still a survey.
  const unseen = bot.survival.climate()
  await sends(bot, 4)
  bot.hear('{"schemaVersion":1,"resources":[],"error":"A sighted living server humanoid and radius 1–16 are required"}')
  bot.hear('{"error":"A living server humanoid is required"}')
  assert.equal((await unseen).error, 'A living server humanoid is required')

  // The dead are told so at once instead of waiting out the timeout; a plain command still gets the line.
  const dead = bot.survival.query()
  await sends(bot, 5)
  bot.hear(DEAD)
  await assert.rejects(dead, error => error.code === 'EDEAD' && /dead take no part/.test(error.message))
  const refused = bot.survival.command('/survival act drink', undefined, 8000)
  await sends(bot, 6)
  bot.hear(DEAD)
  assert.equal(await refused, DEAD)

  // So are the unconscious: a report is refused at once, with its own code.
  const out = bot.survival.climate()
  await sends(bot, 7)
  bot.hear('You are unconscious.')
  await assert.rejects(out, error => error.code === 'EUNCONSCIOUS' && /unconscious/.test(error.message))
})

test('a late reply to a command that timed out is not given to the next one', async () => {
  const bot = fakeBot({ commandTimeout: 40 })
  const late = bot.survival.query()
  await assert.rejects(late, error => error.code === 'ETIMEDOUT' && /No reply to "\/survival json" within 40 ms/.test(error.message))
  const next = bot.survival.query()
  await sends(bot, 2)
  bot.hear(body(10)) // the first command's reply, late
  bot.hear(body(20))
  assert.equal((await next).hunger, 20)

  // A late reply that arrives between commands is dropped too.
  await assert.rejects(bot.survival.act('eat'), /No reply/)
  bot.hear('You eat the bread')
  const drink = bot.survival.act('drink')
  await sends(bot, 4)
  bot.hear('You drink from the stream')
  assert.equal(await drink, 'You drink from the stream')

  // A reply of another shape proves the older command will not be answered any more.
  await assert.rejects(bot.survival.query(), /No reply/)
  const climate = bot.survival.climate()
  await sends(bot, 6)
  bot.hear(CLIMATE)
  await climate
  const fresh = bot.survival.query()
  await sends(bot, 7)
  bot.hear(body(30))
  assert.equal((await fresh).hunger, 30)

  // When the timed-out command is never answered, the guess costs the next command and no more.
  await assert.rejects(bot.survival.query(), /No reply/)
  const taken = bot.survival.query()
  await sends(bot, 9)
  bot.hear(body(40)) // its own reply, taken for the late one
  await assert.rejects(taken, /No reply/)
  const sound = bot.survival.query()
  await sends(bot, 10)
  bot.hear(body(50))
  assert.equal((await sound).hunger, 50)
})

test('commandLimit is merged over the defaults and checked when the bot is made', async () => {
  const defaults = survival.DEFAULT_LIMIT
  assert.deepEqual(survival.readLimit(undefined), defaults)
  assert.deepEqual(survival.readLimit(true), defaults)
  assert.equal(survival.readLimit(false), null)
  assert.deepEqual(survival.readLimit({ ceiling: 180 }), { ...defaults, ceiling: 180 })
  assert.deepEqual(survival.readLimit({ ceiling: 140, drainPerSecond: 16 }), { ...defaults, drainPerSecond: 16 })
  assert.deepEqual(survival.readLimit({ ceiling: undefined }), defaults)
  const bad = [{ ceiling: NaN }, { ceiling: '180' }, { ceiling: 10 }, { ceiling: Infinity }, { drainPerSecond: 0 }, { drainPerSecond: -4 },
    { drainPerSecond: NaN }, { safety: 0 }, { safety: 1.5 }, { fallbackTicksPerSecond: 40 }, { drainPerSec: 16 }, 5, 'fast', [140, 16]]
  for (const limit of bad) assert.throws(() => fakeBot({ commandLimit: limit }), /commandLimit/, JSON.stringify(limit))
  // createBot refuses it before it opens a connection.
  assert.throws(() => require('../lib').createBot({ host: '127.0.0.1', port: 9, username: 'Nobody', commandLimit: { ceiling: 180, drainPerSecond: 0 } }), /commandLimit\.drainPerSecond must be/)

  // `true` and a partial object used to leave the first command waiting for ever.
  for (const limit of [true, { ceiling: 180 }]) {
    const bot = fakeBot({ commandLimit: limit })
    const reply = bot.survival.act('drink')
    await sends(bot, 1)
    bot.hear('ok')
    assert.equal(await reply, 'ok')
  }

  // `false` turns pacing off.
  const free = fakeBot({ commandLimit: false })
  free.on('sent', () => setImmediate(() => free.hear('ok')))
  await Promise.all(Array.from({ length: 12 }, () => free.survival.act('drink')))
  assert.equal(free.sent.length, 12)

  // The default lets a burst of seven through and holds the eighth.
  const paced = fakeBot({ commandLimit: undefined, clock: () => 0 })
  paced.on('sent', () => setImmediate(() => paced.hear('ok')))
  const replies = Array.from({ length: 9 }, () => paced.survival.act('drink'))
  await sends(paced, 7)
  await sleep(60)
  assert.equal(paced.sent.length, 7)
  paced.emit('end', 'test over')
  const settled = await Promise.allSettled(replies)
  assert.deepEqual(settled.map(result => result.status), [...Array(7).fill('fulfilled'), 'rejected', 'rejected'])
})

/**
 * A server ticking at a steady rate and a bot that sends a command whenever its pacer lets it.
 * Returns how many commands went and the highest spam count the server reached (it kicks at 200).
 */
function paceAgainstServer ({ ticksPerSecond, seconds, limit, frozen = false }) {
  let now = 0
  const pacer = survival.createPacer(survival.readLimit(limit), () => now)
  let age = 5000; let ticks = 0; let nextTick = 0; let count = 0; let peak = 0; let admitted = 0
  pacer.sample(age, now) // the time report every player gets on joining
  for (; now <= seconds * 1000; now += 10) {
    while (nextTick <= now) {
      nextTick += 1000 / ticksPerSecond; ticks++
      if (!frozen) age++
      if (count > 0) count--
      if (ticks % 20 === 0) pacer.sample(age, now)
    }
    if (pacer.admit(now) === 0) { admitted++; count += 20; peak = Math.max(peak, count) }
  }
  return { admitted, peak }
}

test('pacing follows the tick rate the server reports', () => {
  for (const ticksPerSecond of [20, 10, 5, 2]) {
    const { admitted, peak } = paceAgainstServer({ ticksPerSecond, seconds: 120 })
    const drained = 120 * ticksPerSecond / 20 // commands' worth of count the server took off
    assert.ok(peak <= 160, `${ticksPerSecond} ticks a second: the count peaked at ${peak}`)
    assert.ok(admitted >= 7 + Math.floor(drained * 0.8) - 2 && admitted <= 7 + drained, `${ticksPerSecond} ticks a second: ${admitted} commands`)
  }
  // A world frozen with /tick freeze still drains the count; its clock does not say how fast.
  const frozen = paceAgainstServer({ ticksPerSecond: 20, seconds: 120, frozen: true })
  assert.ok(frozen.peak <= 160 && frozen.admitted >= 7 + 40, JSON.stringify(frozen))
  // An explicit drainPerSecond is taken as given, whatever the server does: this is what used to be assumed.
  const fixed = paceAgainstServer({ ticksPerSecond: 5, seconds: 120, limit: { drainPerSecond: 16 } })
  assert.ok(fixed.admitted >= 100 && fixed.peak >= 200, JSON.stringify(fixed))

  // Before the second time report: a conservative ten ticks a second.
  let now = 0
  const blind = survival.createPacer(survival.readLimit(), () => now)
  for (let i = 0; i < 7; i++) assert.equal(blind.admit(now), 0)
  assert.ok(blind.admit(now) > 0)
  assert.equal(blind.ticksPerSecond, null)
  now = 2400; assert.ok(blind.admit(now) > 0)
  now = 2600; assert.equal(blind.admit(now), 0)

  // Never more than twenty ticks a second, whatever the clock says.
  now = 0
  const sprint = survival.createPacer(survival.readLimit(), () => now)
  sprint.sample(1000, now)
  for (let i = 0; i < 7; i++) sprint.admit(now)
  now = 1000; sprint.sample(3000, now)
  assert.equal(sprint.ticksPerSecond, 20)
  assert.ok(Math.abs(sprint.count - (140 - 16)) < 1e-9, String(sprint.count))

  // A server that stops ticking stops the count falling: nothing is assumed past the next report due.
  now = 0
  const stalled = survival.createPacer(survival.readLimit(), () => now)
  for (let second = 0; second <= 10; second++) { now = second * 1000; stalled.sample(1000 + 20 * second, now) }
  let burst = 0; while (stalled.admit(now) === 0) burst++
  assert.equal(burst, 7)
  let during = 0
  for (; now <= 70000; now += 100) if (stalled.admit(now) === 0) during++
  assert.equal(during, 0)
  assert.ok(stalled.count >= 140 - 16 - 1e-9)
})

test('a bot paces its commands by the time reports it receives', async () => {
  let now = 0
  const bot = fakeBot({ commandLimit: { ceiling: 40 }, clock: () => now })
  bot.on('sent', () => setImmediate(() => bot.hear('ok')))
  const report = (seconds, ticks) => { now += seconds * 1000; bot.time.age += ticks; bot.emit('time') }
  bot.time.age = 1000; bot.emit('time')
  const replies = ['one', 'two', 'three'].map(step => bot.survival.act(step))
  await sends(bot, 2)
  await sleep(60)
  assert.equal(bot.sent.length, 2) // the third would pass the ceiling, and no time has passed
  report(1, 20) // twenty ticks take 16 off the count of 40: still not room for 20 more
  await sleep(30)
  assert.equal(bot.sent.length, 2)
  report(4, 20) // a slow server: four seconds for the next twenty ticks
  await sends(bot, 3)
  assert.deepEqual(await Promise.all(replies), ['ok', 'ok', 'ok'])
})

test('a disconnect rejects every command at once and leaves nothing behind', async () => {
  const timers = () => process.getActiveResourcesInfo().filter(name => name === 'Timeout').length
  const unhandled = []
  const note = reason => unhandled.push(reason)
  process.on('unhandledRejection', note)
  try {
    const before = timers()
    const bot = fakeBot({ commandTimeout: 60000, commandLimit: undefined })
    const inFlight = bot.survival.query()
    const queued = bot.survival.climate()
    bot.survival.act('drink') // a caller that drops the promise
    await sends(bot, 1)
    assert.ok(timers() > before)
    bot.emit('kicked', { translate: 'disconnect.spam' }, true)
    bot.emit('end', 'socketClosed')
    assert.equal(timers(), before)
    await assert.rejects(inFlight, error => error.code === 'EDISCONNECTED' && /The bot disconnected \(kicked: .*disconnect\.spam.*\); no reply to "\/survival json"/.test(error.message))
    await assert.rejects(queued, error => error.code === 'EDISCONNECTED' && /survival climate/.test(error.message))
    await assert.rejects(bot.survival.query(), /The bot disconnected \(kicked/)
    bot.survival.act('eat') // dropped again, after the end
    bot.hear('a line after the end')
    assert.equal(bot.sent.length, 1)
    assert.equal(timers(), before)

    const quit = fakeBot({ commandTimeout: 30 })
    quit.survival.act('sleep') // dropped, and left to time out
    await sleep(60)
    quit.emit('end', 'disconnect.quitting')
    await assert.rejects(quit.survival.climate(), /The bot disconnected \(disconnect\.quitting\)/)
    await sleep(20) // an unhandled rejection is reported after the microtasks have run
    assert.deepEqual(unhandled, [])
  } finally {
    process.removeListener('unhandledRejection', note)
  }
})

/** A local stand-in for a server's RCON port. `answer(socket, packet)` plays the server. */
async function rconServer (answer) {
  const sockets = []
  const server = net.createServer(socket => {
    sockets.push(socket)
    socket.closed = new Promise(resolve => socket.once('close', resolve))
    socket.on('error', () => {})
    let buffered = Buffer.alloc(0)
    socket.on('data', chunk => {
      buffered = Buffer.concat([buffered, chunk])
      while (buffered.length >= 4 && buffered.length >= 4 + buffered.readInt32LE(0)) {
        const length = buffered.readInt32LE(0)
        const packet = { id: buffered.readInt32LE(4), type: buffered.readInt32LE(8), body: buffered.toString('utf8', 12, 4 + length - 2) }
        buffered = buffered.subarray(4 + length)
        answer(socket, packet)
      }
    })
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  return { port: server.address().port, sockets, stop: () => { for (const socket of sockets) socket.destroy(); return new Promise(resolve => server.close(resolve)) } }
}
const within = (promise, ms, what) => Promise.race([promise, sleep(ms).then(() => { throw new Error(what + ' did not happen within ' + ms + ' ms') })])

test('the RCON client logs in, runs commands, and fails fast when the server goes away', async () => {
  const server = await rconServer((socket, packet) => {
    if (packet.type === 3) socket.write(rcon.frame(packet.id, 2, ''))
    else if (packet.body === 'stop') socket.destroy()
    else if (packet.body !== 'hang') socket.write(rcon.frame(packet.id, 0, 'ran ' + packet.body))
  })
  try {
    const con = await rcon.connect({ port: server.port, password: 'secret', timeout: 20000 })
    assert.equal(await con.send('tick query'), 'ran tick query')
    const started = Date.now()
    const inFlight = con.send('stop')
    const queued = con.send('list')
    await assert.rejects(inFlight, /closed by the server/)
    await assert.rejects(queued, /closed by the server/)
    await assert.rejects(con.send('list'), /closed by the server/)
    assert.ok(Date.now() - started < 5000, 'the commands waited out their timeout')

    const second = await rcon.connect({ port: server.port, password: 'secret', timeout: 20000 })
    const waiting = second.send('hang')
    await sleep(30)
    second.close()
    await assert.rejects(waiting, /closed by close\(\)/)
    await assert.rejects(second.send('list'), /closed by close\(\)/)
    await within(server.sockets[1].closed, 2000, 'closing the socket')
  } finally { await server.stop() }
})

test('a refused or unanswered RCON login closes the socket', async () => {
  const refusing = await rconServer((socket, packet) => socket.write(rcon.frame(-1, 2, '')))
  try {
    await assert.rejects(rcon.connect({ port: refusing.port, password: 'wrong', timeout: 20000 }), /RCON refused the password/)
    await within(refusing.sockets[0].closed, 2000, 'closing the socket after a refused password')
  } finally { await refusing.stop() }

  const silent = await rconServer(() => {})
  try {
    await assert.rejects(rcon.connect({ port: silent.port, password: 'secret', timeout: 60 }), /No RCON reply to "login" within 60 ms/)
    await within(silent.sockets[0].closed, 2000, 'closing the socket after a login that timed out')
  } finally { await silent.stop() }

  // Nothing listening: the failure names the address.
  const gone = await rconServer(() => {})
  await gone.stop()
  await assert.rejects(rcon.connect({ port: gone.port, password: 'secret', timeout: 20000 }), /RCON connection to 127\.0\.0\.1:\d+ failed/)
})

test('an RCON connection that never opens times out', async t => {
  const socket = new EventEmitter()
  socket.destroyed = false
  socket.destroy = () => { socket.destroyed = true }
  socket.write = () => { throw new Error('written to before connecting') }
  t.mock.method(net, 'connect', () => socket)
  const started = Date.now()
  await assert.rejects(rcon.connect({ host: '192.0.2.1', port: 25575, password: 'secret', timeout: 50 }), /No RCON connection to 192\.0\.2\.1:25575 within 50 ms/)
  assert.ok(Date.now() - started < 2000)
  assert.equal(socket.destroyed, true)
})

test('a frozen registry that cannot be read is reported as an error, not thrown', () => {
  const client = new EventEmitter()
  client.state = 'configuration'
  client.write = () => {}
  const errors = []
  client.on('error', error => errors.push(error))
  const session = neoforge.attach(client, MOD_CHANNELS)
  // A string said to be five bytes long in a payload of two.
  assert.doesNotThrow(() => client.emit('custom_payload', { channel: 'neoforge:frozen_registry', data: Buffer.from([0x05, 0x41]) }))
  assert.equal(errors.length, 1)
  assert.match(errors[0].message, /Could not read a neoforge:frozen_registry payload from the server: string runs past the end/)
  assert.ok(errors[0].cause instanceof RangeError)
  assert.equal(session.registries.size, 0)
  client.emit('custom_payload', { channel: 'neoforge:frozen_registry', data: Buffer.concat([string('minecraft:item'), varint(1), varint(0), string('minecraft:air'), varint(0)]) })
  assert.equal(session.registries.get('minecraft:item').get('minecraft:air'), 0)
  assert.equal(errors.length, 1)
})

test('the example stops watching and leaves when its bot dies', t => {
  const bot = new EventEmitter()
  const calls = []
  bot.quit = () => calls.push('quit')
  bot.spawnMod = { added: { blocks: 1, items: 1, entities: 1 } }
  bot.entity = { position: { floored: () => '(0, 64, 0)' } }
  bot.entities = {}
  bot.findBlocks = () => []
  bot.survival = { weather: null, query: async () => ({ hunger: 50, thirst: 80, temperature: 37, conditions: 'sound' }) }
  const lib = require.resolve('../lib')
  const example = require.resolve('../examples/hello.js')
  const real = require.cache[lib]
  const argv = process.argv
  t.after(() => { require.cache[lib] = real; delete require.cache[example]; process.argv = argv })
  require.cache[lib] = { id: lib, filename: lib, loaded: true, exports: { createBot: () => bot } }
  process.argv = [argv[0], example, '25565']
  t.mock.method(console, 'log', () => {})
  t.mock.method(process, 'exit', code => calls.push('exit ' + code))
  let poll = null
  t.mock.method(globalThis, 'setInterval', run => { poll = run; return 'the watch' })
  t.mock.method(globalThis, 'clearInterval', handle => calls.push('clear ' + handle))

  require(example)
  bot.emit('spawn')
  assert.equal(typeof poll, 'function')
  bot.emit('death')
  assert.deepEqual(calls, ['clear the watch', 'quit'])
  bot.emit('end', 'disconnect.quitting')
  assert.deepEqual(calls.slice(2), ['clear the watch', 'exit 0'])
})
