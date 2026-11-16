# Skills, ecology, and geology implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Give every humanoid durable RimWorld-style learning, replace universal resource scatter with identifiable habitats and regional deposits, and give all items correctly named 16 by 16 icons.

**Architecture:** Keep the existing controller-neutral survival and manual-work systems. Add a pure skill model and pure deterministic resource fields, then thin Minecraft adapters. Separate skill, world generation, and asset work so their integration points can be reviewed explicitly.

**Tech stack:** Java 25, Minecraft 26.3, NeoForge 26.3.0.77, existing dependency-free simulation tests and GameTests, Qube CLI, JSON resources, original pixel grids.

**Spec:** [Approved design](../specs/2026-11-07-skills-ecology-geology-design.md).

## Global constraints

- No backstories, profession locks, fictional traits, or new economy subsystem.
- Players, fake players, NPCs, and enrolled humanoids share rules and real costs.
- Keep all existing registry identifiers and support survival save schemas 1 through 5.
- Levels are 0 through 20. Passion factors are 0.35, 1.0, and 1.5. The daily soft cap is 4,000 net XP; further learning is multiplied by 0.2.
- New actors are novices with no passions; interests are explicit persistent state.
- Simulation days are 24,000 ticks; offline wall time earns no work or learning.
- New terrain only: never remove old generated terrain or player construction.
- Resource generation modifies its own chunk and never forces adjacent chunks to load.
- District cells are 256 blocks; 20% are barren; productive family weights are copper 35%, iron 30%, tin 15%, coal 20%.
- Textures go through `qube texture`; open previews and require empty `bindings_missing`.
- Flat 16 by 16 item icons; no new 3D tool models or product dependencies.
- Keep unrelated `.gitignore`, `AGENTS.md`, and `CLAUDE.md` changes out of task commits.
- Use the required gstack browse skill for any additional Wikipedia or documentation research.

## Execution and progress

The user reviewed this implementation plan and explicitly requested execution.
The spec was approved by the user's “this looks good” response. Native execution
was selected from the recommended handoff. Record task progress in
`docs/superpowers/plans/2026-11-07-skills-ecology-geology-execution.md`.

Use the current `realism-survival` project checkout for Qube unless an isolated
workspace is explicitly selected. Its project configuration, toolchain, and
warm-client state already refer to this location. Do not create another checkout
or disturb user edits as a side effect of planning.

## Review focus

1. Schema-5 experts and fractional XP must retain their relative competence, while invalid new records must never partially replace a valid body.
2. Jobs spanning midnight, cancellation, reconnect, and save/reload must neither repeat XP nor reset the daily cap.
3. Negative coordinates and reversed chunk-generation order must produce the same patches, hosts, and deposits.
4. Hidden reserves must not leak through bot discovery or prospecting, and exhausted nodes must stay exhausted after reload.
5. New terrain must support food, water, basic tools, farming, and pottery without requiring metal or universal seed drops.

## Responsibility map

All Java paths below are relative to
`src/main/java/dev/qube/spawn_9b5fbb9/`.

| Unit | Owned files | Contract |
| --- | --- | --- |
| Skill model | `survival/SkillSet.java`, `survival/SurvivalState.java` | Named versioned skill records, old-save migration, simulated learning clock |
| Skill integration | `survival/SkillApi.java`, commands, work adapters, client/network | Validated work exposure, scale conversion, inspection |
| Resource fields | New `world/ResourceFields.java` | Pure seed/coordinate sampling, no Minecraft or mutable global RNG |
| Resource generation | New `world/NaturalResourceGeneration.java`, worldgen JSON | Chunk-local application of surface and geological fields |
| Resource content | Ground resources, fruit trees, content registration, loot/recipes | Named finite sources and ore identities |
| Assets | Item/block model JSON, textures, language JSON, audit CSV | Correct identity, distinct silhouettes, complete bindings |
| Validation | Simulation tests, GameTests, validation segments, reports | Behavioral and visible evidence |

### Task 1: Versioned learning state and migration

