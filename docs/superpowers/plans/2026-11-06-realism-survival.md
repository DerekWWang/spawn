# Realism Survival Implementation Plan

> **For agentic workers:** Use the development skills, test-first implementation, and independent review. Steps use checkboxes for progress.

**Goal:** Implement the approved natural-world survival simulation equally for players, bots, and scripted humanoids.

**Architecture:** Pure simulation rules feed a persistent server-side entity adapter. Natural-world content and agriculture are independent modules. Server commands expose inspection and validated actions; clients receive state for the survival HUD and health/clothing inspection.

**Tech Stack:** Minecraft 26.3, NeoForge 26.3.0.77, Java 25, Qube build/GameTests/visual replay.

**Spec:** `docs/superpowers/specs/2026-11-06-realism-survival-design.md`

## Global Constraints

- Mod ID `spawn_9b5fbb9`; no runtime third-party dependencies.
- All eligible humanoids, including fake players and scripted entities, use identical simulation rules.
- Server-owned mutations, item consumption, elapsed time, capacities, and output validation.
- Preserve existing unrelated workspace changes; work on a local feature branch in the Qube project directory.
- Do not erase old terrain or player construction. Disable future structure generation and fantasy content.
- Complete all subsystems before claiming the overhaul complete.

## Review Focus

- Automation must not refill needs or grant work output without item consumption and elapsed work.
- Relog/unload/sleep must not duplicate births, reset food ages, or stack capacity modifiers.
- Startup/worldgen rules must preserve natural resources and a viable first-day crafting path.
- Incapacity must affect actual player/NPC actions, not only inspection values.
- Dedicated-server loading and client synchronization must work independently of an integrated server.

## Files and interfaces

- `survival/SurvivalState.java`: needs, anatomy, condition progression, capacities; `advance(long ticks, Activity activity, Climate climate, boolean recreating, boolean sleeping)`.
- `survival/SurvivalApi.java`: `isHumanoid(LivingEntity)`, `state(LivingEntity)`, `save(LivingEntity, SurvivalState)`, `calendar(ServerLevel)`, validated interaction API.
- `survival/SurvivalEvents.java`: real-world activity inputs, injuries, food/water/treatments, movement/work penalties, NPC behavior.
- `survival/SurvivalCommands.java`: own-state inspection and operator controller commands; no free survival refills in normal actions.
- `content/SurvivalContent.java`: `register(IEventBus)` and `init()`; items, blocks, production/preservation/clothing recipes.
- `world/NaturalWorld.java`: content prohibition and generation prevention; own events/mixins/data resources.
- `agriculture/SeasonalLiving.java`: calendar-driven crops, livestock, natural seed/resource sources.
- `client/SurvivalHud.java`, `network/SurvivalNetwork.java`: bounded state synchronization, readable HUD and health/clothing screen.
- `SurvivalGameTests.java`: actual entity integration and deterministic progression; pure Java simulation tests under `src/test`.

### Task 1: Shared physiology and controller-neutral persistence

- [x] Write tests for 0-hunger 0.1 speed, 30%-thirst mental cap, strenuous activity costs, recreation quota/sleep, thermal illness, body damage, bleeding treatment, deadly infection/immunity, and save/reload.
- [x] Run the tests against missing implementation and confirm failure.
- [x] Implement `SurvivalState`, persistent `SurvivalApi`, activity/climate inputs, config, calendar, and the entity event adapter. New eligible humanoids start healthy with empty clothing.
- [x] Wire vanilla player food once, enforce starvation/dehydration, derive capacities with stable attribute IDs, and localize injuries.
- [x] Run pure tests and `qube build`; expand actual-entity GameTests after initial registration.

### Task 2: Natural world, resource content, and timed production

- [x] Write regressions for forbidden entities/items, structure/dungeon exclusions, safe-water processing, finite output costs, preservation, and no item-age reset.
- [x] Implement `SurvivalContent.register(IEventBus)`, `init()`, `NaturalWorld`, natural resource acquisition, kiln/pots, boiling, metal processing, cloth/garments, treatments, tools, dyes, and wear.
- [x] Replace bypassing recipes and install all texture/model/item bindings; use `qube texture` for new raster assets.
- [x] Audit the fresh-world recipe/resource graph from loose stones/fiber/clay to safe water, pots, clothing, copper, bronze, and iron; verify natural generation and focused real actions. Test no-output-without-inputs and timestamp preservation.

### Task 3: Seasonal plants and husbandry

- [x] Write growth/birth tests for wrong season, insufficient days, missing feed/water, partial maturity, sleep/unload catch-up, and duplicate births.
- [x] Implement `SeasonalLiving`: 28-day seasons, the spec crop schedules, natural wild seeds/fiber/fruit, sowing, paddy constraints, frost/dormancy, and bone-meal prevention.
- [x] Implement cow/sheep/chicken/pig pregnancy/incubation/maturity, grazing/troughs, milk/wool recovery, and fertilized eggs.
- [x] Run deterministic calendar tests and actual livestock GameTests; inspect crop models in replay scenes.

### Task 4: Shared interactions, collaboration, and client presentation

- [x] Test own-state versus operator access, malformed actions, absent supplies, remote targets, and inadequate capacities.
- [x] Implement `SurvivalCommands` and a documented server API for inspected state, controller enrollment, supplied actions, social sessions, treatment, clothing, and work. Bots receive no physiological exemptions.
- [x] Implement low-frequency state synchronization and five-state HUD, detailed health/clothing screen, calendar, capacity/condition explanations, and nearby inspection.
- [x] NPCs autonomously seek available food/water/recreation/supplies and cooperate through nearby treatment/social actions; expose actions to external planners without promising built-in human-level AI.
- [x] Verify two-player/bot interactions on a dedicated server.

### Task 5: Integrated tests, review, and delivery

- [x] Replace starter arithmetic tests with registered behavior GameTests and run the full `qube test` suite.
- [x] Run `qube build`, dedicated server startup, representative gameplay scenarios, `qube shot --replay`, and dedicated replay. Open every contact sheet before visual claims.
- [x] Request an independent code review against the approved spec; address material findings with focused regressions.
- [x] Update README with play/progression/controller instructions, item policies, timing, world-generation scope, and actual verification results.
- [x] Commit implementation on the local feature branch, leaving unrelated preexisting files untouched.

## Execution record

User approved the written design and explicitly instructed execution. Controller clarification is incorporated above. Routine implementation choices are made within that authorization; no further design approval round is required.

Final evidence and practical limits: [validation-report.md](validation-report.md). A full multi-day human balance playthrough was not run.
