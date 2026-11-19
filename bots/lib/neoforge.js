// NeoForge's login negotiation, spoken by a client that is not NeoForge.
//
// During the configuration phase a NeoForge server asks every client which mod channels it has
// ("neoforge:register"). A vanilla client ignores the question and is refused. A client that answers is
// treated as NeoForge and is then put through NeoForge's own setup steps, two of which wait for a reply.
// This module answers the question, declares the channels the server insists on, and sends those replies.
const { varint, string, Reader } = require('./wire')

// ConnectionProtocol and PacketFlow ordinals, as NeoForge writes them.
const PHASE = { play: 1, configuration: 4 }
const FLOW = { serverbound: 0, clientbound: 1 }

// NeoForge 26.3's own channels (all registered as version "1" and optional).
// A NeoForge-type connection is sent the play ones whether or not it asked, so they must be declared.
const NEOFORGE_PLAY = ['advanced_add_entity', 'advanced_open_screen', 'auxiliary_light_data', 'registry_data_map_sync',
  'advanced_container_set_data', 'recipe_content', 'sync_attachments']
  .map(name => ({ id: 'neoforge:' + name, version: '1', flow: 'clientbound', optional: true }))
const NEOFORGE_CONFIGURATION = [
  { id: 'neoforge:extensible_enum_data', version: '1', flow: 'clientbound', optional: true },
  { id: 'neoforge:extensible_enum_ack', version: '1', flow: 'serverbound', optional: true },
  { id: 'neoforge:frozen_registry_sync_start', version: '1', flow: 'clientbound', optional: true },
  { id: 'neoforge:frozen_registry', version: '1', flow: 'clientbound', optional: true },
  { id: 'neoforge:frozen_registry_sync_completed', version: '1', flow: null, optional: true }
]

function encodeChannel ({ id, version, flow, optional }) {
  const direction = flow ? Buffer.concat([Buffer.from([1]), varint(FLOW[flow])]) : Buffer.from([0])
  return Buffer.concat([string(id), string(version), direction, Buffer.from([optional ? 1 : 0])])
}

/** The reply to "neoforge:register": map<phase, set<channel>>. */
function encodeChannels (channels) {
  const phases = Object.keys(channels)
  const parts = [varint(phases.length)]
  for (const phase of phases) {
    if (!(phase in PHASE)) throw new Error('Unknown connection phase: ' + phase)
    parts.push(varint(PHASE[phase]), varint(channels[phase].length), ...channels[phase].map(encodeChannel))
  }
  return Buffer.concat(parts)
}

function decodeChannels (buffer) {
  const reader = new Reader(buffer)
  const names = Object.fromEntries(Object.entries(PHASE).map(([name, ordinal]) => [ordinal, name]))
  const flows = Object.fromEntries(Object.entries(FLOW).map(([name, ordinal]) => [ordinal, name]))
  const channels = {}
  for (let phases = reader.varint(); phases > 0; phases--) {
    const list = channels[names[reader.varint()]] = []
    for (let count = reader.varint(); count > 0; count--) {
      const id = reader.string(); const version = reader.string()
      const flow = reader.bool() ? flows[reader.varint()] : null
      list.push({ id, version, flow, optional: reader.bool() })
    }
  }
  return channels
}

/** One frozen registry: its name and the id of every entry. Aliases follow the ids and are not needed. */
function decodeFrozenRegistry (buffer) {
  const reader = new Reader(buffer)
  const registry = reader.string()
  const ids = new Map()
  for (let count = reader.varint(); count > 0; count--) { const id = reader.varint(); ids.set(reader.string(), id) }
  return { registry, ids }
}

/**
 * Carry a minecraft-protocol client through NeoForge's negotiation.
 * @param client the bot's protocol client (bot._client)
 * @param modChannels channels the server's mods require, as { play: [...], configuration: [...] }
 * @returns an object whose `registries` map fills with the server's registry ids as they arrive
 */
function attach (client, modChannels = {}) {
  const channels = {
    play: [...(modChannels.play || []), ...NEOFORGE_PLAY],
    configuration: [...(modChannels.configuration || []), ...NEOFORGE_CONFIGURATION]
  }
  const session = { registries: new Map(), negotiated: false, failure: null }
  const send = (channel, data) => client.write('custom_payload', { channel, data })
  client.on('custom_payload', packet => {
    switch (packet.channel) {
      case 'neoforge:register':
        // An empty query from the server; the client's channel list goes back on the same channel.
        if (client.state === 'configuration' && !session.negotiated) { session.negotiated = true; send('neoforge:register', encodeChannels(channels)) }
        break
      case 'neoforge:modded_network_setup_failed':
        session.failure = packet.data.toString('utf8').replace(/[^\x20-\x7e]+/g, ' ').trim()
        client.emit('neoforge_failed', session.failure)
        break
      case 'neoforge:frozen_registry': {
        // A payload that cannot be read is reported through the client's `error` event, which mineflayer
        // passes on as the bot's. The registry stays out of `session.registries`, so the id check at
        // the end of the sync reports it as not sent.
        let decoded
        try { decoded = decodeFrozenRegistry(packet.data) } catch (error) {
          client.emit('error', new Error('Could not read a neoforge:frozen_registry payload from the server: ' + error.message, { cause: error }))
          break
        }
        session.registries.set(decoded.registry, decoded.ids)
        break
      }
      case 'neoforge:frozen_registry_sync_completed':
        client.emit('neoforge_registries', session.registries)
        send('neoforge:frozen_registry_sync_completed', Buffer.alloc(0))
        break
      case 'neoforge:extensible_enum_data':
        // The server waits for this before it lets the player in.
        send('neoforge:extensible_enum_ack', Buffer.alloc(0))
        break
    }
  })
  // Note: the configuration ping that follows the query is answered by mineflayer itself. A second pong
  // makes the server start its setup twice and drop the connection.
  return session
}

module.exports = { attach, encodeChannels, decodeChannels, decodeFrozenRegistry, NEOFORGE_PLAY, NEOFORGE_CONFIGURATION }
