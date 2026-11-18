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

/** Lines the server sends everyone as system chat. They are never the answer to a command. */
function isAnnouncement (message) {
  const key = message.translate ?? message.json?.translate
  return typeof key === 'string' && /^(?:multiplayer\.player\.(?:joined|left)|death\.|chat\.type\.advancement\.)/.test(key)
}

// What each JSON report looks like, so that one is never taken for another.
// /survival json: SurvivalCommands.json. /survival climate: EnvironmentActions.observe, which answers
// { error } alone for an actor it cannot observe. /survival resources: ResourceActions.discover.
const SHAPES = {
  body: report => 'hunger' in report && 'thirst' in report && 'anatomy' in report,
  climate: report => ('temperatureC' in report && 'roof' in report) ||
    (typeof report.error === 'string' && !('resources' in report) && !('hunger' in report)),
  resources: report => Array.isArray(report.resources)
}
// What the server says to every command of a dead player (SurvivalEvents), instead of running it.
const DEAD = 'The dead take no part in the world.'
const UNCONSCIOUS = 'You are unconscious.'

// The server counts 20 against a player for every command and takes 1 off on each of its ticks. A player
// who is not an operator is disconnected for spam when the count reaches 20 x command-spam-threshold-seconds
// (200 by default): at twenty ticks a second, about one command a second sustained, with a burst of nine.
// A server running slow ticks less often, so its count drains more slowly.
const COMMAND_COST = 20
const MAX_TICKS_PER_SECOND = 20
// How long after a command timed out its reply is still looked for, so the next command does not take it.
const LATE_REPLY_MS = 30000

/**
 * Pacing defaults.
 * ceiling: the highest count the bot lets itself reach (the server disconnects at 200).
 * drainPerSecond: null to follow the server's tick rate, or a fixed number taken off the count each second.
 * safety: the share of the observed ticks that is trusted, when following the tick rate.
 * fallbackTicksPerSecond: the tick rate assumed where it cannot be read: before the server's second time
 *   report, and while the world's clock is frozen.
 */
const DEFAULT_LIMIT = Object.freeze({ ceiling: 140, drainPerSecond: null, safety: 0.8, fallbackTicksPerSecond: 10 })

/** The pacing settings in full, or null where pacing is off. Throws on a setting that could never work. */
function readLimit (given) {
  if (given === false) return null
  if (given === undefined || given === null || given === true) return { ...DEFAULT_LIMIT }
  if (typeof given !== 'object' || Array.isArray(given)) {
    throw new TypeError(`commandLimit must be false or an object such as { ceiling: 140 }, not ${Array.isArray(given) ? 'an array' : typeof given + ' ' + String(given)}`)
  }
  const limit = { ...DEFAULT_LIMIT }
  for (const [key, value] of Object.entries(given)) {
    if (!(key in DEFAULT_LIMIT)) throw new TypeError(`Unknown commandLimit setting "${key}"; the settings are ${Object.keys(DEFAULT_LIMIT).join(', ')}`)
    if (value !== undefined) limit[key] = value
  }
  const positive = value => typeof value === 'number' && Number.isFinite(value) && value > 0
  const bad = (key, must) => new RangeError(`commandLimit.${key} must be ${must}, not ${String(limit[key])}`)
  if (!positive(limit.ceiling) || limit.ceiling < COMMAND_COST) throw bad('ceiling', `a finite number of at least ${COMMAND_COST}, which is what one command counts`)
  if (limit.drainPerSecond !== null && !positive(limit.drainPerSecond)) throw bad('drainPerSecond', 'a finite number above 0, or null to follow the server\'s tick rate')
  if (!positive(limit.safety) || limit.safety > 1) throw bad('safety', 'a number above 0 and at most 1')
  if (!positive(limit.fallbackTicksPerSecond) || limit.fallbackTicksPerSecond > MAX_TICKS_PER_SECOND) throw bad('fallbackTicksPerSecond', `a number above 0 and at most ${MAX_TICKS_PER_SECOND}`)
  return limit
}

/**
 * A model of the server's spam count for one player.
 *
 * With a fixed `drainPerSecond` the count falls by that much each second. Otherwise it falls with the
 * server's ticks, read from the world age in the time reports the server sends every twenty ticks:
 * `sample(age, now)` takes one. Between reports the last measured rate is assumed, for no more ticks than
 * one report covers, and the next report corrects the assumption; so a server that stalls stops the
 * count falling. More than twenty ticks a second is never assumed.
 * @param limit settings from readLimit
 * @param clock a function returning milliseconds
 */
