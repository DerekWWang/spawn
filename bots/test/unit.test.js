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