**Files:** modify `survival/SkillSet.java`, `survival/SurvivalState.java`,
`src/test/java/dev/qube/spawn_9b5fbb9/survival/SimulationTests.java`,
`src/test/resources/README.md`; add an independently generated schema-5 fixture.

**Interfaces:** preserve `experience(Skill)`, `level(Skill)`, `untilNext(Skill)`,
`practice(Skill,double)`, `setLevel(Skill,int)`, and `copyFrom(SkillSet)`.
Add `Passion { NONE, INTERESTED, BURNING }`, `passion(Skill)`,
`setPassion(Skill,Passion)`, `learningMultiplier(Skill)`, `dailyNetExperience(Skill)`,
`decayPerDay(Skill)`, `advance(long ticks)`, and a bounded `learningFactor` property.
New skills: `MINING`, `CONSTRUCTION`, `COOKING`, `ANIMALS`, `MEDICINE`.
Keep existing six enum names for integrations; serialize records by stable names.

- [ ] Capture a schema-5 fixture using the existing encoder before changing it, with unequal levels, fractional progress, clothing, needs, and injuries.
- [ ] Add failing assertions for level thresholds 0/1/10/20, factors 0.35/1/1.5, 3,900-to-4,100 XP cap crossing, and simulation-day rollover. For an interested novice, two 2,000-XP awards reach 4,000; the next 100 base XP adds 20.
- [ ] Add failing tests for decay at levels 9/10/20, the 1,000-XP downgrade buffer, bounded top-level reserve, split versus combined tick advances, invalid inputs, unknown/duplicate record identifiers, copy, and old-fixture migration.
- [ ] Run `powershell -File tools/test-simulation.ps1`; confirm the new behavioral assertions fail for missing mechanics.
- [ ] Implement explicit XP thresholds and the reference decay table. Use 55,000 cumulative XP at 10 and 265,000 at 20. Bound the level-20 XP reserve at 32,000. Persist the level separately so the downgrade buffer is represented accurately.
- [ ] Advance decay and the daily clock from `SurvivalState.advance` only when actual simulation ticks advance. Initialize its day offset consistently with the body's calendar offset. Add schema 6; read old six doubles using the old formula and continuous score divided by five.
- [ ] Run the pure suite until the new behavior and all existing physiology assertions pass. Review the new state size against the 8,192-byte packet bound.

### Task 2: Work learning, recipes, and all skill-scale consumers

**Files:** `survival/SkillApi.java`, `survival/SkillEvents.java`,
`controllers/WorldActions.java`, `content/WorkstationEntity.java`,
`content/Production.java`, `content/Pottery.java`, `survival/SurvivalApi.java`,
`agriculture/Husbandry.java`, `survival/CraftingGuard.java`, and
`data/spawn_9b5fbb9/survival/content_rules.json`.
Tests: existing practiced-content/controller tests plus new skill integration tests.

**Interfaces:** Task 1's model is authoritative. Add
`SkillApi.awardWork(LivingEntity,Skill,long activeTicks,double learningWeight)`;
its base rate is 0.25 XP per actual active tick times the nonnegative bounded
weight. Existing recipe practice values supply weight `practice / 25.0`.
Preserve `award` for real bounded outcome awards. Store active exposure and a
completion identity with persisted jobs, never in a process-global entity cache.

- [ ] Add failing real-entity tests proving native/controller parity, no XP on cancelled or rejected work, one award across reload, and unchanged work speed after scale normalization. Test two different valid work durations at the same weight to prove time-based learning.
- [ ] Count exposure only when validated manual work actually advances, and award once on successful completion. Heating alone, absent workers, empty crops, free block placement, and repeated UI queries earn none.
- [ ] Connect mining to extraction, medicine to effective consumed treatment, animals to real tending/product collection, construction to real manual wooden assembly, and cooking to existing attended ingredient work. Do not turn passive heating into an attended job for XP.
- [ ] Move crossbow-stock assembly to construction competence while keeping its real workstation, wood, binding, and time requirements. Keep textiles under weaving. Reject alternating placement/breaking as construction practice.
- [ ] Convert recipe/default/datapack skill gates from 100-scale to 20-scale: copper 4, bronze 8, iron 12, iron armor 14, pants 2, jackets 4, standard pottery 5, fine pottery 12. Normalize work rate to `0.5 + level / 40.0` and pottery effective score to `level * min(manipulation,mental)`.
- [ ] Update existing test setup levels to equivalent new competence; retain assertions about conservation, range, fuel, binding supplies, and actual station stages.
- [ ] Run `qube build` and relevant `qube test --tests` selections; inspect every failure before continuing.