function createPacer (limit, clock = Date.now) {
  const fixed = limit.drainPerSecond !== null
  let count = 0
  let countedAt = clock()
  let last = null // the latest time report: { age, at }
  let measured = null // ticks a second between the last two reports
  let stride = MAX_TICKS_PER_SECOND // ticks from one report to the next
  let ahead = 0 // ticks assumed since the latest report, which the next report confirms or corrects

  /** Bring the count up to `now`. */
  function settle (now) {
    const seconds = Math.max(0, now - countedAt) / 1000
    countedAt = Math.max(countedAt, now)
    if (fixed) { count = Math.max(0, count - seconds * limit.drainPerSecond); return }
    let ticks = seconds * (measured ?? limit.fallbackTicksPerSecond)
    if (last) { ticks = Math.min(ticks, Math.max(0, stride - ahead)); ahead += ticks }
    count = Math.max(0, count - ticks * limit.safety)
  }

  /** Take a time report: the world's age in ticks, and when it arrived. */
  function sample (age, now) {
    if (fixed || !Number.isFinite(age)) return
    if (!last || age < last.age) { settle(now); last = { age, at: now }; ahead = 0; return }
    const seconds = (now - last.at) / 1000
    if (!(seconds > 0)) return
    const aged = age - last.age
    // A report with an unchanged age comes from a frozen world (/tick freeze). The server's loop still
    // runs and still drains the count, but how fast cannot be read from the world's clock.
    const ticks = aged > 0
      ? Math.min(aged, MAX_TICKS_PER_SECOND * seconds)
      : Math.min(MAX_TICKS_PER_SECOND, limit.fallbackTicksPerSecond * seconds)
    count = Math.max(0, count + (ahead - ticks) * limit.safety)
    countedAt = now; ahead = 0
    measured = ticks / seconds
    stride = Math.max(MAX_TICKS_PER_SECOND, aged)
    last = { age, at: now }
  }

  /**
   * Ask to send one command now.
   * @returns 0 when it may go (and is counted), otherwise the milliseconds to wait before asking again
   */
  function admit (now) {
    settle(now)
    if (count + COMMAND_COST <= limit.ceiling) { count += COMMAND_COST; return 0 }
    if (!fixed && last && ahead >= stride) return 1000 // nothing more is assumed until the next report
    const perSecond = fixed ? limit.drainPerSecond : (measured ?? limit.fallbackTicksPerSecond) * limit.safety
    return Math.min(1000, Math.max(20, Math.ceil((count + COMMAND_COST - limit.ceiling) / perSecond * 1000) + 10))
  }

  return {
    admit,
    sample,
    /** The modelled count. */
    get count () { return count },
    /** Ticks a second between the last two time reports, or null before the second. */
    get ticksPerSecond () { return measured }
  }
}

/** A disconnect reason as text: mineflayer passes a string, or the chat component the server sent. */
function reasonText (reason) {
  if (reason == null) return ''
  if (typeof reason === 'string') return reason.slice(0, 300)
  try { return JSON.stringify(reason).slice(0, 300) } catch { return String(reason).slice(0, 300) }
}

/**
 * Add `bot.survival`.
 * @param options.commandTimeout milliseconds to wait for a reply (default 5000)
 * @param options.commandSettle milliseconds to wait before sending a command (default 150)
 * @param options.commandLimit pacing: false for none, or any of the settings in DEFAULT_LIMIT
 * @param options.clock a function returning milliseconds, for tests (default Date.now)
 */
