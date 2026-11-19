// A whole life, played by ordinary survival players against a running Spawn server.
//
//   node test/lifecycle.js --properties ../run/server.properties
//
// The bots have no operator rights. They gather, craft a stone pickaxe from nothing, mine, put a roof
// over their heads, breed cattle and hatch a chick, and one of them dies and stays dead. The operator
// console (RCON) is used only as a stage hand: it lays out the scene, hands over feed, and turns the
// calendar forward. Every step prints PASS or FAIL; the run exits 0 only if all pass.
const fs = require('fs')
const { createBot, rcon } = require('../lib')

const args = {}
for (let i = 2; i < process.argv.length; i++) {
  const key = process.argv[i].replace(/^--/, ''); const next = process.argv[i + 1]
  if (next === undefined || next.startsWith('--')) args[key] = true; else { args[key] = next; i++ }
}
if (!args.properties) { console.error('usage: node test/lifecycle.js --properties <server.properties>'); process.exit(2) }
const server = rcon.readProperties(fs.readFileSync(args.properties, 'utf8'))
const host = args.host || '127.0.0.1'
const MOD = 'spawn_9b5fbb9:'
const DAY = 24000

const log = console.log
console.log = (...a) => { if (!/partial packet|Chunk size is/.test(String(a[0]))) log(...a) }
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
async function until (what, check, timeout = 10000, every = 150) {
  const end = Date.now() + timeout
  for (;;) {
    const value = await check()
    if (value) return value
    if (Date.now() > end) throw new Error('timed out waiting for ' + what)
    await sleep(every)
  }
}

const results = []
let section = ''
async function step (name, run) {
  const started = Date.now()
  try {
    const detail = await run()
    results.push({ section, name, ok: true })
    log(`PASS  ${name}${detail ? '  — ' + detail : ''}  (${((Date.now() - started) / 1000).toFixed(1)} s)`)
    return true
  } catch (error) {
    results.push({ section, name, ok: false, error: error.message })
    log(`FAIL  ${name}  — ${error.message}`)
    return false
  }
}
const expect = (condition, message) => { if (!condition) throw new Error(message) }

function join (username, options = {}) {
  return new Promise((resolve, reject) => {
    const bot = createBot({ host, port: server.port, username, hideErrors: true, ...options })
    const timer = setTimeout(() => reject(new Error(username + ' did not spawn within 60 s')), 60000)
    bot.on('error', () => {})
    bot.once('kicked', reason => log(username, 'kicked:', JSON.stringify(reason).slice(0, 200)))
    bot.once('spawn', () => { clearTimeout(timer); resolve(bot) })
  })
}

