# Tectonic environment validation — 2026-11-08

Runtime: Minecraft 26.3, NeoForge 26.3.0.77, stable Tectonic 3.0.32, Lithostitched 2.0.6, bundled Apollib 1.2.5. Official artifact SHA-512 values are checked during preparation and Gradle profile loading. No beta or runtime downloader is used by the game.

## Behavior and regressions

- Original baseline: 105 GameTests and 2,325 pure assertions passed before changes.
- Expanded pure suite: 38,688 assertions covering deterministic weather across three seeds, negative coordinates, smooth boundaries, elevation cooling, seasons, wet/dry soil, exact subdivision/threshold accounting, fixed terrain-relative deposit identity and 640-height bounds, plus previous survival/skill regressions.
- Expanded GameTest suite: 113 tests. Both legacy and pinned Tectonic runtimes passed the full suite with isolated 16×16×16 test arenas, explicit sky access and padding. The final transparent-roof change passed all 113 tests in the pinned runtime; final legacy/build results are recorded below.
- The actual weather codec round-trips every field. Fresh location/dimension checks accept current data and reject stale, teleported or other-dimension observations. Actual player and enrolled NPC climate inputs compare equal.
- Irrigation tests use real vessels: reservation removes the held pot; cancellation returns its remaining portion without wear; useful completion spends exactly one last portion and one wear; empty pots cannot start; already wet plots return the pot without XP.
- Historical soil and growth agree between frequent and catch-up updates. Review regressions first failed for growth before sowing, frost after maturity, and lost winter maize. All pass after chronological integration and separating regional frost from legacy calendar frost. Wet-spell glass-roof rain also failed before the native rain hook adopted the physical roof predicate, then passed.
- Prior source-to-cordage, skill parity, provenance, station production, native/controller actions, supply conservation and actual two-worker mineral transfer/processing tests remain included.

Three earlier full runs showed unrelated native-drop, cancellation-drop or navigation failures that passed in isolation. The fixtures declared Minecraft's one-block `empty` template while using areas up to eight blocks wide. Larger isolated arenas removed the interference without weakening assertions. The first larger-arena run exposed the template's default roof; explicitly enabling sky access retained the outdoor habitat checks.

## Actual generated worlds

Three new, isolated Tectonic worlds were booted, their seeds confirmed through RCON, and their versioned profiles saved. Each survey sampled a 5×5 grid from −4,096 to +4,096 twice. Bounded sites were force-loaded, saves flushed and servers stopped before decoding region files. Different seeds intentionally have different inspected chunk counts. This is a diagnostic sample, not a random abundance estimate or a guarantee of a viable spawn.

| Seed | Full chunks inspected | Sampled seafloor/ground Y | Wild plant blocks | Unsupported plants | Cold query median | Warm query median |
| --- | ---: | --- | ---: | ---: | ---: | ---: |
| 42 | 257 | −3 to 104 | 9 | 0 | 47.4 ms | 61 µs |
| 918273 | 641 | 3 to 96 | 46 | 0 | 51.9 ms | 74 µs |
| 20261107 | 641 | −9 to 142 | 0 | 0 | 35.9 ms | 74 µs |

Every survey contained copper, iron, tin, coal and barren district parameters. Saved chunks contained the actual typed ores, coherent host material, clay, sparse stone debris and fallen sticks; no vanilla copper/iron ore bodies bypassed the retained exclusions.

| Seed | Malachite ore | Cassiterite ore | Hematite ore | Coal ore | Loose stones | Fallen sticks | Clay |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 42 | 656 | 519 | 582 | 882 | 32 | 174 | 839 |
| 918273 | 965 | 425 | 1,311 | 992 | 149 | 360 | 76,798 |
| 20261107 | 1,470 | 210 | 611 | 668 | 194 | 122 | 6,729 |

Seed 918273 supplied flax, wheat, maize, beetroot, melon and bramble in the sampled sites. Seed 42's sampled plants were maize only. The third sample was dominated by oceans, groves, snowy taiga and stony shores and contained no wild food/fiber plants. Those two samples require further exploration or imported supplies; this evidence does **not** establish a safe first-day route or a complete local technology chain at every spawn. No resources were added merely to make all regions self-sufficient.

The cold query cost includes generator reference sampling, district anchor sampling and weather calculation; warm queries reuse the bounded lattice. These are diagnostic-command timings on this machine, not total actor tick costs or a large-population TPS benchmark. Caches cap terrain at 8,192 points and heights at 32,768 points per server. Generation accesses the effective generator without synchronous neighboring chunk loads.

