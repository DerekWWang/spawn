// How many bots can one server carry? Adds bots in steps and reads the server's tick time at each.
//
//   node test/load.js --properties ../run/server.properties [options]
//
//   --steps 1,5,10,20,40   bot counts to measure at, in order
//   --mode idle|walk       stand still, or wander (default idle)
//   --spread               move every bot to its own stretch of country, so no two share loaded chunks
//   --origin 8000,8000     where the spread grid starts (default 8000,8000)
//   --per-process 40       bots per Node process; more bots start more processes
//   --settle 20            seconds to wait after the world stops loading before measuring
//   --pid 1234             the server's process id, to report its processor time and memory as well
//   --prefix Load          bot names are <prefix>001, <prefix>002, ...
//   --out results.json     also write the results as JSON
//
// The server's budget is 50 ms per tick. Past that it falls behind real time.
// Needs RCON enabled on the server; the bots themselves need no rights.
const fs = require('fs')
const { execFileSync, fork } = require('child_process')
const { monitorEventLoopDelay } = require('perf_hooks')

const args = {}
for (let i = 2; i < process.argv.length; i++) {
  const key = process.argv[i].replace(/^--/, '')
  const next = process.argv[i + 1]
  if (next === undefined || next.startsWith('--')) args[key] = true
  else { args[key] = next; i++ }
}
const prefix = args.prefix || 'Load'
const mode = args.mode || 'idle'
const host = args.host || '127.0.0.1'
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

if (args.worker) worker(); else main().catch(error => { console.error('FAIL', error); process.exit(1) })

// ---------------------------------------------------------------- a process holding some of the bots

function worker () {
  const { createBot } = require('../lib')
  const port = Number(args.port)
  const bots = []
  let dropped = 0
  const lag = monitorEventLoopDelay({ resolution: 10 }); lag.enable()

  function wander (bot) {
    const turn = () => {
      if (!bots.includes(bot)) return
      bot.look(Math.random() * Math.PI * 2, 0).catch(() => {})
      bot.setControlState('forward', true)
      bot.setControlState('jump', true)
      setTimeout(turn, 3000 + Math.random() * 3000)
    }
    turn()
  }
  function join (index) {
    return new Promise(resolve => {
      const name = prefix + String(index + 1).padStart(3, '0')
      const bot = createBot({ host, port, username: name, hideErrors: true })
      const timer = setTimeout(() => { try { bot.end() } catch {} ; resolve({ ok: false, name, why: 'did not spawn within 60 s' }) }, 60000)
      bot.on('error', () => {})
      bot.once('kicked', reason => console.log(name, 'kicked:', JSON.stringify(reason).slice(0, 200)))
      bot.once('end', () => { if (bots.includes(bot)) { dropped++; bots.splice(bots.indexOf(bot), 1) } })
      bot.once('spawn', () => { clearTimeout(timer); bots.push(bot); if (mode === 'walk') wander(bot); resolve({ ok: true, name }) })
    })
  }
  const columns = bot => { try { return bot.world.getColumns().length } catch { return 0 } }

  process.on('message', async message => {
    if (message.type === 'join') process.send({ type: 'joined', id: message.id, ...(await join(message.index)) })
    if (message.type === 'stats') {
      const cpu = process.cpuUsage()
      process.send({ type: 'stats', id: message.id, bots: bots.length, dropped, columns: bots.reduce((sum, bot) => sum + columns(bot), 0), cpuSeconds: (cpu.user + cpu.system) / 1e6, memoryBytes: process.memoryUsage().rss, loopP99Ms: lag.percentile(99) / 1e6 })
      lag.reset()
    }
    if (message.type === 'quit') { for (const bot of [...bots]) { try { bot.quit() } catch {} } setTimeout(() => process.exit(0), 800) }
  })
}

// ---------------------------------------------------------------- the measurement

