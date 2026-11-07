# Living-world implementation report

This report covers the content, world, and agriculture work assigned to the living-world agent. It is an implementation and verification record, not a claim that every item in the approved design has been finished.

## Entry points and integration

- `SurvivalContent.register(IEventBus)` registers content, block entities, creative filtering, and GameTests. `SurvivalContent.init()` attaches gameplay handlers.
- Uses `SurvivalApi.calendar(ServerLevel)`, `state`, `save`, `isHumanoid`, and `give`. Season length uses `Config.SEASON_DAYS`.
- `Production.start(LivingEntity, WorkstationEntity, String)` validates a living enrolled humanoid, same dimension, six-block range, workstation type, current physical/mental capacity, fuel, and actual held/offhand ingredients. The Player overload delegates to it.
- `Production.fuel(LivingEntity, WorkstationEntity)` and `collect(...)` are shared player/controller actions with the same dimension/range/alive checks. Root commands can call these.
- `Production.RECIPES` exposes an immutable active snapshot of recipe inputs, offhand requirements, output, duration, and forge requirement.
- `SeasonalCrops`, `Husbandry`, `ItemAging`, and `Production` each expose `reloadDefinitions(JsonObject)`. Root `RealismData` stages the pack JSON and applies it after server data components bind, including `/reload`. `SeasonalCrops.definitions()`/`schedules()` and species/decay definitions expose immutable snapshots for planning.
- `Pottery.action(LivingEntity,String)` shares fill/boil/seal/store/retrieve operations with NPC scripts and human interactions. It validates alive humanoid status, capacities, proximity, actual pots/ingredients, and lit heat. `waterUnits(ItemStack)`, `safeWater`, and `consumeWater` support core drinking; sealed pots cannot drain. One portion restores 25 thirst in the core; filled pots contain up to four portions.
- `Husbandry.action(LivingEntity,Animal)` shares feeding/milking/shearing with player events; `Husbandry.nest(LivingEntity,BlockPos)` places real fertilized eggs. `Production.stockTrough(LivingEntity,WorkstationEntity)` shares finite-reserve livestock provisioning with human interactions.
- `ItemAging.spoiled(ItemStack)` is the food illness predicate. Spoilage preserves the original registry identity and metadata while lowering nutrition to one, so recipes cannot renew spoiled ingredients.
- Garments: `wool_shirt`, `wool_pants`, `wool_jacket`, `wool_shoes`, and corresponding `fiber_` variants. Treatments: `bandage`, `painkiller`, `antitoxin`, `antiseptic`, `antibiotic`. Core owns wearing/treatment behavior and garment rendering.
- Common mixins are isolated in `world.mixin` and `spawn_living_world.mixins.json`. Root adds the TOML mixin declaration.

## Working mechanics

### Natural world

An explicit forbidden-entity tag blocks entity additions and discards existing forbidden entities on load without loot. Zombie, skeleton, creeper, and enderman are regression cases; cows remain admitted. Categories include explicit real-animal data. Additional data packs can extend prohibition tags.

Supernatural items, magical potion variants, netherite, spawn eggs, redstone mechanisms, and artifacts are removed at relevant inventory/item admission paths. Ordinary water bottles are preserved through potion-content inspection. Generic enchantment components are stripped from natural equipment. Container load, access, and insertion mixins cover chests/hoppers and similar block inventories; player menus/inventories and living equipment are also scrubbed. Villager offers involving prohibited costs/results are removed. Creative parent/search entries are filtered and mod content appears in Ingredients. Right-click use and attacks sanitize immediately.

Global `ChunkGenerator.createStructures` cancellation prevents all newly generated structure starts, including future registered structure sets. `MonsterRoomFeature.place` is globally canceled; biome modifiers also remove vanilla monster rooms and desert wells. Portals and travel to the Nether/End are blocked. Existing player construction is retained.

### Resources and production

New grassland features generate loose stones/sticks, wild resource/seed patches, fruit trees, and tin ore. Wild patches can supply fiber, all new crop seeds, wheat and retained vegetable seeds/planting foods without hostile drops or village loot. Their berry yield is seasonal.

The resource path has stone-edge knapping, cordage, early stone tools/hoes/axes/spears, metal-free kiln crafting/fuel, pottery, primitive campfire crafting, textiles, copper/tin crushing-roasting-smelting, bronze alloying, iron blooms and forge work. Iron ore is excluded from stone-tool harvest so copper/bronze improve progression. Natural obsidian hardness is reduced and its loot becomes flakes for sharp brittle tools. Tool materials have different speed and durability. New tool recipes use forge progress instead of instant ingot recipes. Primitive tools and garments use distinct shaped recipes; a canonical match-layout audit found and removed five ambiguous shapeless pairs. All custom hoes/axes/pickaxes and spears have their vanilla item tags for actual abilities and controller use. Vanilla instant spear recipes are disabled; copper/iron spear outputs have timed forge recipes.

