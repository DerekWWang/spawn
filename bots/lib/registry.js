// Teaches mineflayer's data tables the mod's own blocks, items and entity types.
//
// Mineflayer names what it sees from tables built for vanilla Minecraft. A mod's content arrives as bare
// numbers past the end of those tables. The server can write what the numbers mean
// (/survival export_registry, see README); this module merges that file into a bot's tables and checks it
// against the ids the server announces at login.
const fs = require('fs')
const path = require('path')

const DEFAULT_FILE = path.join(__dirname, '..', 'data', 'spawn-registry.json')

function load (file = DEFAULT_FILE) {
  const data = JSON.parse(fs.readFileSync(file, 'utf8'))
  if (data.schema !== 1) throw new Error(`Unsupported registry export schema ${data.schema} in ${file}`)
  for (const key of ['blocks', 'items', 'entities']) if (!Array.isArray(data[key])) throw new Error(`Registry export ${file} has no "${key}" list`)
  return data
}

const MERGED = Symbol.for('spawn-bots.registry-merged')
const sameShape = (a, b) => JSON.stringify(a) === JSON.stringify(b)

/**
 * Merge the export into a mineflayer registry (bot.registry). Must run before the bot's plugins load,
 * because some of them copy the entity table when they start. Safe to call for every bot.
 * @returns how many of each were added
 */
function inject (registry, data) {
  const counts = { blocks: data.blocks.length, items: data.items.length, entities: data.entities.length }
  // Mineflayer shares one set of tables between every bot of a version in the process, so the merge
  // happens once. A second bot finds it done; a different export in the same process cannot be honoured.
  const fingerprint = JSON.stringify([data.blocks, data.items, data.entities].map(list => list.map(entry => [entry.name, entry.id])))
  if (registry.blocksArray[MERGED] !== undefined) {
    if (registry.blocksArray[MERGED] !== fingerprint) throw new Error('A different registry export is already merged into this process; run bots for different servers in separate processes')
    return counts
  }
  const vanillaStates = Math.max(...registry.blocksArray.map(block => block.maxStateId)) + 1
  if (data.vanillaStates !== vanillaStates) {
    throw new Error(`The registry export counts ${data.vanillaStates} vanilla block states but these data tables have ${vanillaStates}: ` +
      'the export and the bot are for different Minecraft versions')
  }
  // Collision shapes go into the shared shape table as well as onto each block: every bot's block library
  // rebuilds block shapes from that table when it loads, and a block missing from it would be left with
  // no shape at all, which stops the physics of any bot that walks up to one.
  const collision = registry.blockCollisionShapes
  const shapeIds = new Map()
  let nextShape = collision ? Math.max(...Object.keys(collision.shapes).map(Number)) + 1 : 0
  const shapeId = shape => {
    const key = JSON.stringify(shape)
    if (!shapeIds.has(key)) { shapeIds.set(key, nextShape); collision.shapes[nextShape++] = shape }
    return shapeIds.get(key)
  }
  for (const source of data.blocks) {
    if (registry.blocks[source.id] || registry.blocksByName[source.name]) throw new Error(`Block ${source.name} (id ${source.id}) collides with an existing block`)
    const { shapes, needsTool, ...fields } = source
    const block = { ...fields, shapes: shapes[0] || [] }
    // Per-state shapes only where a state changes the shape (a door, a slab), as mineflayer's own tables do.
    const shaped = shapes.some(shape => !sameShape(shape, shapes[0]))
    if (shaped) block.stateShapes = shapes
    if (collision) collision.blocks[block.name] = shaped ? shapes.map(shapeId) : shapeId(block.shapes)
    block.needsTool = needsTool
    registry.blocks[block.id] = block
    registry.blocksByName[block.name] = block
    registry.blocksArray.push(block)
    for (let state = block.minStateId; state <= block.maxStateId; state++) {
      if (registry.blocksByStateId[state]) throw new Error(`Block state ${state} of ${block.name} is already taken by ${registry.blocksByStateId[state].name}`)
      registry.blocksByStateId[state] = block
    }
  }
  for (const source of data.items) {
    if (registry.items[source.id] || registry.itemsByName[source.name]) throw new Error(`Item ${source.name} (id ${source.id}) collides with an existing item`)
    const item = { ...source }
    registry.items[item.id] = item
    registry.itemsByName[item.name] = item
    registry.itemsArray.push(item)
  }
  for (const source of data.entities) {
    if (registry.entities[source.id] || registry.entitiesByName[source.name]) throw new Error(`Entity ${source.name} (id ${source.id}) collides with an existing entity type`)
    const entity = { ...source }
    registry.entities[entity.id] = entity
    registry.entitiesByName[entity.name] = entity
    registry.entitiesArray.push(entity)
  }
  registry.blocksArray[MERGED] = fingerprint
  return counts
}

const SYNCED = [
  { registry: 'minecraft:block', key: 'blocks', table: 'blocksByName' },
  { registry: 'minecraft:item', key: 'items', table: 'itemsByName' },
  { registry: 'minecraft:entity_type', key: 'entities', table: 'entitiesByName' }
]

/**
 * Compare the export, and mineflayer's vanilla tables, with the ids the server sent during login.
 * @param registries Map<registry name, Map<entry name, id>> from the NeoForge negotiation
 * @returns a list of human-readable problems; empty when everything agrees
 */
function verify (registry, data, registries) {
  const problems = []
  for (const { registry: name, key, table } of SYNCED) {
    const server = registries.get(name)
    if (!server) { problems.push(`The server did not send its ${name} ids`); continue }
    const exported = new Map(data[key].map(entry => [entry.name, entry.id]))
    let vanillaWrong = 0
    for (const [entry, id] of server) {
      if (entry.startsWith('minecraft:')) {
        const known = registry[table][entry.slice('minecraft:'.length)]
        if (!known || known.id !== id) vanillaWrong++
      } else if (!exported.has(entry)) problems.push(`${entry} is on the server but not in the registry export`)
      else if (exported.get(entry) !== id) problems.push(`${entry} has id ${id} on the server but ${exported.get(entry)} in the registry export`)
    }
    for (const entry of exported.keys()) if (!server.has(entry)) problems.push(`${entry} is in the registry export but not on the server`)
    if (vanillaWrong) problems.push(`${vanillaWrong} vanilla entries of ${name} have different ids on the server than in mineflayer's tables`)
  }
  return problems
}

module.exports = { load, inject, verify, DEFAULT_FILE }