async function main () {
  if (!args.properties) { console.error('usage: node test/load.js --properties <server.properties> [--steps 1,5,10] [--mode idle|walk] [--spread]'); process.exit(2) }
  const rcon = require('../lib/rcon')
  const server = rcon.readProperties(fs.readFileSync(args.properties, 'utf8'))
  const steps = String(args.steps || '1,5,10,20,40').split(',').map(Number)
  const settle = Number(args.settle || 20) * 1000
  const perProcess = Number(args['per-process'] || 40)
  const [originX, originZ] = String(args.origin || '8000,8000').split(',').map(Number)
  // View distance 10 loads a square 21 chunks (336 blocks) wide; 448 apart leaves room for the 48-block
  // scatter that finds dry land.
  const SPACING = 448
  const ROW = 8

  const workers = []
  let finished = false
  let nextId = 1
  const waiting = new Map()
  function workerFor (index) {
    const slot = Math.floor(index / perProcess)
    if (!workers[slot]) {
      const child = fork(__filename, ['--worker', '--port', String(server.port), '--host', host, '--mode', mode, '--prefix', prefix], { silent: true, execArgv: ['--max-old-space-size=8192'] })
      const relay = stream => { let rest = ''; stream.on('data', chunk => { const lines = (rest + chunk).split('\n'); rest = lines.pop(); for (const line of lines) if (line.trim() && !/partial packet|Chunk size is/.test(line)) console.log(line) }) }
      relay(child.stdout); relay(child.stderr)
      child.on('exit', code => { if (!finished) { console.error(`A bot process stopped unexpectedly (exit code ${code}); the measurement is void.`); process.exit(1) } })
      child.on('message', message => { const resolve = waiting.get(message.id); if (resolve) { waiting.delete(message.id); resolve(message) } })
      workers[slot] = child
    }
    return workers[slot]
  }
  const ask = (child, message) => new Promise(resolve => { const id = nextId++; waiting.set(id, resolve); child.send({ ...message, id }) })
  async function stats () {
    const all = await Promise.all(workers.filter(Boolean).map(child => ask(child, { type: 'stats' })))
    const sum = key => all.reduce((total, s) => total + s[key], 0)
    return { bots: sum('bots'), dropped: sum('dropped'), columns: sum('columns'), cpuSeconds: sum('cpuSeconds'), memoryBytes: sum('memoryBytes'), loopP99Ms: Math.max(0, ...all.map(s => s.loopP99Ms)), processes: all.length }
  }

  /** Wait until the bots have stopped receiving new chunks for ten seconds. */
  async function worldLoaded (limit = 900000) {
    const end = Date.now() + limit
    let last = -1; let since = Date.now()
    while (Date.now() < end) {
      const now = (await stats()).columns
      if (now !== last) { last = now; since = Date.now() } else if (Date.now() - since >= 10000) return now
      await sleep(1000)
    }
    return last
  }

  function serverProcess () {
    if (!args.pid) return null
    try {
      if (process.platform === 'win32') {
        const out = execFileSync('powershell', ['-NoProfile', '-Command', `$p=Get-Process -Id ${Number(args.pid)}; $p.TotalProcessorTime.TotalSeconds; $p.WorkingSet64`], { encoding: 'utf8' }).trim().split(/\s+/)
        return { cpuSeconds: Number(out[0]), memoryBytes: Number(out[1]) }
      }
      const out = execFileSync('ps', ['-o', 'cputimes=,rss=', '-p', String(Number(args.pid))], { encoding: 'utf8' }).trim().split(/\s+/)
      return { cpuSeconds: Number(out[0]), memoryBytes: Number(out[1]) * 1024 }
    } catch { return null }
  }
  function parseTick (reply) {
    const number = pattern => Number((reply.match(pattern) || [])[1])
    return { average: number(/Average time per tick: ([\d.]+)ms/), p50: number(/P50: ([\d.]+)ms/), p95: number(/P95: ([\d.]+)ms/), p99: number(/P99: ([\d.]+)ms/) }
  }

  const console_ = await rcon.connect({ host, port: server.rconPort, password: server.rconPassword })

  async function measure (asked) {
    const before = await stats(); const proc0 = serverProcess(); const t0 = Date.now()
    const samples = []
    for (let i = 0; i < 6; i++) { // each `tick query` covers the last 100 ticks, five seconds
      await sleep(5200)
      samples.push(parseTick(await console_.send('tick query')))
    }
    const seconds = (Date.now() - t0) / 1000
    const after = await stats(); const proc1 = serverProcess()
    const count = async selector => Number(((await console_.send(`execute if entity ${selector}`)).match(/count: (\d+)/i) || [0, 0])[1])
    const entities = await count('@e'); const itemEntities = await count('@e[type=minecraft:item]')
    const daytime = Number(((await console_.send('time query day')).match(/at (\d+)/) || [])[1])
    const mean = key => samples.reduce((sum, s) => sum + s[key], 0) / samples.length
    return {
      bots: after.bots,
      asked,
      chunksHeldByBots: after.columns,
      entities,
      itemEntities,
      daytime,
      tickMs: +mean('average').toFixed(2),
      tickP95Ms: +mean('p95').toFixed(2),
      tickP99WorstMs: Math.max(...samples.map(s => s.p99)),
      serverCores: proc0 && proc1 ? +((proc1.cpuSeconds - proc0.cpuSeconds) / seconds).toFixed(2) : null,
      serverMemoryMb: proc1 ? Math.round(proc1.memoryBytes / 1048576) : null,
      clientProcesses: after.processes,
      clientCores: +((after.cpuSeconds - before.cpuSeconds) / seconds).toFixed(3),
      clientMemoryMb: Math.round(after.memoryBytes / 1048576),
      clientLoopP99Ms: +after.loopP99Ms.toFixed(1)
    }
  }

  const results = []
  console.log(`mode ${mode}${args.spread ? ', spread out' : ', together at spawn'}; steps ${steps.join(', ')}; ${perProcess} bots per process`)
  let next = 0
  let unplaced = 0
  for (const count of steps) {
    for (let live = (await stats()).bots; live < count && next < count + 40; live = (await stats()).bots) {
      const batch = []
      for (let k = 0; k < 5 && live + batch.length < count; k++) { const index = next++; batch.push(ask(workerFor(index), { type: 'join', index }).then(reply => ({ ...reply, index }))) }
      for (const reply of await Promise.all(batch)) {
        if (!reply.ok) { console.log(reply.name, reply.why); continue }
        if (args.spread) {
          const x = originX + (reply.index % ROW) * SPACING; const z = originZ + Math.floor(reply.index / ROW) * SPACING
          // Dry land within 48 blocks keeps the areas apart; open sea gets a wider search before giving up.
          let placed = false
          for (const range of [48, 160, 400]) {
            if (/Spread 1/.test(await console_.send(`spreadplayers ${x} ${z} 0 ${range} false ${reply.name}`))) { placed = true; break }
          }
          if (!placed) { unplaced++; console.log(reply.name, 'found no dry land near', x, z, 'and stays at spawn') }
        }
      }
    }
    // Wandering bots never stop receiving chunks, so give them a fixed half minute instead.
    const chunks = mode === 'walk' ? (await sleep(30000), (await stats()).columns) : await worldLoaded()
    await sleep(settle)
    const result = { ...(await measure(count)), ...(args.spread ? { stayedAtSpawn: unplaced } : {}) }
    results.push(result)
    console.log(JSON.stringify({ ...result, chunksAtSettle: chunks }))
    if (result.tickMs > 60) { console.log('The server is past its 50 ms budget; stopping here.'); break }
  }

  if (args.out) fs.writeFileSync(args.out, JSON.stringify({ mode, spread: !!args.spread, perProcess, results }, null, 2))
  finished = true
  for (const child of workers.filter(Boolean)) child.send({ type: 'quit' })
  console_.close()
  await sleep(1500)
  process.exit(0)
}
