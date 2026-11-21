// Stdio MCP server for the commander: one set of tools that directs every actor through the commander host.
// env: EVAL_PORT, DEADLINE (ms since epoch), ACTORS (comma-separated names)
const http = require('http')
const readline = require('readline')
const PORT = Number(process.env.EVAL_PORT || 47100)
const DEADLINE = Number(process.env.DEADLINE)
const ACTORS = (process.env.ACTORS || '').split(',').filter(Boolean)
const PAGE = 30000

const num = { type: 'number' }
const str = { type: 'string' }
const obj = (props, required = []) => ({ type: 'object', properties: props, required, additionalProperties: false })
const actor = { type: 'string', enum: ACTORS, description: 'who' }
const TASKS = {
  make: '{item, count?} come to hold `count` of an item by any way the world allows: the person weighs every recipe, station process, hand craft and natural source for it, gathers and makes the ingredients, sets down a crafting table or station if one is needed, and tries the next way when one fails; the result has a trace of what was tried',
  go: '{x, z, within?} walk there over dry land; never wades into water',
  go_to: '{name, within?} walk to another person',
  gather: '{what, count?, radius?} take the nearest blocks whose id contains `what` ("oak_log", "sand", "gravel"...) by whatever the world requires (timed hand-gathering, timed mining, or breaking), and pick up the drops; or {x, y, z} for one block',
  dig: '{x, y, z} take one block',
  craft: '{recipe, times?} craft one exact recipe id (e.g. "minecraft:oak_planks") with what is at hand; nothing is fetched (make does that)',
  drink: '{until?} drink until thirst reaches `until`: from a carried pot if there is one, else from the nearest shore standing on dry land (as the water policy allows)',
  eat: '{item?} eat an item from the inventory; without an item, eat the best food carried, or find or make some and eat it',
  place: '{item, x, y, z} place a block',
  build: '{item, cells: [[x,y,z],...]} place one item into many cells, lowest first',
  hunt: '{creature, seconds?} chase and strike the nearest creature of a kind (entity id, e.g. "pig") with whatever is in hand, then pick up drops; will not follow into water',
  give: '{item, n, to} walk to another person and hand them items',
  hold: '{item} hold an item in hand',
  use: '{item?, seconds?} use (right-click) the held item',
  use_on: '{x, y, z, item?} use the held item on a block (right-click the block)',
  act: '{action, target?} a body action: eat, feed, drink, read, play, clothe, undress, mend, treat, social',
  command: '{line} send any game command as this person (e.g. "/survival vessel fill") and return its full reply',
  say: '{text} say something aloud',
  follow: '{name, seconds?} keep near another person',
  wait: '{seconds?} stay put',
  pickup: '{radius?} pick up dropped items nearby'
}
const taskList = Object.entries(TASKS).map(([k, v]) => `  ${k} ${v}`).join('\n')
const step = obj({ task: { type: 'string', enum: Object.keys(TASKS) }, args: { type: 'object', description: 'the task\'s arguments' } }, ['task'])
const TOOLS = [
  ['team', 'Everyone at once: position, health, body (hunger, thirst, temperature, conditions), whether they are in water, what they hold and carry, what they are doing (and whether on your order, by themselves, or as their standing job), their queue, recent self-care, and how your last order to them ended; what the group has learned together (stations set down, foods and water that made someone ill); the time of day; and any news (alerts about people, and what was said aloud) since you last asked.', obj({}), () => ['team', {}]],
  ['order', 'Give one person an order. It replaces whatever they were doing, runs on its own while you think, and its result shows under `last` in team. `then` queues further steps that run in turn while each succeeds. Tasks and their args:\n' + taskList, obj({ actor, task: { type: 'string', enum: Object.keys(TASKS) }, args: { type: 'object', description: 'the task\'s arguments' }, then: { type: 'array', items: step, description: 'steps to run afterwards, in order' } }, ['actor', 'task']), a => ['order', a]],
  ['orders', 'Give several people orders at once: a list of {actor, task, args, then}.', obj({ list: { type: 'array', items: obj({ actor, task: { type: 'string', enum: Object.keys(TASKS) }, args: { type: 'object' }, then: { type: 'array', items: step } }, ['actor', 'task']) } }, ['list']), null],
  ['cancel', 'Stop what a person is doing and clear their queue.', obj({ actor }, ['actor']), a => ['cancel', a]],
  ['policy', 'Read or tune how a person looks after themselves (or "all"). Everyone always swims up and out of water, gets away from whatever hurts them, drinks when thirsty and eats when hungry (finding or making food if they carry none), pausing your order and taking it up again after; that is not optional. You can tune: drinkBelow/drinkUntil (thirst 25-80 at which they go and drink, and 60-100 to drink up to), eatBelow/eatUntil (hunger, same ranges), water ("safe-first": a carried pot, else open water, though once open water has made someone ill only when thirst is critical; "open": open water whatever is known), foods (preferred foods, best first), home ([x, z]) and roam (how far an idle person may be from home before walking back), idle (a standing order {task, args} they repeat whenever nothing else is queued, or null), onFail ("skip" to the next queued step, or "stop" the queue). Omit `set` to read.', obj({ actor: { type: 'string', enum: [...ACTORS, 'all'] }, set: { type: 'object' } }, ['actor']), a => ['policy', a]],
  ['look', 'Look through one person\'s eyes; this does not interrupt their order. what = ways (every way this person could come by item `name` from where they stand, open or shut and why), map (top-down map, radius 4-16), find (nearest blocks whose id contains `name`, radius up to 64), nearby (creatures, people, dropped items), survey (natural resources in sight, grouped by kind, radius 1-16), body (full body report), climate (weather and air), column (blocks from y=top down to y=bottom at x,z), score (the worth of the works of art and monuments made so far, and what each kind of work would be worth), command (any game command line as this person, e.g. "/help survival", "/survival planning", "/survival recipeinfo <recipe or item>", "/techtree list"; use `offset` to read past a long reply).', obj({ actor, what: { type: 'string', enum: ['ways', 'map', 'find', 'nearby', 'survey', 'body', 'climate', 'column', 'score', 'command'] }, radius: num, name: str, count: num, x: num, z: num, top: num, bottom: num, line: str, offset: num }, ['actor', 'what']), a => ['look', a]],
  ['wait', 'Let time pass until someone finishes an order or something happens to someone (at most `seconds`, up to 90), then see the team. Use this instead of asking team over and over.', obj({ seconds: num }), a => ['wait', a]]
]
const BY_NAME = Object.fromEntries(TOOLS.map(t => [t[0], t]))