Kilns/forges store actual input and output stacks, recipe, fuel, elapsed progress, wear, and timestamps. Unfinished work retains its unfinished input; only full processing grants output. Sticks, logs, and charcoal provide different heat reserves. Shift-use an oak log at a fueled kiln to turn it into charcoal; ordinary use adds the log as fuel. Workstations wear out after 256 completions; accumulated wear is stamped into their dropped item and restored on replacement. Catch-up heat use is capped at the actual remaining job work, preserving unused fuel and avoiding extra hunger cost. Forge work persists a worker UUID and pauses if that actor is absent, dead, farther than six blocks, or incapable. Shared `LaborGuard` checks reject second manual-forge reservations or overlap with existing world/grid jobs before consuming ingredients; legacy stations with one worker UUID share one twenty-tick labor budget. Only work that actually consumes heat marks the shared labor clock. Work progresses according to current capacity and consumes current hunger; manual forge work cannot accrue unloaded catch-up. Passive kiln heat processing can catch up to its finite fuel reserve.

Vanilla direct ore/cooked-meat processing and instant tool recipes are overridden with survival-inaccessible bedrock inputs. Their registry identities are retained because 26.3 has references to cooking recipes that cannot simply be made conditionally absent.

### Pottery and preservation

A fired pot is a portable item with vanilla container components holding up to four food stacks. Shift-use with offhand food stores it; shift-use with empty offhand retrieves it. Shift-use with a pot lid consumes a physical lid into the seal; opening recovers exactly one lid. Nesting containers is refused. Components preserve the inventory through save/transport; output is moved atomically rather than copied.

Unsealed pots collect four untreated water portions at surface water. Interacting with heat begins boiling; holding the open pot within four blocks of that heat for 1,000 actual loaded ticks makes the water safe. Leaving heat or sealing interrupts the operation. Vessel use accumulates wear. A vessel broken while storing/retrieving food spills every remaining stack and ceramic shards.

Food timestamps persist in components. Raw meat lasts half a day, cooked food one day, produce three days, dry rice grain/wheat seven days. Sealed pottery applies one-sixth decay; ordinary block storage applies 0.9 decay. Split stacks retain age, unlike ages cannot merge due to component inequality, crafting and kiln cooking inherit the oldest ingredient age, and container/hopper access does not reset timestamps. Dropped items remain long enough to weather: exposed food ages, other materials accumulate weathering with rain/hot-biome acceleration and eventually become scrap/fiber. Stored unused durable materials do not weather. Storage clears the exposure timestamp while retaining accumulated wear; re-dropping cannot turn stored days into exposure. Dropped-item reload preserves its exposure timestamp, so actual exposed catch-up still works. NPC held/equipped food receives the same timestamp updates as player food.

### Agriculture and husbandry

All declared vanilla/new crops run through an elapsed-calendar random-tick interception path; bone meal cannot replace growth days. Definitions cover wheat, carrots, potatoes, beetroot, rice, maize, fiber, medicinal plants, berries, melon/pumpkin stems, cocoa, sugar cane, and mushrooms. Melon/pumpkin establishment takes ten/twelve days, later fruit three/four days, with one adjacent fruit allowed at a time. Planted mushroom and cane breaking cannot skip four/six days; mature mushrooms spread on a four-day schedule and initialize immature offspring. Sowing windows are enforced through the authoritative placement interaction. Farmland moisture, biome warmth, rice-adjacent paddy water, winter dormancy, and frost-sensitive immature crops affect growth. Crop records persist by dimension/block position in server command storage; elapsed catch-up is bounded to one seasonal year, sufficient to saturate the current crop durations. New crop harvests return food/material plus sustainable seeds. Mature crops remain calendar-ticking, so ripe rice cannot become frost-immune. Wild ripe berries initialize as established sources, then enter the two-day recovery cycle; stale custom crop display age cannot grant an unearned full harvest.

Natural fruit trees are mature sources; player-planted trees establish for one full year. Mature trees report spring flowering, yield apples and a new tree planting item once per summer/autumn year, and do not yield in winter. Ordinary oak/dark-oak leaf apple drops are removed.

Cows/sheep/chickens/pigs persist birth time, nutrition, feeding, pairing cooldowns, pregnancy, litter, and product timers. Feeding consumes real inventory and does not instantly grow a juvenile. Two fed mature adults, drinking water, recovery, and space permit pairing. Cows/sheep/pigs deliver one deferred birth/litter at 12/8/8 days; pig litters contain 2–4. Delivery clears the pregnancy before spawning, preventing reload duplication. Juvenile maturity is 28/20/12/20 days respectively. Cows require calving and daily milk recovery; sheep need four fed/watered days for wool. These use serialized qualifying-time clocks: dry/poorly fed days do not become product recovery when supplies return. Finite trough reserves are consumed in aggregate during catch-up, crediting only supplied growth time plus valid nutrition reserves; no per-day tick loop or free unloaded water is used. Both player and vanilla/dispenser shearing paths obey the wool calendar. Chicken normal eggs have an explicit persisted due tick and are limited to one per fed/watered day (one initially ready egg for an eligible existing adult); only the separate fertilized-egg item can incubate on hay near heat for four days. Thrown ordinary eggs cannot create chicks.

