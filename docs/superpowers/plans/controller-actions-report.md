# Shared controller world actions

The server action surface lives in `dev.qube.spawn_9b5fbb9.controllers.WorldActions`.
It accepts real `LivingEntity` actors, including players, villagers and explicitly
enrolled scripted NPCs. It does not create fake players or provide an AI planner.

## Public API

- `start(actor, operation, BlockPos)` starts `gather`, `harvest`, `mine`, `till`, or `sow`.
- `craft(actor, registeredRecipeId)` matches an actual server crafting recipe against
  actual actor supplies and nearby server containers/dropped item stacks.
- `inspectJob(actor)` returns a JSON snapshot of operation, status, target,
  progress, required work, and estimated remaining loaded ticks at current capacity.
- `planning(actor)` returns capacities, limiting conditions, calendar, crop
  schedules and next sowing times, plus normal crafting recipe ingredients,
  outputs, processing times and current capacity estimates.
- `planning(actor, recipeOffset[, cropOffset])` pages a stable catalog with at
  most 64 entries and a hard 24 KiB UTF-8 response budget. Both `nextOffset`
  and `nextCropOffset` indicate continuation; `-1` means complete. Mod recipes
  sort first. Crafting and production process requirements share the catalog.
- `recipeInfo(actor, id, alternativeOffset)` returns focused crafting or kiln/
  forge requirements. Ingredient alternatives have counts, truncation flags,
  and continuation offsets rather than unbounded tag expansion.
- `cancel(actor, reason)` cancels work and returns the exact reserved inputs.
- `livestock(actor, animal)`, `nest(actor, position)` and `trough(actor, position)`
  adapt the actual shared husbandry/production actions, with actor, capacity,
  proximity and physical-supply validation.
- `register(modBus)` registers GameTests; `init()` registers tick, death and
  permission-checked command adapters. The main mod calls both.

Commands expose the same surface through `/survival <operation> x y z`,
`/survival craft <recipeId>`, `planning [recipeOffset] [cropOffset]`,
`recipeinfo <id> [alternativeOffset]`, `jobs`, `cancel`, `livestock <animal>`,
`nest x y z`, and `trough x y z`.
Operator-only `/survival control <actor> ...` exposes the same arguments for NPCs.

## Validation and conservation

Work requires a living, eligible server humanoid within four blocks of its
loaded target. Creative/spectator players use their existing privileged game
mode rather than entering survival jobs. Actor dimension, range, target block,
tool identity/damage and sowing conditions are checked again while working.
Mental or manipulation incapacity pauses progress. Invalid target, movement,
tool changes or death cancel it. One job can be active per actor.

Jobs persist on the actual actor, including exact reserved ItemStack components.
They accrue capacity-weighted work only through loaded entity ticks; sleep,
calendar jumps and unloading never create manual labor. Work expends 0.5 hunger
per 1,000 loaded work ticks. Crafting uses the same 100 base work ticks as the
shared human grid crafting guard. Mining additionally spends the normal 0.08
hunger per broken block and invokes the actual tool's mining durability path.
Actual manual progress also records `survivalManualWorkAt` in game time so the
shared human grid guard can prevent simultaneous credit for world and grid work.

Mining validates actual harvest-tool requirements and hardness before removal.
Its loot path passes the real held tool and actor into `Block.dropResources`,
including NeoForge BlockDropsEvent, rather than `Level.destroyBlock`'s empty-tool
loot context. Containers/workstations are not mined through this API.

Gathering and fruit harvesting have shared `LivingEntity` entry points in
GroundResourceBlock and FruitTreeBlock. Their existing human callbacks start the
same timed actions. Completion helpers reject calls outside an authorized job.
Crop harvest reconciles the seasonal simulation first, rereads the actual world,
then uses its real block/drop path. Frost removal and immature produce guards
therefore also apply to scripts. Sowing consumes a real seed, validates seasonal
windows, moist prepared soil, paddy water and block survival, and records planting
time. Fruit-tree sowing respects its spring/autumn establishment window.

Crafting reserves supplies from player inventory, NPC hands/InventoryCarrier
inventory, and actual containers/dropped stacks within three blocks. Allocation
handles more than two material kinds and repeated ingredient counts. It builds
the registered recipe's actual layout and calls its server `matches`/`assemble`
methods. Completion rechecks the registered recipe, applies ingredient aging
once, and returns recipe remainders. Caller-provided ItemStacks are never accepted
as supplies. Cancellation refunds exact reserved inputs after aging them;
player death refunds enter the normal player inventory/drop policy.
Recipes larger than 2x2 require a real crafting table within four blocks for
every controller. Work cancels if that table disappears or leaves reach;
smaller shapeless recipes use the same 2x2 handcrafting area available to humans.

## Tests and evidence

The initial coordinated red build observed missing WorldActions references in
the tests before implementation. A focused retry subsequently passed all nine
matching tests: the existing controller parity regression and eight new tests
covering timed gathering and incapacity, tool rejection/change cancellation,
actual ore loot and durability, first-day custom hoe tilling, three-material
crafting with nearby chest supplies, reservation refunds, persistence/one-time
completion, and seed/paddy rejection.

The cancellation test initially teleported its NPC into an unloaded neighboring
chunk and could not observe its spawned refund. It now explicitly loads the
destination and crosses the four-block work radius, matching a real loaded
actor tick. The corrected test passed.

Three additional acceptance tests now cover calendar jumps without offline
labor, physical seed consumption/planting records, and stale ripe rice destroyed
by frost. The shared-supplies regression also now checks table requirements.
These all passed in the parent's coordinated 50-test run.

The final coordinated `qube test` run passed **52/52 GameTests in 21.2 seconds**,
including all thirteen new controller tests, existing controller parity, both
core guards, and the living-world/definition/conservation suites. The additional
controller tests validate the actual bounded planner page/continuation schema,
focused recipe and production heat information, and NPC livestock/nest/trough
adapters consuming real server supplies. The full run compiled and registered
the new command tree successfully. No commits were created.

## Explicit limits

The craft-by-recipe adapter supports normal registered crafting recipes with
declared placement ingredients up to 3x3. Dynamic/special recipes without
declared ingredients require their existing dedicated action, such as garment
dyeing; kiln/forge recipes use the shared production API. Sowing accepts declared
BlockItem crops when their actual block-survival conditions permit the supplied
position. Crop/stem plants require prepared moist farmland; cocoa derives its
facing from jungle-log support, and cane, berries, and mushrooms use their native
substrate/survival rules. Seasonal limits apply to both interaction adapters.

NPC finished items and refunds follow SurvivalApi.give, which spawns real items
at an NPC's loaded location; NPC planners must arrange collection/storage. This
action layer supplies validation and execution, not pathfinding, supply hauling,
or autonomous planning. No visual appearance claims are made by these tests.

## Final scoped integration

The final full run passed 62/62 registered GameTests (61 mod tests plus the
harness check), including the latest grid and completion-boundary assertions.
Controller mining charges the shared broken-block cost once. Adventure
permissions are checked at entry and during work. Forge/grid/world jobs share
LaborGuard claims and actual-work timestamps. Focused regressions also cover
cocoa orientations and valid cane substrates. Independent review closed all
four findings in this scope.
