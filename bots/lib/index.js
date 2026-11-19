// A mineflayer bot that joins a Spawn server as an ordinary player.
const mineflayer = require('mineflayer')
const neoforge = require('./neoforge')
const registry = require('./registry')
const survival = require('./survival')

const VERSION = '26.3'
// The play channels the mod registers as required: survival/SurvivalNetwork and environment/WeatherNetwork
// (version 4), and the tech tree's journal, discovery and ledger channels in network/ServerNetwork (version 1).
const TECH_TREE_TO_SERVER = ['open_journal', 'discover', 'answer', 'craft', 'cancel_craft', 'write_treatise', 'prospect', 'request_ledger']
const TECH_TREE_TO_CLIENT = ['knowledge_sync', 'journal_status', 'quiz', 'craft_progress', 'station_status', 'ledger_report']
const MOD_CHANNELS = {
  play: [
    { id: 'spawn_9b5fbb9:state', version: '4', flow: 'clientbound', optional: false },
    { id: 'spawn_9b5fbb9:weather', version: '4', flow: 'clientbound', optional: false },
    ...TECH_TREE_TO_SERVER.map(name => ({ id: 'spawn_9b5fbb9:' + name, version: '1', flow: 'serverbound', optional: false })),
    ...TECH_TREE_TO_CLIENT.map(name => ({ id: 'spawn_9b5fbb9:' + name, version: '1', flow: 'clientbound', optional: false }))
  ]
}

/**
 * Create a bot. Takes mineflayer's options (host, port, username, ...) plus:
 * @param options.registryFile path of a registry export other than the bundled one
 * @param options.strictRegistry end the bot when the server's ids disagree with the export (default true)
 * @param options.commandTimeout milliseconds to wait for a /survival reply (default 5000)
 * @param options.commandSettle milliseconds to wait before sending a command, so earlier packets land first (default 150)
 * @param options.commandLimit pacing that keeps the bot inside the server's command spam limit: false where the
 *   server has none, or an object with any of `ceiling`, `drainPerSecond`, `safety`, `fallbackTicksPerSecond`
 *   (see lib/survival.js). A setting that cannot work throws here.
 */
function createBot (options = {}) {
  const { registryFile, strictRegistry = true, commandTimeout, commandSettle, commandLimit, ...rest } = options
  survival.readLimit(commandLimit) // a setting that cannot work throws here, before anything connects
  const data = registry.load(registryFile)
  const bot = mineflayer.createBot({ auth: 'offline', ...rest, version: VERSION })
  // With an explicit version the data tables exist as soon as createBot returns, before any plugin loads.
  if (!bot.registry) throw new Error('mineflayer did not build its data tables synchronously')
  const added = registry.inject(bot.registry, data)
  const session = neoforge.attach(bot._client, MOD_CHANNELS)
  bot.spawnMod = { added, session, verified: false, problems: [] }
  bot._client.once('neoforge_registries', registries => {
    const problems = registry.verify(bot.registry, data, registries)
    bot.spawnMod.problems = problems
    bot.spawnMod.verified = problems.length === 0
    if (!problems.length) return
    const shown = problems.slice(0, 8).join('; ') + (problems.length > 8 ? `; and ${problems.length - 8} more` : '')
    const error = new Error(`The server's ids do not match the registry export (run /survival export_registry and copy the file): ${shown}`)
    bot.emit('error', error)
    if (strictRegistry) bot.end('registry mismatch')
  })
  bot._client.on('neoforge_failed', reason => bot.emit('error', new Error('The server refused the channel list: ' + reason)))
  survival.install(bot, { commandTimeout, commandSettle, commandLimit })
  return bot
}

module.exports = { createBot, neoforge, registry, survival, rcon: require('./rcon'), MOD_CHANNELS, VERSION }