async function main () {
  const con = await rcon.connect({ host, port: server.rconPort, password: server.rconPassword, timeout: 20000 })
  const op = async (...commands) => { let last = ''; for (const command of commands) last = await con.send(command); return last }
  const tick = async () => Number(((await con.send('tick query')).match(/Average time per tick: ([\d.]+)ms/) || [])[1])
  const count = async selector => Number(((await con.send(`execute if entity ${selector}`)).match(/count: (\d+)/i) || [0, 0])[1])
  async function advanceCalendar (days) {
    const now = Number(((await con.send('data get storage spawn_9b5fbb9:calendar sleepOffset')).match(/(-?\d+)L/) || [0, 0])[1])
    await con.send(`data modify storage spawn_9b5fbb9:calendar sleepOffset set value ${now + Math.round(days * DAY)}L`)
  }

  // ------------------------------------------------------------------ the stage
  await op('gamerule spawn_mobs false', 'weather clear', 'time set 1000', 'kill @e[type=!minecraft:player]')
  const bot = await join(args.name || 'Settler')
  await bot.waitForChunksToLoad()
  await sleep(1000)
  const O = bot.entity.position.floored()
  const Vec3 = O.constructor // mineflayer's own vector class, without requiring a package this one does not declare
  const at = (dx, dy, dz) => O.offset(dx, dy, dz)
  const xyz = v => `${v.x} ${v.y} ${v.z}`
  await op(
    `fill ${xyz(at(-7, -1, -7))} ${xyz(at(7, -1, 7))} minecraft:grass_block`,
    `fill ${xyz(at(-7, 0, -7))} ${xyz(at(7, 6, 7))} minecraft:air`,
    `tp ${bot.username} ${O.x + 0.5} ${O.y} ${O.z + 0.5}`,
    `clear ${bot.username}`,
    `setblock ${xyz(at(2, 0, 0))} ${MOD}loose_stones`, `setblock ${xyz(at(2, 0, 1))} ${MOD}loose_stones`,
    `setblock ${xyz(at(2, 0, -1))} ${MOD}wild_plants`, `setblock ${xyz(at(2, 0, 2))} ${MOD}fallen_sticks`,
    `setblock ${xyz(at(-2, 0, 0))} minecraft:oak_log`, `setblock ${xyz(at(-2, 0, 1))} minecraft:oak_log`,
    `setblock ${xyz(at(-2, 0, -1))} minecraft:stone`, `setblock ${xyz(at(-2, 0, -2))} minecraft:stone`,
    `setblock ${xyz(at(0, -1, 6))} minecraft:water`
  )
  await sleep(1500)
  const home = new Vec3(O.x + 0.5, O.y, O.z + 0.5)

  const have = name => bot.inventory.items().filter(item => item.name === name).reduce((sum, item) => sum + item.count, 0)
  const say = text => bot.survival.command(text, undefined, 8000)
  async function walkTo (target, within = 0.8, timeout = 6000) {
    const end = Date.now() + timeout
    while (Date.now() < end) {
      const here = bot.entity.position
      const flat = Math.hypot(here.x - target.x, here.z - target.z)
      if (flat <= within) break
      await bot.lookAt(new Vec3(target.x, here.y + 1.6, target.z), true)
      bot.setControlState('forward', true)
      await sleep(80)
    }
    bot.clearControlStates()
    await sleep(250)
  }
  /** Walk over whatever lies on the ground within reach, then come back. */
  async function pickUp (near, radius = 3.5) {
    for (let round = 0; round < 4; round++) {
      const drops = Object.values(bot.entities).filter(e => e.name === 'item' && e.position.distanceTo(near) <= radius)
      if (!drops.length) break
      for (const drop of drops) await walkTo(drop.position, 0.5, 4000)
      await sleep(400)
    }
  }
  /** Run a timed job by command and wait until the things it yields are in the pack. */
  async function job (command, item, atLeast, where, timeout = 30000) {
    const before = have(item)
    const reply = await say(command)
    await until(`${item} from "${command}" (server said: ${reply})`, async () => {
      if (where) await pickUp(where)
      return have(item) >= before + atLeast
    }, timeout, 500)
    return reply
  }

  log(`\nStage at ${O}; ${bot.username} is in ${bot.game.gameMode} mode.\n`)
  const ticks = { start: await tick() }

  // ------------------------------------------------------------------ gather and craft
  section = 'craft'
  await step('joins as an ordinary survival player with empty hands', async () => {
    expect(bot.game.gameMode === 'survival', 'game mode is ' + bot.game.gameMode)
    expect(bot.inventory.items().length === 0, 'inventory is not empty')
    const refused = await say('/survival enroll @s')
    expect(/permission|Unknown|Incorrect|unknown/i.test(refused), 'an operator command was not refused: ' + refused)
    return 'operator commands are refused'
  })
  await step('gathers loose stones, plant fibre and sticks from the ground', async () => {
    const stones = at(2, 0, 0); const more = at(2, 0, 1); const plants = at(2, 0, -1); const sticks = at(2, 0, 2)
    const first = await job(`/survival gather ${xyz(stones)}`, MOD + 'loose_stone', 3, stones.offset(0.5, 0, 0.5)); await walkTo(home)
    await job(`/survival gather ${xyz(more)}`, MOD + 'loose_stone', 3, more.offset(0.5, 0, 0.5)); await walkTo(home)
    await job(`/survival gather ${xyz(plants)}`, MOD + 'plant_fiber', 2, plants.offset(0.5, 0, 0.5)); await walkTo(home)
    await job(`/survival gather ${xyz(sticks)}`, 'stick', 2, sticks.offset(0.5, 0, 0.5)); await walkTo(home)
    return `${have(MOD + 'loose_stone')} stones, ${have(MOD + 'plant_fiber')} fibre, ${have('stick')} sticks; "${first}"`
  })
  await step('knaps stone edges and twists cordage by hand', async () => {
    const first = await job(`/survival craft ${MOD}knap`, MOD + 'stone_edge', 2)
    await job(`/survival craft ${MOD}knap`, MOD + 'stone_edge', 2)
    await job(`/survival craft ${MOD}twist_cordage`, MOD + 'cordage', 2)
    return `${have(MOD + 'stone_edge')} edges, ${have(MOD + 'cordage')} cordage; "${first}"`
  })
  await step('fells two logs by hand and makes planks and a crafting table', async () => {
    for (const spot of [at(-2, 0, 0), at(-2, 0, 1)]) {
      const block = bot.blockAt(spot); expect(block && block.name === 'oak_log', 'no log at ' + spot)
      const before = have('oak_log')
      await bot.dig(block)
      await until('the felled log to be picked up', async () => { await pickUp(spot.offset(0.5, 0, 0.5)); return have('oak_log') > before }, 12000, 400)
      await walkTo(home)
    }
    await job('/survival craft minecraft:oak_planks', 'oak_planks', 4)
    await job('/survival craft minecraft:oak_planks', 'oak_planks', 4)
    await job('/survival craft minecraft:crafting_table', 'crafting_table', 1)
    return `${have('oak_planks')} planks and a table`
  })
  await step('sets the table down and makes a knapped stone pickaxe', async () => {
    await bot.equip(bot.inventory.items().find(item => item.name === 'crafting_table'), 'hand')
    await bot.placeBlock(bot.blockAt(at(-1, -1, 2)), new Vec3(0, 1, 0))
    expect(bot.blockAt(at(-1, 0, 2)).name === 'crafting_table', 'the table was not placed')
    const reply = await job(`/survival craft ${MOD}knapped_stone_pickaxe`, MOD + 'knapped_stone_pickaxe', 1)
    return `"${reply}"`
  })

  // ------------------------------------------------------------------ mine
  section = 'mine'
  await step('cannot break stone bare-handed, by command or by striking it', async () => {
    bot.setQuickBarSlot(8); await sleep(300)
    expect(!bot.heldItem, 'the hand is not empty: ' + (bot.heldItem && bot.heldItem.name))
    const spot = at(-2, 0, -1)
    const reply = await say(`/survival mine ${xyz(spot)}`)
    bot._client.write('block_dig', { status: 0, location: spot, face: 1, sequence: 0 }); bot.swingArm()
    await sleep(6000)
    expect(bot.blockAt(spot).name === 'stone', 'the stone broke without a tool')
    return `"${reply}"`
  })
  await step('mines stone with the pickaxe it made, and the pickaxe wears', async () => {
    const pick = bot.inventory.items().find(item => item.name === MOD + 'knapped_stone_pickaxe')
    await bot.equip(pick, 'hand')
    const spot = at(-2, 0, -1)
    const reply = await job(`/survival mine ${xyz(spot)}`, 'cobblestone', 1, spot.offset(0.5, 0, 0.5))
    await walkTo(home)
    expect(bot.blockAt(spot).name === 'air', 'the stone is still there')
    const worn = bot.heldItem && bot.heldItem.durabilityUsed
    expect(worn >= 1, 'the pickaxe shows no wear (' + worn + ')')
    return `1 cobblestone, pickaxe wear ${worn}; "${reply}"`
  })
  await step('mines a second block by striking it, with no command', async () => {
    const spot = at(-2, 0, -2); const before = have('cobblestone')
    const block = bot.blockAt(spot)
    await bot.lookAt(spot.offset(0.5, 0.5, 0.5), true)
    bot._client.write('block_dig', { status: 0, location: spot, face: 1, sequence: 0 }) // start destroying
    bot.swingArm()
    await until('the struck stone to break', async () => { await pickUp(spot.offset(0.5, 0, 0.5)); return have('cobblestone') > before }, 20000, 500)
    await walkTo(home)
    expect(block && bot.blockAt(spot).name === 'air', 'the stone is still there')
    return `${have('cobblestone')} cobblestone`
  })

  // ------------------------------------------------------------------ build
  section = 'build'
  await step('puts a roof over its own head and the server recognises it', async () => {
    await walkTo(home, 0.25)
    const before = await bot.survival.climate()
    expect(before.roof === false, 'reported a roof before anything was built')
    const place = async (reference, face, name) => {
      const item = bot.inventory.items().find(i => i.name === name); expect(item, 'out of ' + name)
      await bot.equip(item, 'hand'); await bot.placeBlock(bot.blockAt(reference), face)
    }
    await place(at(0, -1, -1), new Vec3(0, 1, 0), 'oak_planks')
    await place(at(0, 0, -1), new Vec3(0, 1, 0), 'oak_planks')
    await place(at(0, 1, -1), new Vec3(0, 1, 0), 'oak_planks')
    await place(at(0, 2, -1), new Vec3(0, 0, 1), 'oak_planks')
    expect(bot.blockAt(at(0, 2, 0)).name === 'oak_planks', 'the roof block is not above the bot')
    const after = await until('the roof to be recognised', async () => { const c = await bot.survival.climate(); return c.roof === true && c }, 8000, 700)
    return `roof ${before.roof} -> ${after.roof} (profile ${after.profile})`
  })
  ticks.afterWork = await tick()

  // ------------------------------------------------------------------ new life
  section = 'new life'
  const uuidOf = entity => entity.uuid
  const near = (name, where, radius = 3) => Object.values(bot.entities).filter(e => e.name === name && e.position.distanceTo(where) <= radius)
  await step('pairs two cows with feed, and a calf is born twelve days later', async () => {
    const pen = at(0, 0, 5)
    await op(`summon minecraft:cow ${pen.x - 0.5} ${pen.y} ${pen.z + 0.5} {NoAI:1b,PersistenceRequired:1b}`, `summon minecraft:cow ${pen.x + 1.5} ${pen.y} ${pen.z + 0.5} {NoAI:1b,PersistenceRequired:1b}`, `give ${bot.username} minecraft:wheat 2`)
    await walkTo(new Vec3(O.x + 0.5, O.y, O.z + 3.2), 0.4)
    const cows = await until('two cows', () => { const c = near('cow', pen.offset(0.5, 0, 0.5)); return c.length === 2 && c })
    await until('the wheat', () => have('wheat') === 2)
    await bot.equip(bot.inventory.items().find(item => item.name === 'wheat'), 'hand')
    const first = await say(`/survival livestock ${uuidOf(cows[0])}`)
    const second = await say(`/survival livestock ${uuidOf(cows[1])}`)
    expect(/Pregnancy begun/.test(second), `no pregnancy: "${first}" then "${second}"`)
    await until('the feed to be used up', () => have('wheat') === 0, 4000)
    await sleep(1500)
    expect(near('cow', pen.offset(0.5, 0, 0.5), 4).length === 2, 'a calf appeared at once')
    await advanceCalendar(11.9)
    await sleep(2500)
    expect(near('cow', pen.offset(0.5, 0, 0.5), 4).length === 2, 'a calf was born before its day')
    await advanceCalendar(0.2)
    await until('the calf', () => near('cow', pen.offset(0.5, 0, 0.5), 4).length === 3, 8000)
    const calves = await count(`@e[type=minecraft:cow,x=${pen.x},y=${pen.y},z=${pen.z},distance=..5,nbt={Age:-24000}]`)
    return `"${second}"; three cattle in the pen${calves ? ', one a calf' : ''}`
  })
  await step('pairs two hens, nests the egg on hay by a fire, and a chick hatches after four days', async () => {
    const coop = at(4, 0, 5); const nest = at(5, 0, 1)
    await op('kill @e[type=minecraft:cow]', 'kill @e[type=minecraft:item]',
      `summon minecraft:chicken ${coop.x - 0.5} ${coop.y} ${coop.z + 0.5} {NoAI:1b,PersistenceRequired:1b}`, `summon minecraft:chicken ${coop.x + 0.5} ${coop.y} ${coop.z + 0.5} {NoAI:1b,PersistenceRequired:1b}`,
      `give ${bot.username} minecraft:wheat_seeds 2`, `setblock ${xyz(nest)} minecraft:hay_block`, `setblock ${xyz(nest.offset(1, 0, 0))} minecraft:campfire`)
    await walkTo(new Vec3(O.x + 3.0, O.y, O.z + 3.4), 0.4)
    const hens = await until('two hens', () => { const c = near('chicken', coop.offset(0, 0, 0.5)); return c.length === 2 && c })
    await until('the seed', () => have('wheat_seeds') === 2)
    await bot.equip(bot.inventory.items().find(item => item.name === 'wheat_seeds'), 'hand')
    await say(`/survival livestock ${uuidOf(hens[0])}`)
    const laid = await say(`/survival livestock ${uuidOf(hens[1])}`)
    expect(/Fertilized egg/.test(laid), `no fertilized egg: "${laid}"`)
    await until('the fertilized egg in hand', async () => { await pickUp(coop.offset(0, 0, 0.5), 4); return have(MOD + 'fertilized_egg') >= 1 }, 12000, 500)
    await walkTo(new Vec3(nest.x - 1.5, O.y, nest.z + 0.5), 0.4)
    await bot.equip(bot.inventory.items().find(item => item.name === MOD + 'fertilized_egg'), 'hand')
    const placed = await say(`/survival nest ${xyz(nest)}`)
    expect(/placed/.test(placed), `the egg was not nested: "${placed}"`)
    await walkTo(new Vec3(nest.x - 3.5, O.y, nest.z + 0.5), 0.4)
    await sleep(4000)
    expect(have(MOD + 'fertilized_egg') === 0, 'the egg came back into the pack')
    await advanceCalendar(3.9)
    await sleep(2500)
    expect(near('chicken', nest.offset(0.5, 1, 0.5), 3).length === 0, 'a chick hatched before four days')
    await advanceCalendar(0.2)
    await until('the chick', () => near('chicken', nest.offset(0.5, 1, 0.5), 3).length === 1, 8000)
    return `"${laid}" then "${placed}"; one chick at the nest`
  })
  await step('is still alive and sound after sixteen days of calendar were turned forward', async () => {
    const body = await bot.survival.query()
    expect(bot.health > 0 && bot.game.gameMode === 'survival', 'the bot did not survive the calendar being advanced')
    return `hunger ${body.hunger.toFixed(0)}, thirst ${body.thirst.toFixed(0)}, ${body.conditions}`
  })
  ticks.afterLife = await tick()
  if (args['stop-after'] === 'new life') { log('Stopping here with the stage left standing.'); process.exit(0) }

  // ------------------------------------------------------------------ death
  section = 'death'
  await step('a dead animal and a dead enrolled villager leave the world', async () => {
    const spot = at(-4, 0, 4)
    await op(`summon minecraft:villager ${spot.x + 0.5} ${spot.y} ${spot.z + 0.5} {NoAI:1b,Tags:["lifecycle_npc"]}`, 'survival enroll @e[tag=lifecycle_npc]')
    await until('the villager', () => near('villager', spot.offset(0.5, 0, 0.5)).length === 1)
    expect(await count('@e[type=minecraft:chicken]') >= 1, 'no chicken to kill')
    await op('kill @e[tag=lifecycle_npc]', 'kill @e[type=minecraft:chicken]')
    await until('the bodies to be gone', async () => (await count('@e[type=minecraft:villager]')) + (await count('@e[type=minecraft:chicken]')) === 0, 6000, 400)
    await until('the bot to stop seeing the villager', () => near('villager', spot.offset(0.5, 0, 0.5)).length === 0, 4000)
    return 'both gone within a few seconds'
  })
  let mortal = null
  await step('a player who dies of thirst comes back only as a spectator', async () => {
    mortal = await join('Mortal')
    await sleep(1500)
    expect(mortal.game.gameMode === 'survival', 'Mortal joined in ' + mortal.game.gameMode)
    let died = false; mortal.once('death', () => { died = true })
    await op('survival debug Mortal thirst 0')
    let how = 'thirst'
    try { await until('death by thirst', () => died, 45000, 300) } catch { how = 'the operator (thirst did not kill within 45 s)'; await op('kill Mortal'); await until('death', () => died, 8000) }
    await until('the spectator game mode', () => mortal.game.gameMode === 'spectator', 15000)
    const flag = await con.send('data get entity Mortal NeoForgeData.survivalDead')
    expect(/1b/.test(flag), 'the dead mark is not set: ' + flag.trim())
    return `killed by ${how}; now ${mortal.game.gameMode}`
  })
  await step('the dead can neither act, order nor speak', async () => {
    const patch = at(3, 0, -3)
    await op(`setblock ${xyz(patch)} ${MOD}loose_stones`, `tp Mortal ${patch.x + 0.5} ${patch.y + 1} ${patch.z - 1}`)
    await sleep(800)
    const refused = await mortal.survival.command(`/survival gather ${xyz(patch)}`, undefined, 8000).catch(error => error.message)
    expect(/dead take no part/.test(refused), 'the command of a dead player was not refused: ' + refused)
    const drink = await mortal.survival.command('/survival act drink', undefined, 8000).catch(error => error.message)
    expect(/dead take no part/.test(drink), 'the body action of a dead player was not refused: ' + drink)
    let heard = false; const listen = (username, message) => { if (username === 'Mortal' && /from beyond/.test(message)) heard = true }
    bot.on('chat', listen); mortal.chat('a word from beyond'); await sleep(2000); bot.removeListener('chat', listen)
    expect(!heard, 'the living heard the dead speak')
    mortal._client.write('block_dig', { status: 0, location: patch, face: 1, sequence: 0 })
    await sleep(6000)
    const stones = await count('@e[type=minecraft:item,nbt={Item:{id:"' + MOD + 'loose_stone"}}]')
    expect(bot.blockAt(patch).name === MOD + 'loose_stones' && stones === 0, `a dead player gathered from the patch (block ${bot.blockAt(patch).name}, ${stones} stones on the ground)`)
    await op(`setblock ${xyz(patch)} minecraft:air`)
    return `"${refused}"`
  })
  await step('leaving and rejoining does not bring the dead back', async () => {
    mortal.quit(); await sleep(2500)
    mortal = await join('Mortal'); await sleep(1500)
    expect(mortal.game.gameMode === 'spectator', 'rejoined in ' + mortal.game.gameMode)
    return 'still a spectator'
  })
  await step('an operator can return the dead to the world as a new person', async () => {
    const reply = await op('survival revive Mortal')
    await until('survival mode', () => mortal.game.gameMode === 'survival', 8000)
    const body = await mortal.survival.query()
    const learned = Object.values(body.skills || {}).reduce((sum, skill) => sum + (typeof skill === 'number' ? skill : (skill.level || 0)), 0)
    expect(learned === 0, 'the new person kept skills: ' + JSON.stringify(body.skills).slice(0, 120))
    return `"${reply.trim()}"; thirst ${body.thirst.toFixed(0)}, nothing learned`
  })
  ticks.end = await tick()

  // ------------------------------------------------------------------ strike the set
  await op('kill @e[type=!minecraft:player]', `fill ${xyz(at(-7, 0, -7))} ${xyz(at(7, 6, 7))} minecraft:air`, `fill ${xyz(at(-7, -1, -7))} ${xyz(at(7, -1, 7))} minecraft:grass_block`,
    `clear ${bot.username}`, 'clear Mortal', 'gamerule spawn_mobs true')
  const failed = results.filter(r => !r.ok)
  log(`\nServer tick: ${ticks.start} ms at the start, ${ticks.afterWork} after the work, ${ticks.afterLife} after the breeding, ${ticks.end} at the end.`)
  log(`${results.length - failed.length} of ${results.length} steps passed.`)
  if (args.out) fs.writeFileSync(args.out, JSON.stringify({ results, ticks }, null, 2))
  try { bot.quit() } catch {} try { mortal && mortal.quit() } catch {}
  con.close()
  await sleep(800)
  process.exit(failed.length ? 1 : 0)
}

main().catch(error => { console.error('FAIL', error); process.exit(1) })
