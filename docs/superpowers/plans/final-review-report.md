# Independent whole-overhaul review — 2026-11-06

Reviewed the actual working-tree Java/resources, including untracked files, against the approved spec/controller clarification, implementation plan, and living-world report. This is a new code review after the core fixes, not a repetition of the first review. Source assumptions below were checked against the local Minecraft 26.3.0.77 patched source/data archives. No build, test, server, or screenshot commands were run by this reviewer. The implementation team is changing files concurrently; line numbers refer to the inspected snapshot. The separately assigned chicken egg-timer fix is intentionally excluded.

## Prioritized findings

### P1 — Identical crafting inputs make required outputs unreachable

`src/main/resources/data/spawn_9b5fbb9/recipe/{fiber_shirt,fiber_pants,wool_shirt,wool_pants,knapped_stone_hoe,spear,obsidian_axe,obsidian_hoe,obsidian_pickaxe}.json:2`

There are four collision groups: fiber shirt/pants, wool shirt/pants, stone hoe/spear, and all three obsidian tools. Each group has exactly the same shapeless ingredient multiset. Minecraft `RecipeManager.getRecipeFor` returns the first match, so the crafting grid cannot select every output; the recipe book does not disambiguate identical grids. This prevents obtaining a full fiber/wool outfit and several tools through the intended route. Give each output distinct ingredients or a distinct shaped layout; verify each actual recipe result, not merely registry presence.

### P1 — Unprivileged clothing actions take another player's garment

`survival/SurvivalApi.java:115-125`; `survival/SurvivalEvents.java:194-195`; `survival/SurvivalCommands.java:39-41`

The new target clothing action is available through ordinary `/survival act clothe <player>` and sneak-interaction. Its only target checks are type, dimension, and distance. Equipping a cheap fiber shirt on another player gives that player's prior wool/dyed shirt to the actor at line 122, with no ownership/consent check. This is a real multiplayer inventory transfer, not the trusted operator `/control` path. Preserve collaborative NPC dressing while requiring appropriate permission/consent for another player's equipment; displaced equipment should remain with its owner.

### P2 — Vanilla 26.3 spear recipes bypass the new production route

`src/main/resources/data/minecraft/recipe/` (missing spear overrides); `content/Production.java:28`

The overrides cover axes, hoes, picks, shovels and swords but omit the vanilla wooden/stone/copper/iron/golden/diamond spear recipes, all present in the target-version data archive. A player can make a stone spear from vanilla material/sticks without knapping/fiber, and copper/iron spears without timed forge work. Audit the actual target-version recipe registry by required processing category rather than a preselected tool-name list, and supply an intended route for retained outputs.

### P2 — Stored durable items weather for time spent safely stored

`content/ItemAging.java:64-69`

`weatherTick` survives pickup/storage, but nothing closes the exposure interval when the item leaves the world. Drop a tool long enough to get a weather timestamp, pick it up, store it for more than its four/28-day threshold, and drop it again. The next weather update charges the whole stored interval and turns it into scrap/fiber. Preserve accumulated exposure while resetting the interval at pickup/drop boundaries. Stored unused durable materials are explicitly required to remain stable.

### P2 — NPC equipment food bypasses decay at consumption

`world/NaturalWorldRules.java:64-71`; `survival/SurvivalApi.java:183-189`; `survival/SurvivalEvents.java:169-173`

Living equipment is sanitized, but only player inventory/menu stacks receive `ItemAging.update`. NPC-held food can retain an old unspoiled flag indefinitely. Consumption reads nutrition and `spoiled` without reconciling its timestamp. Give a villager a timestamped cooked item, leave it uneaten past its shelf life, then have it eat: it receives fresh nutrition without the spoiled-food condition. Apply the same age update to all humanoid supplies and reconcile age before granting nutrition/treatment/feed outputs.

### P2 — Ripe natural berry bushes can never become harvestable

`agriculture/SeasonalCrops.java:53-57,69-71,83-89`; `world/mixin/CropTickMixin.java:15`

An untracked, naturally generated sweet berry bush at AGE=3 never receives a mod growth record. Vanilla `SweetBerryBushBlock.isRandomlyTicking` returns false at age 3. Both right-click and breaking require at least eight days of recorded growth and therefore deny its berries forever, even after seasons pass. Initialize wild mature plants explicitly or reconcile their state at harvest/load without depending on a random tick that cannot occur.

### P2 — Unloaded kiln catch-up consumes fuel past completion

`content/WorkstationEntity.java:26-36`

Fuel use is `min(dt, fuel)` without a bound for remaining work. Start a 2,000-tick pot with 64,000 heat, unload it for 64,000 ticks, and reload: it produces one pot but spends all 64,000 heat; remaining loaded would spend only 2,000. Cap processing at the time of completion and age the completed output for the remaining interval as needed. Loaded and unloaded processing must have the same resource result.

