# Tectonic Environment Implementation Plan

> **For agentic workers:** Use executing-plans to implement this plan task by task. The user explicitly requested implementation of the presented design; continue natively without another permission handoff. One fresh final review is required.

**Goal:** Integrate Tectonic geography with finite resources and shared regional climate that changes survival, agriculture and livestock decisions.

**Architecture:** Tectonic owns terrain. A deterministic terrain adapter and pure environment model supply generation and runtime consumers, with versioned world settings, bounded caches and local weather snapshots. Physical irrigation uses the existing timed controller work path and real pottery water.

**Tech stack:** Java 25, Minecraft 26.3, NeoForge 26.3.0.77, Tectonic 3.0.32, Lithostitched 2.0.6, Qube.

**Spec:** `docs/superpowers/specs/2026-11-08-tectonic-geography-climate-design.md`

## Global constraints

- Retain all previous skills, item assets, save identities and conservation rules.
- Do not reset, move or rewrite user worlds. Tectonic development runtime is an explicit profile; new isolated worlds prove it.
- Pin dependencies and hashes. Tectonic bundles Apollib 1.2.5; do not duplicate it.
- Baseline generation is independent of weather/time/player buildings. Generation writes only its own chunk and never loads neighbor chunks synchronously.
- Regions start at 2,048 blocks, mineral districts remain 256 blocks. Preserve finite bodies, barren areas and prior placement exclusions.
- Player, fake-player and NPC simulation consumes the same environment. No backstories, autonomous societies or additional technology chains in this milestone.
- Keep station recipes and fuel costs. No runtime downloads or silent generator substitution.
- Preserve the current feature branch and its uncommitted prior implementation; no automatic commits or pushes.

## Review focus

1. Old or unprofiled worlds must not silently opt into Tectonic generation; profile mismatch must be explicit.
2. Pure terrain sampling at borders, negative coordinates and high build limits must not deadlock world generation or change with chunk order.
3. Sleeping, reloads and historical plot catch-up must not create water, crops, labor or duplicate XP.
4. Rain/snow seen by each client must match its own server weather, while roofs and enclosed rooms have distinct effects.
5. Irrigation cancellation, item replacement, full plots and last water portions must conserve the actual vessel and water.

### Task 1: Pinned runtime and world profile

Files: `build.gradle`, `gradle.properties`, new `config/tectonic-runtime.json`, `tools/prepare-tectonic-runtime.ps1`, `environment/WorldEnvironment.java`, mod startup binding, integration tests.

Interfaces: `WorldEnvironment.settings(ServerLevel)` returns versioned profile/epoch/seed settings; `active(ServerLevel)` selects the new simulation. Explicit `tectonicProfile` Gradle property controls development dependencies. Preserve a legacy profile when adopting existing vanilla worlds.

- [x] Add profile/persistence regressions and run them red.
- [x] Add pinned dependency resolution and explicit profile diagnostics; validate release artifact hashes.
- [x] Boot the actual stack in an isolated new world; verify profile persistence and no silent activation on existing unprofiled saves.

### Task 2: Pure climate and terrain adapter

Files: new `environment/EnvironmentModel.java`, `environment/TerrainSampling.java`, `environment/EnvironmentApi.java`, `EnvironmentModelTests.java`, simulation test runner updates.

Interfaces: immutable `Terrain`, `Weather`, `Exposure`, `Settings` records; `baseline(seed,x,z,terrain,settings)`, `weather(seed,x,z,tick,terrain,settings)`, and `soilWater(...)`; adapter `TerrainSampling.sample(WorldGenLevel,ChunkGenerator,int,int)` and server equivalent. Cache at a bounded spatial lattice and obtain reference heights from the effective generator.

- [x] Red tests: elevation cooling, smooth spatial/time boundaries, reproducibility, negative coordinates, wet/dry water balance and interval composition.
- [x] Implement the pure model and bounded adapter with explicit world-height clamps and no neighbor chunk reads.
- [x] Prove cache bounds and actual Tectonic sampler behavior with GameTests and surveys.

### Task 3: Terrain-conditioned finite geology and ecology

Files: `world/ResourceFields.java`, `world/NaturalResourceGeneration.java`, resource field and adapter tests.