The feed/water trough is a real persisted block with a metal-free recipe and visible open-basin model. Wheat/seeds/vegetables/edible food stock feed; filled pots stock water. Nearby livestock consume its finite reserves based on elapsed biological time. Natural grass/hay grazing and nearby surface water are alternatives.

## Files

Java under `src/main/java/dev/qube/spawn_9b5fbb9/`:

- `content/SurvivalContent.java`, `Production.java`, `WorkstationBlock.java`, `WorkstationEntity.java`.
- `content/Pottery.java`, `ItemAging.java`, `ContentDefinitions.java`, `GroundResourceBlock.java`, `FruitTreeBlock.java`, `ContentGameTests.java`. The separate controller worker later extracted shared gather/fruit helpers from the latter two gameplay blocks.
- `agriculture/SeasonalCrops.java`, `Husbandry.java`.
- `world/NaturalWorldRules.java`, `WorldGameTests.java`.
- `world/mixin/{Structure,CropTick,Obsidian,Dungeon,ContainerAccess,ContainerLoad,Shearing}Mixin.java`.

Resources include `spawn_living_world.mixins.json`, assets for every registered content item/block, models/item plus modern items bindings, blockstates/crop age models, English names, six Qube-generated textures, new recipes/loot/tags/features/biome modifiers, vanilla recipe overrides, leaf/obsidian loot overrides, tool-harvest/ability tags, and `data/spawn_9b5fbb9/survival/content_rules.json`.

## Verification record

- Initial red GameTest was run against the starter: fantasy-addition admission failed on zombie as intended; three unrelated baseline tests passed.
- Exact 26.3 APIs were checked through `qube api`; runtime verified additional data changes (feature providers, recipe references, tool enchantability).
- Six Qube textures were generated, their previews opened, and `qube texture read` reported `bindings_missing: []` for every one: loose stone, bronze ingot, plant fiber, tin ore, knapped stone pickaxe, and kiln.
- Content regressions cover prohibited entity admissions/natural animal admission, prohibited container inserts with water-bottle exception, exact one-sixth pot preservation plus split age, unfinished/completed kiln firing and duplicate prevention, seasonal bone-meal denial, and real juvenile feeding inventory/age behavior.
- Integrated fulltest results are recorded below. Earlier full runs uncovered and fixed registry load issues; a separate root mock-player network send crash was reported to and fixed by the root agent.
- No screenshot appearance or complete fresh-world playthrough claim is made in this subtask report. Root owns final server/multiplayer/visual verification.

## Remaining gaps and limitations

These remain work, rather than implied completed acceptance criteria:

- Crop schedules, species maturity/gestation/incubation/product/cooldown timings, shelf lives, preservation/weather multipliers, and processing recipes are now reloadable pack definitions. Contamination and physiology are root-owned definitions. Crop definitions accept registered block IDs; processing and per-item shelf overrides reject unknown items. Group reloads validate into a replacement before publication, so invalid groups cannot leave half-applied entries. Existing in-flight process duration remains stored, but its output uses the active recipe definition at completion.
- Crop suitable-time catch-up uses current moisture/climate as its estimate for unloaded history. It cannot reconstruct past irrigation changes or weather. New plant records use command storage, with no pruning for unloaded demolished blocks until their positions are encountered again.
- The fruit tree has working annual behavior/messages but a simple foliage block model; it does not yet have distinct rendered juvenile/flower/fruit stages or natural full tree anatomy.
- Berry right-clicks and breaking obey establishment/recovery/season checks. Mushroom/cane breaking and melon/pumpkin first-versus-repeat fruit have dedicated rules; third-party automation and undeclared harvestable non-CropBlock plants still need broader coverage. Naturally generated, untracked mushrooms/cane can be gathered as existing mature wild resources; newly planted/spread plants carry the required schedule.
- Recipe registry overrides use impossible survival inputs to keep referenced recipes bound; recipes are not removed from every possible third-party recipe-viewer listing. Third-party containers that bypass vanilla BaseContainerBlockEntity/components need their own adapter. Bundle-specific hidden contents are not recursively traversed yet.
- Entity prohibition uses an explicit extendable data tag; unknown third-party fantasy entities require data entries. Some optional vanilla IDs are included with required=false for compatibility. Comparable custom non-structure spawner features beyond the vanilla monster-room hook require their own definitions.
- Treatment crafting uses boiled-water-sterilized cloth as its water-dependent precursor. It is a gameplay abstraction, not a medical manufacturing simulation. Clothing/pottery dye visual variants depend on root integration; most content shares a small set of texture assets rather than unique silhouettes.
- Passive crushing/roasting is represented by timed kiln processes rather than separate mortar/roasting infrastructure. Tool-head shaping is represented by the stone edge and forge tool recipes rather than a full separate head/haft inventory system. Pot lids are molded via crafting rather than independently kiln-fired.
- Weathering uses broad organic/durable categories and shared scrap outputs. Food-bearing vessel handling spills contents on break. The no-actor consumeWater API can break an empty/water-only vessel without producing a visible shard drop. Workstations have finite use but no repair interaction yet.
- Husbandry requires nearby water/troughs for biological progress but does not simulate animal thirst or hunger injuries. Grazing does not consume grass/hay blocks. Warm-nest catch-up estimates continuous warmth from the current loaded nest/heat state; it cannot reconstruct unloaded fuel history.
- Passing deterministic coverage now includes actual twelve-day pregnancy and delivery serialization/no duplication, no-water birth deferral, daily eggs across reload/catch-up, milk/wool boundaries and dry-wool delay, finite-water juvenile growth, ripe berries, mature-rice frost, item aging/storage, workstation conservation, and invalid-definition rollback. Broader tests remain useful for pig litter space deferral, fertilized-egg incubation/reload, all retained crop variants, banned loot/trade/creative component combinations, hopper freshness transit, normal-terrain generation, and the complete first-day-to-iron route. Root owns final normal-world/server/multiplayer/replay evidence.

## Reloadable content schema

`data/spawn_9b5fbb9/survival/content_rules.json` contains:

- `crops`: registered block ID ? `days`, `mask` (spring/summer/autumn/winter bits 1/2/4/8), `warm`, `frost`, `flooded`. Days are integer 1?3650; masks 1?15. Simple default crop names remain accepted by the Java query API.
- `husbandry`: `cow`, `sheep`, `chicken`, `pig` ? integer `maturityDays`, `gestationDays`, `productRecoveryDays`, `breedingCooldownDays`, each 1?3650. Chicken gestation is incubation. Pig product recovery is unused because its product is meat.
- `decay`: positive finite `rawMeatDays`, `cookedFoodDays`, `produceDays`, `dryGrainDays`, `organicWeatherDays`, `durableWeatherDays`; `sealedPotRate`, `storageRate` in 0.000001?1; `rainMultiplier`, `hotMultiplier` in 1?100; optional `shelfDays` mapping registered item IDs to positive days. Day lifetimes are bounded to one tick?3650 days.
- `production`: process key ? `input`, `count`, `output`, `amount`, `ticks`, `forge`, `second`, `secondCount`. Item IDs must exist, output must be allowed natural content, counts respect max stack size, and duration is 1?64000 ticks. `second` may be the special `@safe_water` requirement (1?4 open boiled-pot portions). New recipes require input/output; existing entries can override only selected fields.

Omitted entries/fields retain shipped defaults on each reload. Wrong types, nonfinite/out-of-range values, unknown fields, unsupported species, and imaginary registry IDs are rejected before the group replacement is applied. Four deterministic regression tests were first run red against no-op APIs, then verified in the full integrated suite.

## Final frozen-source evidence

- Initial fantasy-admission red test failed on zombie as expected.
- Conservation/biology review red run: 6 of 30 failed; all real defects were corrected, and the waterless-maturation fixture was isolated from neighboring tests' water.
- Focused conservation/biology/crop run: all 19 content GameTests passed in 15.8 seconds.
- Definition reload red run: all four targeted tests failed on missing override behavior before implementation.
- Earlier integrated run reported by root: **50/50 GameTests passed in 18.8 seconds**, followed by **52/52** after controller additions. Those runs included all content definitions, finite trough catch-up, biology/conservation, root guards and controller actions; root also reported a passing build and **31 pure physiology checks**.
- Manual-labor review: `content_forge_single_actor`, `content_forge_world_job`, and `content_forge_legacy_budget` all failed before integration. Production/Workstation then integrated `LaborGuard.mayStartForge`/`claimForge` and `mayAdvanceForge`/`worked`. The completed integrated log records **62/62 required GameTests passed**, with **BUILD SUCCESSFUL in 25 seconds**, including all three new regressions. Rejected work preserves supplies/fuel; legacy overlapping forges cannot multiply progress, heat use or hunger charges.
- No scoped Production/Workstation Java/resource changes were made after the 62-test result. Root/controller workers own later cross-module regression additions and final server, normal-terrain and replay verification; this report does not claim those are completed.