### Task 3: Skills inspection and controller compatibility

**Files:** `survival/SkillApi.java`, `survival/SurvivalCommands.java`,
`network/SurvivalNetwork.java`, `client/SurvivalHud.java`, language JSON,
controller tests, and new UI validation segment `validation/realism-skills.json`.

**Interfaces:** controller skill entries include `level`, `maxLevel`,
`experience`, `experienceToNext`, `passion`, `learningMultiplier`,
`dailyNetExperience`, `dailySoftCap`, `decayPerDay`, `workRate`, and
`effectiveLevel`. Top-level inspection identifies schema 6. Bump the negotiated
network protocol from 2 to 3. Existing skill keys retain their names.

- [ ] Add failing inspection tests for an injured expert and a passion-weighted novice, including correct capacity limits and daily-learning fields.
- [ ] Add operator-only `debug_passion <target> <skill> <none|interested|burning>` and change debug skill bounds to 0 through 20. Normal controller verbs cannot set competence or passions.
- [ ] Implement a separate readable skills page from the health screen, with all eleven rows, progress bars, selected-row details, and scrolling at small resolutions. The screen does not pause simulation.
- [ ] Verify snapshot, reconnect, and respawn copy every new state field, including daily cap and passions.
- [ ] Run build/tests, then capture and inspect the skills page with a real client, headless player, and NPC. Replay the segment in Task 8.

### Task 4: Deterministic surface ecology and finite named sources

**Files:** create `world/ResourceFields.java`, `world/NaturalResourceGeneration.java`,
and focused world-generation tests; modify `content/GroundResourceBlock.java`,
`content/FruitTreeBlock.java`, `content/SurvivalContent.java`, worldgen and biome
modifier JSON, loot tables, blockstates/models, plant item definitions, and
`tools/test-simulation.ps1` to compile the new pure resource-field source.

**Interfaces:** `ResourceFields` accepts a seed and integer coordinates and
returns immutable surface-patch candidates. The Minecraft adapter filters
candidates by real substrate, climate, light, water, and existing vegetation.
Register `natural_surface` as a `Feature<NoneFeatureConfiguration>` with one
chunk-local application per decorated chunk. Never use neighboring placement.

- [ ] Add failing pure tests for deterministic candidates at positive and negative coordinates, chunk-boundary continuity, reversed evaluation order, radius bounds, spacing, and expected patch density.
- [ ] Add failing GameTests for plant-specific drops, finite harvest, actual support, no rock-to-stick bonus, no mixed seed bundle, and no free sapling from harvesting apples.
- [ ] Implement candidates at the approved defaults: one plant-patch candidate per eight chunks, radius 3 through 6, 5 through 12 occupied positions; ordinary-surface stone occupancy at most 0.1%, rocky contexts at most 3%; fruit-grove candidate per sixty-four chunks, 2 through 5 trees, minimum trunk separation 5.
- [ ] Register separate forage identities for fiber, yarrow, brambles, rice, maize, wheat, root crops, and gourds. Define each exact yield in a data table. Preserve legacy generic blocks but stop generating them; their fallback harvest must not mint every seed.
- [ ] Generate fallen sticks near actual woody vegetation, and rock fragments in compatible rocky/alluvial contexts. Use small blockstate model variations for rock fragments.
- [ ] Preserve seasonal and establishment rules. Give the apple source an appropriate tree structure using vanilla logs/leaves and an identifiable harvest block, rather than placing lone generic fruit blocks everywhere.
- [ ] Replace the three old blanket surface modifiers. Keep new crop sources available without metal and document retained domesticated crop stands as a gameplay approximation.
- [ ] Run the pure tests and targeted GameTests; compare an ordinary grassy area and a valid resource patch in the final worldgen survey.