function install (bot, options = {}) {
  const { commandTimeout = 5000, commandSettle = 150, clock = Date.now } = options
  const limit = readLimit(options.commandLimit)
  const pacer = limit && createPacer(limit, clock)

  const waiting = [] // commands not yet begun
  let active = null // the one command being settled, paced or awaited
  let timer = null // the one timer the active command holds
  let late = null // the last command that timed out, whose reply may still arrive: { expect, until }
  let kicked = null // the reason the server gave, if it disconnected the bot
  let closed = null // once the bot has disconnected: makes the error every call then gets

  function begin () {
    if (active || closed || !waiting.length) return
    active = waiting.shift()
    // The server runs a chat command ahead of game packets that arrived just before it (a change of held
    // item, a step), so a command sent straight after one can see the old state. Let them land first.
    timer = setTimeout(send, commandSettle)
  }

  function send () {
    const job = active
    // Wait until one more command keeps this bot inside the server's spam limit.
    const wait = pacer ? pacer.admit(clock()) : 0
    job.paced = wait > 0
    if (wait > 0) { timer = setTimeout(send, wait); return }
    job.sent = true
    timer = setTimeout(expire, job.timeout)
    try { bot.chat(job.text.startsWith('/') ? job.text : '/' + job.text) } catch (error) { finish(error) }
  }

  function expire () {
    const job = active
    // The reply may only be late. Remember what it looks like, so that it is dropped when it comes
    // rather than handed to the next command. A command that timed out because a late reply was
    // dropped in its place leaves nothing: if that guess was wrong, the error must not repeat.
    late = job.shadowed ? null : { expect: job.expect, until: clock() + LATE_REPLY_MS }
    finish(Object.assign(new Error(`No reply to "${job.text}" within ${job.timeout} ms`), { code: 'ETIMEDOUT' }))
  }

  function finish (error, value) {
    const job = active
    clearTimeout(timer); timer = null; active = null
    if (error) job.reject(error); else job.resolve(value)
    begin()
  }

  /**
   * Queue a command. `expect(text)` is shown each system chat line that arrives once the command has been
   * sent, and returns { value } to resolve, { error } to reject, or nothing to keep waiting.
   */
  function request (text, expect, timeout = commandTimeout) {
    const result = new Promise((resolve, reject) => {
      if (closed) { reject(closed(text)); return }
      waiting.push({ text, expect, timeout, resolve, reject, sent: false, paced: false, shadowed: false })
      begin()
    })
    // A caller that drops the promise must not bring down a process full of other bots when it rejects.
    // A caller that awaits it still gets the rejection.
    result.catch(() => {})
    return result
  }

  // Only system chat can answer a command: player chat and action-bar overlays never do.
  bot.on('message', (message, position) => {
    if (position !== 'system') return
    const awaited = active && active.sent ? active : null
    if (late && late.until < clock()) late = null
    if ((!awaited && !late) || isAnnouncement(message)) return
    const text = fullText(message)
    if (late) {
      let theirs = false
      try { theirs = !!late.expect(text) } catch { late = null }
      if (theirs) {
        late = null
        try { if (awaited && awaited.expect(text)) awaited.shadowed = true } catch {}
        return
      }
    }
    if (!awaited) return
    let outcome
    try { outcome = awaited.expect(text) } catch (error) { finish(error); return }
    if (!outcome) return
    late = null // replies come in the order the commands went: an older one cannot follow this
    if (outcome.error) finish(outcome.error); else finish(null, outcome.value)
  })

  if (pacer && limit.drainPerSecond === null) {
    bot.on('time', () => {
      const age = bot.time && bot.time.age
      if (typeof age !== 'number') return
      pacer.sample(age, clock())
      if (active && active.paced) { clearTimeout(timer); send() }
    })
  }

  bot.on('kicked', reason => { kicked = reason })
  bot.once('end', reason => {
    const why = kicked != null ? 'kicked: ' + reasonText(kicked) : reasonText(reason)
    closed = text => Object.assign(new Error(`The bot disconnected${why ? ' (' + why + ')' : ''}; no reply to "${text}"`), { code: 'EDISCONNECTED', reason: kicked ?? reason })
    clearTimeout(timer); timer = null; late = null
    const unanswered = active ? [active, ...waiting] : [...waiting]
    active = null; waiting.length = 0
    for (const job of unanswered) job.reject(closed(job.text))
  })

  /**
   * Send a command and resolve with the first system chat line that `accept` takes. Commands run one at
   * a time. The promise rejects when no such line comes within `timeout` or the bot disconnects.
   */
  function command (text, accept, timeout) {
    return request(text, accept ? message => (accept(message) ? { value: message } : null) : message => ({ value: message }), timeout)
  }

  /** Send a command that answers with a JSON report, and resolve with the report of that shape. */
  function report (text, shape) {
    return request(text, message => {
      if (message.trim() === DEAD) return { error: Object.assign(new Error(`The server refused "${text}": ${DEAD}`), { code: 'EDEAD' }) }
      if (message.trim() === UNCONSCIOUS) return { error: Object.assign(new Error(`The server refused "${text}": ${UNCONSCIOUS}`), { code: 'EUNCONSCIOUS' }) }
      if (!message.trimStart().startsWith('{')) return null
      let value
      try { value = parseJson(message) } catch { return null }
      return value !== null && typeof value === 'object' && shape(value) ? { value } : null
    })
  }

  bot.survival = {
    /** Latest local weather, or null before the first report. */
    weather: null,
    command,
    /** The bot's own body: needs, temperature, capacities, anatomy, clothing, conditions, skills. */
    query () { return report('/survival json', SHAPES.body) },
    /** Air temperature, roof, fire and rain where the bot stands. */
    climate () { return report('/survival climate', SHAPES.climate) },
    /**
     * What the bot can see to gather, fell or mine around it.
     * @param radius 1 to 16 blocks; the server's default is 12
     */
    resources (radius) {
      if (radius !== undefined && !(Number.isInteger(radius) && radius >= 1 && radius <= 16)) {
        const refused = Promise.reject(new RangeError(`The resource survey's radius is a whole number from 1 to 16, not ${String(radius)}`))
        refused.catch(() => {})
        return refused
      }
      return report('/survival resources' + (radius === undefined ? '' : ' ' + radius), SHAPES.resources)
    },
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

module.exports = { install, decodeWeather, parseJson, fullText, isAnnouncement, readLimit, createPacer, DEFAULT_LIMIT }
