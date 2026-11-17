// The mod's own interface, as a bot sees it: its weather channel and the /survival commands every player has.
const { Reader } = require('./wire')

/** The once-a-second local weather the server sends each player (spawn_9b5fbb9:weather). */
function decodeWeather (buffer) {
  const reader = new Reader(buffer)
  const dimension = reader.string(); const profile = reader.string(); const calendar = Number(reader.long())
  const position = { x: reader.int(), y: reader.int(), z: reader.int() }
  return { dimension, profile, calendar, position, temperature: reader.float(), rain: reader.float(), wind: reader.float(), snow: reader.bool() }
}

/** Java prints NaN and Infinity as bare words; JSON has no such thing. */
function parseJson (text) {
  return JSON.parse(text.replace(/([:[,]\s*)-?(?:NaN|Infinity)\b/g, '$1null'))
}

/**
 * The whole text of a chat message. Mineflayer's own string form stops at 4,096 characters, and the body
 * report is longer than that.
 */
function fullText (message) {
  const walk = part => {
    if (part == null) return ''
    if (typeof part === 'string' || typeof part === 'number') return String(part)
    if (Array.isArray(part)) return part.map(walk).join('')
    if (part.translate !== undefined) return null // needs the language table: leave it to mineflayer
    const own = part.text ?? part[''] ?? ''
    const rest = (part.extra || []).map(walk)
    return rest.includes(null) ? null : own + rest.join('')
  }
  return walk(message.json) ?? message.toString()
}

function install (bot, { commandTimeout = 5000 } = {}) {
  let queue = Promise.resolve()

  /**
   * Send a command and resolve with the first chat line that `accept` takes. Commands run one at a time
   * so two replies cannot be confused.
   */
  function command (text, accept = () => true, timeout = commandTimeout) {
    const run = () => new Promise((resolve, reject) => {
      const timer = setTimeout(() => { bot.removeListener('message', listen); reject(new Error(`No reply to "${text}" within ${timeout} ms`)) }, timeout)
      function listen (chat) {
        const message = fullText(chat)
        if (!accept(message)) return
        clearTimeout(timer); bot.removeListener('message', listen); resolve(message)
      }
      bot.on('message', listen)
      bot.chat(text.startsWith('/') ? text : '/' + text)
    })
    const result = queue.then(run, run)
    queue = result.catch(() => {})
    return result
  }
  const json = message => message.trimStart().startsWith('{')

  bot.survival = {
    /** Latest local weather, or null before the first report. */
    weather: null,
    command,
    /** The bot's own body: needs, temperature, capacities, anatomy, clothing, conditions, skills. */
    async query () { return parseJson(await command('/survival json', json)) },
    /** Air temperature, roof, fire and rain where the bot stands. */
    async climate () { return parseJson(await command('/survival climate', json)) },
    /**
     * A body action: eat, drink, read, play, clothe, undress, mend, treat, social, feed, give_water.
     * @param target optional player name or entity selector
     * @returns the server's one-line answer
     */
    act (action, target) { return command(`/survival act ${action}${target ? ' ' + target : ''}`) }
  }

  bot._client.on('custom_payload', packet => {
    if (packet.channel !== 'spawn_9b5fbb9:weather') return
    try { bot.survival.weather = decodeWeather(packet.data); bot.emit('survival_weather', bot.survival.weather) } catch (error) { bot.emit('error', error) }
  })
}

module.exports = { install, decodeWeather, parseJson, fullText }