### Task 5: Regional geology and resource-processing identity

**Files:** extend `world/ResourceFields.java` and `world/NaturalResourceGeneration.java`;
modify `content/SurvivalContent.java`, `content/Production.java`, recipe/loot JSON,
worldgen modifiers, mining tags, and world-generation tests.

**Interfaces:** add immutable `District` and `DepositSample` records. Provide
`district(long seed,int cellX,int cellZ)` and
`sampleDeposit(long seed,int x,int y,int z)` with no world access. Register
`regional_geology` as a second chunk-local feature. Material identities are
`malachite`, `cassiterite`, `hematite`, and `limestone`; keep old ore IDs readable.

- [ ] Add failing tests for cell probabilities, mineral weights, fixed-seed reproducibility, negative cell coordinates, finite extents, cross-chunk ore continuity, host validity, and distinct barren terrain.
- [ ] Implement 256-block cells, 20% barren selection, approved mineral weights, jittered centers, oriented bodies 20 through 48 blocks across and 4 through 8 vertically, and 15% through 30% ore grade within mineralized host envelopes.
- [ ] Generate coherent mineable granite/limestone/sandstone quarry bodies and appropriately placed clay/sand/gravel fields separately from the rarer ore districts. Replace only eligible natural rock or sediment in the current chunk.
- [ ] Add malachite-bearing copper, cassiterite-bearing tin, and hematite iron blocks/samples, required harvest tags, and material-conserving routes into existing crushing/smelting chains. Keep the original generic ore routes for old saves.
- [ ] Generate layered coal and iron bodies and a granite-associated tin envelope. Provide occasional actual outcrop evidence, not decorative markers that grant unearned ore.
- [ ] Remove every replaced vanilla copper/iron/coal placement variant and the original tin scatter. Inspect target-version registry entries through `qube api`; do not guess feature names. Audit other retained ore families and document them.
- [ ] Add real-world tests for correct tool/drops, exhausted blocks across reload, absence of removed scatter, and no forced neighboring chunk generation.
- [ ] Run pure and GameTests, followed by a multi-seed count/connectivity survey recorded for Task 8.

### Task 6: Observable resources and bot prospecting

**Files:** new `controllers/ResourceActions.java`; modify `controllers/WorldActions.java`,
`survival/SurvivalCommands.java`, and `survival/SurvivalApi.java`; add controller tests.

**Interfaces:** add `ResourceActions.discover(LivingEntity,int radius)` returning
a bounded JSON list, and `ResourceActions.prospect(LivingEntity,BlockPos)` returning
`SurvivalApi.ActionResult` with an observed mineral/host description. Commands
are `/survival resources` and `/survival prospect <x> <y> <z>`, with equivalent
operator controller verbs and planning documentation.

- [ ] Add failing tests for loaded-chunk/range/visibility restrictions, no hidden-ore leakage, finite list limits, and native/controller parity.
- [ ] Discover at most 64 visible resource sources within a maximum radius of 16 blocks. Include actual identity, position, habitat, readiness, and known gathering method.
- [ ] Prospect only a visible outcrop within four blocks or an actually held extracted sample. Use mining competence to refine identification confidence; never return generated deposit coordinates, unobserved reserves, or guaranteed buried directions.
- [ ] Keep extraction on existing timed mining jobs, with real tools, wear, hunger, capacities, and permissions.
- [ ] Run a two-actor scenario where one extracts finite ore and another carries/processes the actual output; assert inventory and fuel conservation and independent XP.

### Task 7: Wikipedia-backed names and original item icons

**Files:** all affected `assets/spawn_9b5fbb9/items`, `models/item`, `textures/item`,
language JSON, the approved item-reference audit, and new resource source entries.
Add `tools/audit-item-assets.ps1` for resource integrity and an inventory-atlas segment.

**Interfaces:** the existing 119-row audit is the initial manifest. Extend it
for newly registered items with `registry_id`, `current_name`,
`proposed_display_name`, `wikipedia_reference`, `icon_brief`, and `notes`.
It is not permission to misidentify an unspecified legacy item as a new species.