function host (op, args) {
  return new Promise(resolve => {
    const req = http.request({ host: '127.0.0.1', port: PORT, method: 'POST', path: '/' }, res => {
      let data = ''; res.on('data', c => { data += c }); res.on('end', () => { try { resolve(JSON.parse(data)) } catch { resolve({ ok: false, error: data }) } })
    })
    req.on('error', error => resolve({ ok: false, error: 'host unreachable: ' + error.message }))
    req.end(JSON.stringify({ op, args }))
  })
}
function clock () {
  const left = DEADLINE - Date.now()
  if (left <= 0) return '[clock] Time is up. Stop giving orders now and write your final report.'
  return `[clock] ${Math.ceil(left / 60000)} min of the session left`
}
async function call (name, input) {
  if (Date.now() > DEADLINE) return { text: clock(), isError: false }
  const tool = BY_NAME[name]
  if (!tool) return { text: 'no such tool ' + name, isError: true }
  input = input || {}
  let reply
  if (name === 'orders') {
    const results = []
    for (const o of input.list || []) { const r = await host('order', o); results.push({ actor: o.actor, ...(r.ok ? r.result : { ok: false, why: r.error }) }) }
    reply = { ok: true, result: results }
  } else {
    const [op, args] = tool[3](input)
    const offset = Math.max(0, Number(args.offset || 0)); delete args.offset
    reply = await host(op, args)
    if (reply.ok && typeof reply.result === 'string' && reply.result.length > PAGE) {
      const total = reply.result.length
      reply.result = reply.result.slice(offset, offset + PAGE) + (offset + PAGE < total ? `\n[reply continues: ${total} characters in all; ask again with offset ${offset + PAGE}]` : '')
    }
  }
  let body = reply.ok ? reply.result : { error: reply.error }
  if (input.what === 'map' && reply.ok) body = `origin ${JSON.stringify(body.origin)}; ${body.note}\n${body.rows}\nlegend ${JSON.stringify(body.legend)}\nheights\n${body.heights}`
  let text = typeof body === 'string' ? body : JSON.stringify(body)
  if (text.length > PAGE + 2000) text = text.slice(0, PAGE) + `\n[cut: ${text.length} characters; narrow the request]`
  return { text: text + '\n' + clock(), isError: !reply.ok }
}

const send = message => process.stdout.write(JSON.stringify(message) + '\n')
readline.createInterface({ input: process.stdin }).on('line', async line => {
  let msg; try { msg = JSON.parse(line) } catch { return }
  const { id, method, params } = msg
  if (method === 'initialize') return send({ jsonrpc: '2.0', id, result: { protocolVersion: params.protocolVersion || '2025-06-18', capabilities: { tools: {} }, serverInfo: { name: 'command', version: '1.0.0' } } })
  if (method === 'tools/list') return send({ jsonrpc: '2.0', id, result: { tools: TOOLS.map(([name, description, inputSchema]) => ({ name, description, inputSchema })) } })
  if (method === 'tools/call') { const { text, isError } = await call(params.name, params.arguments); return send({ jsonrpc: '2.0', id, result: { content: [{ type: 'text', text }], isError } }) }
  if (method === 'ping') return send({ jsonrpc: '2.0', id, result: {} })
  if (id !== undefined) send({ jsonrpc: '2.0', id, error: { code: -32601, message: 'unknown method ' + method } })
})
