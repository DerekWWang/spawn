# Execution ledger: 2026-11-08-tectonic-environment

Spec: `docs/superpowers/specs/2026-11-08-tectonic-geography-climate-design.md`
Plan: `docs/superpowers/plans/2026-11-08-tectonic-environment.md`

User approved the presented design with "Implement this" and explicitly requested execution. Continue natively in the existing `realism-survival` feature checkout; it contains the prior uncommitted implementation that this work extends. Creating a clean checkout from HEAD would omit that required work. Preserve unrelated changes and avoid commits/pushes.

The explicit implementation request supplies authorization to execute the plan without another approval handoff. One fresh final review follows native implementation, as required by the execution skill.

Pre-flight: Tasks 1/2 share versioned settings and active-profile detection; Tasks 2/3 share stable terrain; Tasks 2/4/5 share runtime and historical climate; Tasks 4/6 share current actor observations. Generation never consumes current weather. Existing actor schema/skill state remains authoritative. No interface conflicts identified.

Baseline: 2,325 pure assertions and all 105 original GameTests passed before Java changes.

Tasks 1–6 implemented. Task 7 final verification, packaging and documentation in progress.

Ruling: implement the pure environment contract before completing runtime binding — Tasks 1/2 share Settings and profile detection — this avoids two competing representations. Native execution remains in the existing checkout; no commits or user-world mutations.

Task 1: pinned stable release artifacts and SHA-512 checks, optional Gradle runtime wrapper, production package script, loader/version guard, startup profile file, config fingerprint and lantern exclusion. Actual stack booted successfully on NeoForge 26.3.0.77. The profile regression first failed on beta acceptance, then passed after exact version validation. Legacy saves refuse silent Tectonic substitution. Three fresh isolated profiles persisted successfully.

Task 2: pure deterministic weather/soil model, bounded 64-block terrain lattice (8,192 terrain points, 32,768 cached heights), effective-generator height/climate sampling and shared exposure API. Initial model failed elevation cooling; the completed pure suite passed 38,688 assertions. Minecraft 26.3 uses createClimateSampler(SamplerContext.EMPTY_UNCACHED), not the older sampler() accessor. Generator queries never load neighboring chunks.

Task 3: 2,048-block geological province composition, barren districts, fixed terrain anchors for finite mineral bodies, province quarry hosts and stable baseline vegetation filters. Anchor-height regressions first failed for the missing overload, then passed. Three saved-world audits found all four mineral families and zero unsupported plants. Terrain/weather observations and counts are preserved in docs/validation/2026-11-08-tectonic-survey.json. Seed samples are intentionally uneven and do not prove every start is viable.

Task 4: independent soil timestamps, fixed historical weather buckets, exact moisture threshold crossing credit, chronological frost and real-vessel timed irrigation. Real item tests cover reservation, cancellation, last portion, already-wet plots and no unnecessary XP. Review found pre-sowing growth, frost order and legacy winter harvest bugs. All three were reproduced as failing GameTests before one fix pass; eight environment tests then passed.

Task 5: shared player/NPC environment, wind/enclosure separate from roof, preserved legacy Climate constructor, historical sleep weather and climate-conditioned pasture/product recovery with finite trough supply. Pure roof/wind and actual actor parity tests pass. Hoisted invariant water scans out of animal historical loops following review.

Task 6: protocol 4 local weather payload, actual codec round-trip, dimension/location/expiry checks, client-only rain intensity/precipitation hooks covering rain/snow, particles and sound; server regional surface snow/ice; climate HUD and controller observations. Dedicated client visibly rendered regional rain with matching HUD. Vulkan is needed on this machine; the initial OpenGL launch crashed in the known NVIDIA driver path. Five-second snapshot expiry is explicit, and wind does not bend rendered rain.

Final review: one fresh read-only reviewer (tectonic_review), three Important crop findings, two Minor observation/performance findings; all addressed. No additional Important findings in profile, runtime pins, terrain, pot conservation, actor parity or packet expiry.

Ruling: enlarge the old one-block GameTest arenas to empty 16×16×16 arenas with explicit sky access and padding — tests used 4–8-block work/supply areas and caused unrelated drop/navigation failures as registry order changed. Isolation retained assertions and removed fixture interference. Legacy and Tectonic full suites each passed all 113 tests after this adjustment. The first larger-arena run correctly exposed its default ceiling; sky_access=true preserves the outdoor habitat fixture.

Visual verification found the native rain hook still used skylight while bodies/soil used physical heightmap roofs. Added a wet-spell glass-roof regression, observed it fail, and changed the native hook to the same roof predicate. Final full reruns and replay are pending below.

Tooling: intermittent Windows socket-buffer/CLI response failures sometimes left Qube jobs running successfully in the daemon. Checked daemon jobs and collected their real completion results instead of treating CLI transport failure as a test failure or launching duplicate work. Authentication timeout interrupted one exploratory capture; retry succeeded.


Final: legacy 113/113 passed (22.0s), Tectonic 113/113 passed (30.4s), Qube build passed (6.8s), 38,688 pure assertions, 147 icon audits. Seven dedicated replay shots on two inspected sheets, all warning arrays empty. The exact returned pot has water=0, damage=1, count=1; soil water=0.4494463131. Extended-height stack accepts Y639/rejects Y640, samples terrain through Y517 and generated 399 cassiterite blocks at Y314?335. The first height probe omitted required min_y and normalized to 320; corrected both fields in a fresh profile. An intervening changed-config boot was deliberately rejected, proving the fingerprint guard.

Ruling: preserve this normal feature checkout and all uncommitted work, corresponding to the finishing skill's keep-branch choice already established in the approved execution constraints. No integration/merge menu, commits, pushes, worktree deletion or user-world cleanup is needed.

Tasks 1?7 complete within the approved milestone. Scientific tuning, guaranteed viable starts, large-population TPS benchmarking, autonomous settlement/trade planning and new technology trees remain explicit limits/future milestones. Validation details and proof sheets are in docs/validation/2026-11-08-tectonic-environment.md. Runtime ZIP assembly and final archive inspection follow this entry.

Final artifact inspection: 653 JSON files parse; runtime ZIP contains exactly Spawn, Tectonic and Lithostitched plus config/guide/hash manifest. Upstream jars omit separate license files; official Modrinth MIT declarations were verified through gstack browse and an explicit third-party notice is bundled. Final two-observer dedicated replay completed seven shots with client/server agreement, no desyncs or warnings. All implementation and review work is retained locally on the existing feature branch.

Final package verified: three exact jars, SHA-512-matched upstream artifacts, byte-identical built Spawn jar, CRC-clean ZIP, configuration, setup guide and third-party notices. Runtime size 1,701,797 bytes. All isolated QA servers and the task-owned warm client were stopped. No commits or pushes were made.