- [ ] Add an asset audit that reports the currently shared placeholder bindings and verifies registered items, localized names, PNG dimensions, item definitions, and referenced models/textures. Run it to establish the current missing/distinctness failures.
- [ ] Read additional Wikipedia references using gstack browse only for genuinely new identities. Preserve honest generic treatment classes and unknown legacy materials; do not invent a chemical active ingredient.
- [ ] Use `qube texture write` templates for tool shapes, ingots, and appropriate nuggets/dust. Use original 16 by 16 palette grids for seeds, ore fragments, bundles, cord, woven fabric, vessels, garment silhouettes, medicine containers, blanks, and other shapes. No hand-written PNG bytes.
- [ ] Give every item its correct model binding and material palette. Blanks visibly lack finished fittings; crushed ores are granules, not ingots. Use flat item icons for tools and station inventory items while preserving their in-world block models.
- [ ] Review every Qube enlarged preview, require zero missing bindings, run the asset audit, and build a labelled atlas covering every affected item.
- [ ] Inspect the atlas at enlarged pixel scale and in actual inventory/held-item captures. Fix ambiguous silhouettes or unreadable palettes before acceptance.

### Task 8: Integrated verification, evidence, and documentation

**Files:** `README.md`, execution ledger, new validation report,
`validation/realism-skills.json`, `validation/realism-resources.json`,
`validation/realism-items.json`, and a deterministic survey utility under `tools/`.

**Interfaces:** Tasks 1 through 7 must be present before final verification.
The report states actual commands, counts, seeds, images opened, remaining
approximations, and old-terrain compatibility limits.

- [ ] Run `qube build`, `qube test`, and the item-asset audit. Fix failures; do not weaken the existing material, station, timing, health, or permission contracts to make tests pass.
- [ ] Survey three fixed seeds with both positive and negative chunk coordinates and reversed chunk order. Record surface coverage, habitat violations, quarry distribution, ore counts, connected extents, barren spacing, and removed feature variants.
- [ ] Demonstrate a metal-free first-day route to food, water, basic tools, pottery, and a planted crop using actual reachable sources. Document any remaining abstraction about domesticated plants.
- [ ] Start the dedicated stage, capture skills UI, surface resources, two quarry/mine angles, visible outcrops, actual bot collaboration, and the inventory atlas using Qube segments.
- [ ] Run `qube shot --dedicated --replay`; open every contact sheet printed before making visual claims. Confirm server/client skill agreement and persisted interests/caps on reconnect and respawn.
- [ ] Perform an independent whole-change review against the spec, especially the five review-focus conditions. Resolve material findings and run affected checks again.
- [ ] Update README and report the actual result, generated-world limitations, and retained simulation approximations. Keep unrelated user files untouched. Leave changes reviewable locally; no push, merge, or publication is part of this request.

## Plan self-review

| Connection | Check and resolution |
| --- | --- |
| Tasks 1 / 2 / 3 | All use the same eleven enum keys, 20-level scale, passion factors, and versioned state. Gate and UI conversion follow the model change. |
| Tasks 2 / 6 | Prospecting identifies evidence; extraction stays in the shared manual-work adapter and is the source of mining learning. |
| Tasks 4 / 5 | A single pure resource-field unit owns hashing; two separate features apply surface and geological results only inside their current chunks. |
| Tasks 4 / 5 / 7 | Content registration precedes final asset coverage. New typed identities extend the reference audit; legacy generic IDs remain honest. |
| Tasks 2 / 4 / 5 | New drops and ore-processing recipes retain real inputs, timed work, and compatibility routes. No general seed bundle or renewable mineral node remains. |
| All tasks / Task 8 | Each behavioral unit has focused tests; final build, full tests, surveys, and inspected Qube replay provide integration evidence. |

Every approved spec section is owned by a task. Unknown historical crop
domestication and complete tectonics remain documented boundaries, not hidden
implementation requirements. Execution method: native, with a fresh final review.
