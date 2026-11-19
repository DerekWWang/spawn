// What does the mod's own interface cost the server when many bots use it at once?
//
//   node test/active.js --properties ../run/server.properties [--bots 20] [--seconds 20] [--interval 1300] [--unlimited]
//
// --interval is the milliseconds between one bot's calls. Faster than about one a second needs
// --unlimited and a server with command-spam-threshold-seconds=0, or the server disconnects the bots.
//
// Joins the bots together, then runs one phase at a time and reads the server's tick time in each:
// standing idle, polling their body, polling the climate, surveying resources, and working a hand
// craft continuously. The operator console (RCON) hands each bot stone to knap; nothing else.
const fs = require('fs')
const { createBot, rcon } = require('../lib')

const args = {}
for (let i = 2; i < process.argv.length; i++) {
  const key = process.argv[i].replace(/^--/, ''); const next = process.argv[i + 1]
  if (next === undefined || next.startsWith('--')) args[key] = true; else { args[key] = next; i++ }
}
if (!args.properties) { console.error('usage: node test/active.js --properties <server.properties> [--bots 20] [--seconds 20]'); process.exit(2) }
const server = rcon.readProperties(fs.readFileSync(args.properties, 'utf8'))
const host = args.host || '127.0.0.1'
const COUNT = Number(args.bots || 20)
const SECONDS = Number(args.seconds || 20)
const prefix = args.prefix || 'Busy'
const INTERVAL = Number(args.interval || 1300)
const log = console.log
console.log = (...a) => { if (!/partial packet|Chunk size is/.test(String(a[0]))) log(...a) }
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

async function main () {
  const con = await rcon.connect({ host, port: server.rconPort, password: server.rconPassword, timeout: 20000 })
  const bots = []
  for (let i = 0; i < COUNT; i++) {
    const name = prefix + String(i + 1).padStart(3, '0')
    const bot = createBot({ host, port: server.port, username: name, hideErrors: true, commandTimeout: 15000, ...(args.unlimited ? { commandLimit: false, commandSettle: 0 } : {}) })
    bot.once('kicked', reason => log(name, 'kicked:', JSON.stringify(reason).slice(0, 160)))
    bot.on('error', () => {})
    await new Promise((resolve, reject) => { bot.once('spawn', resolve); setTimeout(() => reject(new Error(name + ' did not spawn')), 60000) })
    bots.push(bot)
  }
  await sleep(8000)
  const tick = async () => {
    const samples = []
    for (let i = 0; i < Math.max(1, Math.round(SECONDS / 5)); i++) { await sleep(5200); const q = await con.send('tick query'); samples.push([Number(q.match(/Average time per tick: ([\d.]+)/)[1]), Number(q.match(/P95: ([\d.]+)/)[1])]) }
    return { tickMs: +(samples.reduce((s, x) => s + x[0], 0) / samples.length).toFixed(2), p95Ms: +(samples.reduce((s, x) => s + x[1], 0) / samples.length).toFixed(2) }
  }

  const results = []
  /** Every bot repeats `work` every `everyMs` while the tick is sampled. */
  async function phase (label, everyMs, work) {
    let running = true; let calls = 0; let failures = 0; let bytes = 0
    const loops = bots.map(async (bot, index) => {
      await sleep(index * everyMs / bots.length) // spread the bots across the interval
      while (running) {
        const started = Date.now()
        if (work) { try { const out = await work(bot); calls++; bytes += typeof out === 'string' ? out.length : JSON.stringify(out || '').length } catch { failures++ } }
        await sleep(Math.max(0, everyMs - (Date.now() - started)))
      }
    })
    await sleep(3000)
    const t0 = Date.now(); const c0 = calls
    const measured = await tick()
    const perSecond = (calls - c0) / ((Date.now() - t0) / 1000)
    running = false; await Promise.all(loops)
    const row = { phase: label, ...measured, callsPerSecond: +perSecond.toFixed(1), averageReplyBytes: calls ? Math.round(bytes / calls) : 0, failures }
    results.push(row); log(JSON.stringify(row))
  }

  log(`${bots.length} bots together; each phase sampled for about ${SECONDS} s`)
  await phase('idle', 1000, null)
  await phase('body report', INTERVAL, bot => bot.survival.query())
  await phase('climate', INTERVAL, bot => bot.survival.climate())
  await phase('resource survey, radius 12', INTERVAL, bot => bot.survival.resources())
  await phase('resource survey, radius 16', INTERVAL, bot => bot.survival.resources(16))
  for (const bot of bots) await con.send(`give ${bot.username} spawn_9b5fbb9:loose_stone 64`)
  await sleep(1500)
  await phase('hand craft (knapping), restarted as it finishes', Math.max(INTERVAL, 1300), bot => bot.survival.command('/survival craft spawn_9b5fbb9:knap'))
  await phase('idle again', 1000, null)

  for (const bot of bots) await con.send(`clear ${bot.username}`)
  if (args.out) fs.writeFileSync(args.out, JSON.stringify({ bots: bots.length, results }, null, 2))
  for (const bot of bots) { try { bot.quit() } catch {} }
  con.close(); await sleep(800); process.exit(0)
}
main().catch(error => { console.error('FAIL', error); process.exit(1) })