### P2 — Breaking and replacing a workstation resets its wear

`content/WorkstationEntity.java:19,40-42`; `content/Production.java:40`; `src/main/resources/data/spawn_9b5fbb9/loot_table/blocks/{kiln,forge}.json:9`

Wear exists only in the block entity. The block loot produces a plain station item with no wear component, so mining/replacing a station at 255 or 256 completions gives a fresh 256-use station for free. Transfer wear into/from the dropped item, or make an exhausted station yield the defined spent material instead of a fresh workstation.

### P2 — Wool recovery counts unfed/unwatered days

`agriculture/Husbandry.java:58,81`; `world/mixin/ShearingMixin.java:12-14`

Recovery is an absolute `woolAt=now+4*DAY` deadline plus an endpoint nutrition/water check. Shear a sheep, keep it without food/water for four days, then feed it and add water: it immediately regrows rather than requiring four adequately supplied days. Accumulate suitable recovery time, with bounded catch-up using the declared unloaded-history policy. This is distinct from the chicken-timer work already assigned.

### P2 — Several vanilla fantasy entities are not prohibited

`world/NaturalWorldRules.java:38,57`; `src/main/resources/data/spawn_9b5fbb9/tags/entity_type/forbidden_entities.json`

The sole entity policy is membership in this tag, which omits `minecraft:hoglin`, `minecraft:strider`, `minecraft:ravager`, and the target-version `minecraft:sulfur_cube`. Local `EntityTypes` confirms all exist. Summoning/loading these succeeds through the admission predicate, and ravagers can participate in surviving illager raids. Add the missing vanilla fantasy species and audit the target registry against the natural/humanoid categories; unknown third-party definitions can remain an explicit extension boundary.

### P2 — Grid crafting remains available at zero mental capacity

`world/NaturalWorldRules.java:80`; `content/ItemAging.java:54-57`; `survival/SurvivalEvents.java:177-178`

The new incapacity guards cover attack/mining and the workstation API. Ordinary crafting handlers only sanitize and age output. A player with brain condition <=10/mental capacity 0 can still instantly craft medicine, garments, cloth, and tools. The spec explicitly disallows existing instant recipe paths bypassing bodily incapacity. Gate the authoritative crafting transaction and define which retained manual processes require elapsed work/hunger, including automation/controller paths.

### P2 — Mature crops stop receiving seasonal lifecycle checks

`agriculture/SeasonalCrops.java:99,103-104`; `world/mixin/CropTickMixin.java:15`

Frost/lifecycle processing only runs when vanilla requests randomTick. Vanilla `CropBlock.isRandomlyTicking` returns false at maximum age, so a mature rice crop never reaches the `rice_crop` winter-destruction branch. Leave ripe rice through winter: it remains harvestable, bypassing the required harvest-before-frost constraint. Separate seasonal lifecycle scheduling from vanilla growth-tick eligibility, or reconcile these rules on load/harvest.

## Important remaining acceptance gaps

- **Scripted non-player resource/farming actions:** vessel, production, livestock and trough methods now accept LivingEntity, but gathering loose/wild resources and fruit harvesting remain Player-only block callbacks (`GroundResourceBlock.java:18`, `FruitTreeBlock.java:16`). There is no validated server action for scripted NPC gathering/sowing/harvesting or ordinary recipe work. Calling `setBlock` directly bypasses sowing/placement records; requiring a script to emulate a Player is not the promised controller-neutral action layer. Commands also omit trough stocking, livestock/nest actions, and unfinished-work retrieval, despite their public APIs or player interactions.
- **Controller planning information:** `/survival query` exposes body state, but no crop schedules, upcoming sow/harvest windows, nearby job requirements, or limiting-reason inspection for a chosen recipe. `/survival recipes` supplies IDs only. The approved clarification explicitly requires upcoming seasonal constraints and actionable limiting capacities.
- **Autonomous NPC care/work:** NPC autonomy seeks food/water/social contact but does not seek/use available treatment or clothing supplies and does not scale ordinary brain work cadence for reduced mental capacity; only zero mental capacity disables AI. These behaviors remain in the implementation plan.
- **Config/data requirements:** per-crop schedules, pathogen definitions, treatment classes, biological durations, decay durations, thresholds, contamination rates and recipe durations remain Java constants. The implementation report correctly acknowledges this, but the approved configurable/data-driven behavior is still incomplete.
- **Evidence still missing:** the current core `state_persistence` test rereads a tag on the same entity. A passing GameTest suite does not establish actual player logout/reload, dimension transfer, death with both inventory policies, full fresh-world first-day-to-iron progression, structure-free normal world generation, or dedicated two-client inspection/clothing/rejected-action behavior. Root owns upcoming screenshots and integration validation; this review makes no visual or runtime-completion claim.

## Reviewed fixes and limits