Interfaces: add a terrain-conditioned district overload retaining the legacy signature; geological center elevation is derived once at its district reference point, not per-column. Province composition and host setting modify probabilities without guaranteeing ore.

- [x] Red tests: highland/lowland anchor shift, finite coherent body and barren province distributions.
- [x] Use stable climate/slope for species and vegetation density; suppress alpine/open-plant habitat that conflicts with local baseline.
- [x] Apply new generation only under the versioned profile; preserve old identifiers and mining provenance.
- [x] Verify reverse order, negative borders, new height limits and replacement of old ore rules.

### Task 4: Soil history and physical irrigation

Files: `agriculture/SeasonalCrops.java`, `controllers/WorldActions.java`, `content/Pottery.java`, new environment GameTests.

Interfaces: plot soil water in existing crop records, independently timestamped; `SeasonalCrops.irrigate(ServerLevel,BlockPos,double)` returns whether useful water was accepted. Timed `irrigate` reserves a real pot, consumes one portion only on useful completion, and refunds the unchanged vessel on cancellation.

- [x] Red tests: dry/rain plot behavior, reload/catch-up equivalence and real pot conservation.
- [x] Use shared historical temperature/rain for crop growth and frost while preserving calendar duration and rice's real paddy requirement.
- [x] Add native/controller irrigation commands and capacity/range/permission validation.
- [x] Verify last portion, cancel, moved actor and already-wet plot outcomes.

### Task 5: Actor exposure and livestock

Files: `survival/SurvivalEvents.java`, `SurvivalState.java`, `agriculture/Husbandry.java`, environment adapter tests.

Interfaces: shared `EnvironmentApi.at(level,pos,tick)` and bounded `exposure(level,pos)`; preserve old Climate constructor and legacy profile behavior. New climate carries actual ambient temperature and effective wind, with enclosure independent of roof cover.

- [x] Red tests: naked/clothed, windy/roofed/enclosed, wet/dry, player/NPC equivalence.
- [x] Connect body heat and thirst to shared climate. Unheated roofs must not create room-temperature air.
- [x] Connect grazing/exposure to local conditions, retaining physical feed/water and product recovery.
- [x] Run existing survival and husbandry regressions.

### Task 6: Local weather synchronization and observation

Files: `network/SurvivalNetwork.java`, client weather state/mixin, HUD, `controllers/EnvironmentActions.java`, mixin config, packet tests.

Interfaces: versioned local weather payload with ambient, rain, wind and snow condition; per-client interpolation/expiry; observation commands report current local conditions and active profile without hidden resources or perfect forecasts.

- [x] Red tests for profile/weather payload round-trip and stale/dimension mismatch handling.
- [x] Bind local precipitation rendering, rain/snow choice and sounds to authoritative local snapshots.
- [x] Add readable local conditions to health/HUD and controller inspection.
- [x] Dedicated tests compare distinct players' local conditions and synchronized actor state.

### Task 7: Integrated evidence, review and documentation

Files: validation segments, three-seed survey tooling/report, README, execution ledger.

- [x] Run full baseline and Tectonic-profile build/tests plus unchanged asset audit.
- [x] Survey three isolated Tectonic seeds across multiple settings and record heights, ore bounds, plants and basic supplies.
- [x] Capture two-angle terrain, local weather, farm/irrigation and actor adaptation scenarios with dedicated Qube replay. Open all printed contact sheets.
- [x] Measure terrain cache/generation and ordinary actor sample costs; record evidence and limits.
- [x] Dispatch one fresh final reviewer; resolve important findings in one regression-driven pass and rerun affected/full checks.
- [x] Update README, world migration/profile instructions, runtime dependency manifest and final validation report.

## Self-review

Tasks 1-2 define profile identity and shared pure interfaces consumed by Tasks 3-6. World generation always uses baseline climate; bodies and plots use runtime/historical weather. Task 4 owns vessel reservations and plot timestamps; Task 5 owns animal conservation; Task 6 owns visual consistency. Task 7 verifies all five review-focus conditions and the actual dependency stack. New technology/society AI remains a subsequent milestone as presented in the approved draft.
