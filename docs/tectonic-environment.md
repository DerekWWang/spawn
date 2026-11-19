# Tectonic environment runtime

For Minecraft **26.3**, NeoForge **26.3.0.77**, Tectonic **3.0.32**, and Lithostitched **2.0.6**. Tectonic includes Apollib 1.2.5. Official release hashes and source links are in `config/tectonic-runtime.json` in the repository (included as `tectonic-runtime.json` in the runtime ZIP).

## Install and create a world

1. Use a fresh Minecraft 26.3 / NeoForge 26.3.0.77 installation or server directory.
2. Copy the runtime ZIP's `mods` and `config` directories into that installation. The three jars are Spawn, Tectonic, and Lithostitched. Install the same stack on clients and server; the weather protocol is version 4.
3. Create a **new world**. Keep its Tectonic configuration with its save. The supplied config disables artificial lanterns along underground rivers; Apollib fills in Tectonic's remaining defaults at startup.

Existing vanilla worlds retain legacy simulation when Tectonic is absent. Installing Tectonic over a saved legacy world is refused. An unprofiled existing world also refuses automatic adoption. The advanced JVM flag `-Dspawn.adoptEnvironment=true` explicitly allows adopting an unprofiled world after a backup; previously generated chunks retain their old resources and terrain, so a new world is strongly preferred. This flag does not override a saved legacy profile or a generator fingerprint mismatch.

The file `world/data/spawn_9b5fbb9-environment.json` stores profile, epoch, season length, resource schema, generator version and configuration fingerprint. Changing a saved world's terrain config or season length fails with a diagnostic instead of silently changing its generation/history. Restore its original configuration to reopen it. Do not delete the profile to bypass these checks.

## What the environment changes

- Tectonic remains the terrain generator. Spawn samples its effective sea level, heights and climate density fields on a bounded 64-block lattice without loading neighboring chunks.
- Geological provinces span 2,048 blocks. Their composition biases finite 256-block mineral districts, including barren areas. A district's deposit depth follows one fixed terrain reference; it stays a coherent body across chunk boundaries. Quarry host rock follows the province. Clay follows actual nearby water.
- Wild plants and apple groves use stable baseline warmth, humidity, relief, soil and habitat. Mountain exposure, dry ground and unsuitable temperatures reduce vegetation. Current rain and player construction never change which deposits generate.
- Regional weather varies smoothly with geography and time. Elevation, prevailing wind and an upwind barrier approximation affect temperature and rainfall. Each player receives their own server observation; rain/snow rendering, particles and rain sounds use that snapshot. Surface snow/ice and rain interactions use the same server weather.
- Bodies share actual ambient temperature and wind. Roofs block rain; walls reduce wind. Unheated roofs do not create room-temperature air. Clothing, wetness, fires, exertion and water reserves still matter. Players and enrolled NPCs use the same path.
- Crops persist independent soil-water history. Weather is integrated in fixed 600-tick buckets, up to one year of catch-up. Bare-field time never grows a newly planted crop; frost is evaluated in chronological order. Rice still requires physical paddy water. Irrigation never replaces rice flooding or elapsed growth days.
- Animals graze according to local growing conditions and pasture moisture. Stored feed, hay, drinking water and shelter support animals when pasture or exposure is unsuitable. Trough supplies and product recovery remain finite.

Right-click prepared farmland with an **open water pot**, or use `/survival irrigate x y z`. The job reserves the actual vessel, takes 60 base loaded work ticks, and uses one portion plus normal vessel wear only when useful water reaches the plot. Cancelling or finishing an already wet plot returns the unchanged pot and gives no practice. `/survival control <actor> irrigate x y z` provides the same action to bots/NPCs. Use `/survival climate` or `/survival control <actor> climate` for current observations.

`/survival environmentSurvey x z` is an explicit game-master diagnostic for terrain/deposit validation. It is separate from agent observations and can reveal a district's generation parameters. Ordinary resource discovery/prospecting still reports only visible blocks and physical samples.

## Development

Run `powershell -NoProfile -ExecutionPolicy Bypass -File tools/qube-tectonic.ps1 build` (or `test`) to prepare hash-verified local runtime dependencies and activate the explicit `tectonicProfile` Gradle property. Ordinary `qube build` / `qube test` preserve the legacy runtime. No dependencies are downloaded by the running game.

`python tools/prepare-environment-qa.py` creates ignored, isolated QA projects for seeds 42, 918273, and 20261107. It never copies or changes a user world. Boot a project with `qube server up --project run/qube/tectonic-surveys-v2/42`; run `tools/survey-tectonic.ps1 -Seed 42` and `tools/select-tectonic-sites.py 42` for repeatable terrain diagnostics and bounded site generation.

## Scope and limits

This is an environmental gameplay model, not a numerical atmosphere, watershed or plate-tectonics simulation. Rain shadow and coast effects are approximations. Historical crops and livestock assume the currently observed roof, adjacent water and pasture substrate held during catch-up; construction histories are not replayed. Client weather covers the immediate observer and expires after five seconds or a substantial move/dimension change. Wind affects exposure but does not physically bend rendered rain. Thunderstorms/lightning are not simulated regionally.

Resource scarcity and geography support specialization, quarries, stored supplies and transport through existing physical production and exchange. Autonomous settlement planning, trade strategy and new technology trees remain future milestones. Backstories are excluded. The surveyed starts are not guaranteed to contain every early resource within walking distance.

See `docs/validation/2026-11-08-tectonic-environment.md` in the repository for the measured results and visual evidence.