Complete samples, block counts, bounds and biome occurrences are in [the machine-readable survey](2026-11-08-tectonic-survey.json).

## Save safety and height limits

An actual isolated world refused to boot after its Tectonic terrain config changed, with: `Tectonic configuration differs from this world's saved generator fingerprint`. The saved profile retained its original fingerprint. Unprofiled old-world refusal, legacy retention, version rejection and explicit profile activation also have GameTest coverage. User worlds were not reset, moved or converted.

The optional 640-height integration check is recorded in the final results below. Tectonic's release codec requires **both** `min_y` and `max_y`; supplying only `max_y` silently uses its default height limits. The QA preparer supplies both, and the initial normalized-default probe is retained as investigation evidence rather than claimed as a 640-height success.

## Dedicated client evidence

The client uses Vulkan on this machine because its NVIDIA OpenGL driver crashes during launch. Two terrain angles show Tectonic's wooded slopes. Two coastal views show local rain while the HUD and server observation report the same warm, wet conditions. Native global `/weather clear` from the stage prelude does not replace regional weather.

The farm is an explicitly controlled fixture. A transparent roof prevents ambient rain from obscuring irrigation; the worker uses a real supplied pot and the command outputs expose the stored soil water and returned vessel. The scene demonstrates the water transaction, not a naturally constructed settlement. Ordinary two-worker material collaboration is covered by the actual extraction/transfer/processing regression.

Final proof paths and inspected replay results are appended below. `tools/capture-environment-proof.ps1` resets only the named fixture worker/items and runs the recorded segments; `-Replay` provides the final dedicated replay. Stage archive preparation uses a copy of the isolated natural world; no user save is used.

## Limits

Climate, rain shadow, province lithology and water balance are gameplay approximations. There is no numerical atmosphere, watershed, plate simulation or reliable forecast. Historical substrate/roof/water availability uses the currently observed physical setting rather than replaying construction. Catch-up is bounded to one year. Wind affects exposure without bending rain geometry. Snapshots expire after five seconds or substantial movement/dimension changes. Regional lightning is not modeled. Autonomous settlements, trade policy, new technology trees and backstories are excluded from this milestone.

## Final results

- Final `qube build`: passed in 6.8 seconds; its Gradle simulation task passed 38,688 assertions. The standalone pure runner also passed 38,688 assertions.
- Final legacy runtime: all 113 GameTests passed in 22.0 seconds (`run/qube/environment-final-legacy-roofs.log`).
- Final pinned Tectonic runtime: all 113 GameTests passed in 30.4 seconds (`run/qube/environment-final-tectonic-roofs.log`).
- Item audit: all 147 localized definitions have distinct bindings, 16?16 PNGs and usable transparent silhouettes.
- Dedicated two-observer replay: seven proof shots, two inspected contact sheets, no warnings, no timing warnings and no missing entities. The last replay read back soil water **0.4494463131**, plus the actual returned `fired_pot` with **water 0, damage 1, count 1** after one supplied portion. Pot lookup explicitly selects that item rather than any nearby seed drop.
- Extended-height runtime: the corrected profile accepted placement at Y639 and rejected Y640. Effective terrain samples reached Y517; a fixed district reference also measured Y512. A saved-world audit found **399 cassiterite ore blocks spanning Y314?335**, proving actual generation above vanilla height. See [district samples](2026-11-08-tectonic-height640.json) and [saved blocks](2026-11-08-tectonic-height640-world.json). The stress profile uses min_y ?64, max_y 640 and vertical_scale 4.5; these are test settings, not the supplied default.
- Existing-world config mismatch was rejected before generation; its original profile fingerprint remained in place.

Inspected final replay sheets: [terrain and regional rain](tectonic-terrain-proof.png), [physical irrigation](tectonic-irrigation-proof.png). The farm is controlled scaffolding; a villager's folded-arm renderer does not expose its reserved inventory, so vessel conservation is established by server item data and the GameTests as well as the visible returned item.

The original CLI sometimes lost its response while a daemon job completed. Final reported results above come from completed job output and logs, not from transport return codes. Qube `build --restart` returned this QA server to its ordinary world; explicitly restarting with `server up --stage` restored the archived natural-world stage before final proof.

The final replay includes a real second server-side player bot at the warm rainy coast while the client remains in cold dry highlands. Their simultaneous climate observations differ by location, the main client keeps its own weather, and `getSpawnSurvivalForaging` / `getSpawnSurvivalMining` synchronize without desyncs. The final farm assertion checks the farmland block and authoritative soil/item data; vanilla visual moisture may tick from 7 to 6 between work completion and capture.