The prior garment swap now carries wear/color, normal API eating uses timed consumption, hand selection is passed through interactions, mild incubation uses six hours, daily recreation quota is configurable after rollover, and sleep captures actual sleeping state without the previous extra tick subtraction. Version 3 retains explicit v1/v2 decoding. These observations are source review, not additional executed tests.

Recommendation: fix the crafting recipe collisions and unprivileged garment transfer first, then close the conservation/timing bypasses above before claiming the approved overhaul complete.

## Scoped verification addendum after the 55-GameTest / 31-pure-assertion run

Read-only review of the new controller actions, crafting guards/mixins, reloadable rules, latest shared actions/NPC care, and networking. No builds/tests or source edits were performed. The preceding twelve findings are **closed in the inspected source**: recipe collisions and vanilla spear routes have changed, remote garment replacement is denied, weathering/equipment aging/workstation conservation and wool recovery have explicit fixes, fantasy IDs are covered, mature crop lifecycle/harvest paths are reconciled, and grid crafting is guarded. Data reloads and controller planning/actions also close the previously noted implementation gaps within their documented scope.

Four remaining concrete issues were found in the newly integrated action paths:

1. **P1 — Adventure players bypass block restrictions through commands.** `controllers/WorldActions.java:75-86` accepts adventure mode and validates only `ServerLevel.mayInteract` plus border/range. The local vanilla implementation of `mayInteract` checks spawn protection/border, not adventure `CAN_BREAK`/placement restrictions; vanilla mining separately calls `Player.blockActionRestricted`. An unprivileged adventure player with no adventure-permitted tool can `/survival mine` ordinary blocks, and direct sow/till mutations similarly bypass placement restrictions. Validate the corresponding vanilla player restrictions at both start and completion.

2. **P2 — Controller mining charges the broken-block cost twice.** `controllers/WorldActions.java:183` calls `Block.dropResources` with the real actor, which posts `BlockDropsEvent`; `survival/SurvivalEvents.java:220` charges 0.08 through its handler. The controller then explicitly subtracts another 0.08 on the same line. Thus an otherwise identical controller break costs 0.16 plus timed work, instead of the requested single 0.08 broken-block charge. Keep one authoritative charge.

3. **P2 — Manual labor can run at full capacity in multiple systems simultaneously.** `content/WorkstationEntity.java:31-40` advances each forge independently for its assigned worker, without checking/claiming the actor's world/grid job or shared labor budget. `CraftingGuard.java:17` only coordinates with WorldActions, and `WorldActions.java:149-164` does not reserve capacity against forges. Start two forge jobs for the same nearby actor, then a craft/mine action: all advance at full capacity during the same loaded ticks. Use a shared per-actor manual-work claim/budget across forge, grid, and world actions; passive kiln heating can remain independent.

4. **P2 — Controller sowing rejects valid non-farmland crop placement.** `controllers/WorldActions.java:100-107,186-189` always requires moist farmland and always places the default block state. A normal cocoa placement against a jungle-log side is rejected for missing farmland; even an artificial farmland setup can fail because the default facing is not derived from the support. Sugar cane likewise has its own substrate/water rule. These crops are included in the declared schedules/planner and are plantable by human interaction. Derive the crop-specific placement state/support conditions and preserve identical seasonal validation for both adapters.

No additional recipe-reservation duplication/cancellation loss, client-authoritative mutation, or concrete reload-order failure was established in this scoped inspection. This is a source-review conclusion, not a replacement for the parent's ongoing runtime verification.

## Final scoped closure after controller fixes

Re-inspected only the four addendum findings after the controller worker confirmed its final edits were complete. **All four are closed in the inspected source; no important unresolved issue remains within this confirmation scope.**

- **Adventure restrictions: closed.** `WorldActions.validate` checks actual break/place components for restricted players, including the planting support. The same validation runs before reservation and on each work tick before completion.
- **Duplicate mining cost: closed.** Controller mining retains `Block.dropResources` and the shared `SurvivalEvents.broken` charge; its extra explicit 0.08 deduction is removed.
- **Simultaneous manual labor: closed.** `LaborGuard` coordinates forge claims, world jobs, and credited grid work. Both controller entry points reject forge/grid conflicts before consuming supplies; production rejects competing work; forge advancement and grid work respect the shared claims and recent-work timestamp. World completion records that timestamp before releasing its job, preventing a forge from crediting the same completion interval.
- **Native crop placement: closed.** Farmland requirements apply to farmland crops; cocoa derives a surviving horizontal support/facing, and cane uses its native substrate/water survival rule. The resulting state is used for actual placement, with seasonal checks retained.

The corresponding regression sources cover actual Adventure item components, single-cost mining, cocoa orientations/cane substrate, forge conflicts, credited player grid conflicts, and the world-work completion boundary. Root reported 62 passing GameTests before the final grid/completion fixture extension; execution of that latest extension remains with root. This confirmation ran no builds/tests and changed no source files.
