# Skills, ecology, geology and item validation

Implemented the approved realism design on `realism-survival`. Backstories,
profession locks, currency and autonomous trading were excluded as agreed.

## Delivered behavior

- Eleven named, independently saved skills at levels 0-20. Useful completed
  work supplies active-time XP; passions affect learning, daily net learning has
  a soft cap, and advanced skills decay with a level-loss buffer.
- Schema-6 state and protocol-3 snapshots. Original schema-5 saves migrate all
  six existing skills while retaining needs, injuries and clothing. The
  checked-in 818-byte fixture was captured with the original encoder.
- A dedicated skills screen, native/controller work parity, scaled equipment
  gates and quality, and observational resource/prospecting APIs.
- Sparse stone debris, woodland sticks, species-specific finite plant stands,
  habitat checks, small apple groves, quarry rock bodies, regional mineral
  envelopes, grade variation and intervening barren layers. Placed stone and
  previously extracted positions cannot renew mining practice.
- Malachite, cassiterite, hematite, limestone, flax, yarrow and blackberry
  identities enter real existing processing chains. Hand processing lets flax
  reach cordage before a loom exists.
- 147 localized item definitions with distinct 16x16 icon bindings, including
  144 carried items and three crop render aliases. Flat tool silhouettes remain.
  Wikipedia references and intentional generic identities are recorded in the
  [item audit](../specs/2026-11-07-item-reference-audit.csv).

## Automated checks

| Check | Result |
| --- | --- |
| `qube build` | Final build passed in 4.8 seconds; `build/libs/spawn_9b5fbb9-1.0.0.jar`. |
| `qube test` | All 105 GameTests passed: 104 mod tests plus the harness test. |
| `tools/test-simulation.ps1` | 2,325 behavior assertions passed. |
| `tools/audit-item-assets.ps1 -IncludeBlocks` | All 147 definitions passed dimensions, localization, distinct model/texture binding and transparency checks. |
| Independent review | Five important and four smaller findings resolved; see [review adjudication](2026-11-07-realism-review.md). |

Regression checks cover actual mining exposure, cancellation, reload, passion
queries, hidden-resource exclusion, old vein removal, native/controller parity,
finite species-specific drops, two-actor mineral transfer and processing,
placement provenance, last-item feeding, roofed habitats and neighboring
decoration order. Seven review regressions failed before their fixes and then
passed. A further grassland regression failed when applied terrain exposed
Minecraft 26.3's grass-block exclusion from the dirt tag; both placement and
plant survival now explicitly support grass blocks.

The two-actor test extracts one real mineral sample, transfers that exact stack
to another actor, consumes it in an actual station, and collects one processed
output. Its shortened test recipe uses real scheduled ticks and preserves the
material and station contracts. Existing complete metalworking and first-day
tests cover equipment, pottery, safe water, hoe construction and sowing; the new
flax test covers the changed natural-source-to-cordage bootstrap. This is
compositional progression coverage, not a claim of a full unassisted day-long
survival playthrough.

## Applied terrain

Three isolated normal worlds used seeds 42, 918273 and 20261107, verified by
RCON before sampling. Each saved 406 fully generated chunks around representative
productive and barren districts, at positive and negative coordinates: 1,218
chunks in total. Existing user worlds were retained. Only disposable survey
worlds were regenerated after the grass-block fix.

| Seed | Plant blocks | Unsupported plants | Loose stones | Malachite | Cassiterite | Hematite | Coal |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 42 | 95 | 0 | 190 | 796 | 123 | 33 | 6 |
| 918273 | 83 | 0 | 61 | 381 | 437 | 591 | 462 |
| 20261107 | 14 | 0 | 13 | 1,106 | 508 | 345 | 0 |

Plant coverage is approximately 0.091%, 0.080% and 0.013% of sampled columns.
Ocean-heavy terrain contributes to the lower third-seed coverage. Replaced
vanilla copper/iron ores, raw-metal density-vein blocks and legacy scattered tin
were absent. Resource bounds, quarry material counts and biome occurrences are
in the [saved-world survey](2026-11-07-resource-world-survey.json).

The pure survey sampled 4,096 districts per seed. Barren counts were 801, 843
and 847, with distinct copper/iron/tin/coal distributions. Productive field
envelopes include thousands of host positions with approximately 15-30% mineral
grade before actual terrain and caves clip them. See the
[field survey](2026-11-07-resource-fields-survey.json).
An actual surface-feature adapter also produced identical blocks in reversed
neighboring chunk decoration orders with seed 1.

## Visual and reconnect evidence

The dedicated flat-stage replay produced 11 proof captures on five contact
sheets, all opened. Player and NPC skill telemetry agreed with their client
copies, with zero desyncs. All 144 carried icons were rendered across three
panels from two angles. Every icon is visible, without missing-texture tiles;
tools, unfinished blanks, mineral fragments, fibers, seeds, food and garments
retain distinct silhouettes and palettes.

- [Skills](../evidence/2026-11-07-realism/stage-proof-0.png)
- [Resource stands and quarry materials](../evidence/2026-11-07-realism/stage-proof-1.png)
- [Complete carried-item atlas](../evidence/2026-11-07-realism/stage-proof-2.png)
- [Earned learning](../evidence/2026-11-07-realism/stage-proof-3.png)
- [Retained learning](../evidence/2026-11-07-realism/stage-proof-4.png)

A real client restart preserved every field of all eleven skill records.
Foraging retained 20 earned XP, 20 daily net XP and its interested passion;
mining retained its burning passion and advanced decay bookkeeping. The
[before](2026-11-07-realism-practice-evidence.json) and
[after](2026-11-07-realism-reconnect-evidence.json) query records compare equal.
The [full stage replay](2026-11-07-realism-stage-proof.json) also records repeated
state inspection. Its identical-screen warning on the final capture is expected
for persistence verification. UI-only waits deliberately advance no world time.

Natural-world captures use a separate stage archive copied from the original
seed-42 survey world. The operator cutaway exposes an existing deposit for
inspection; setup vegetation drops are cleared before mining, and the mineral
body itself is generated. The NPC uses real timed
mining with a real tool and gains practice. Water can flow into an exhausted
cell, so the depletion assertion requires absence of the mineral, rather than
an air block. The original survey world and its measured counts are retained.

The natural-world replay produced four further proof captures, all opened:
[plant stands and the quarry cutaway](../evidence/2026-11-07-realism/natural-terrain-proof.png).
The [recorded proof](2026-11-07-realism-natural-proof.json) confirms depletion
and actual mining practice. Together the two replays contain 15 proof captures.

The test client initially crashed in NVIDIA's OpenGL driver. The task-owned QA
client uses Minecraft's supported Vulkan backend; dedicated capture works.
No global driver or system settings were changed.

## Boundaries

New generation applies to fresh chunks. Old terrain, builds and identifiers are
retained; there is no destructive world rewrite. Different districts provide
material incentives for collaboration and transport, rather than a complete
autonomous economic simulation.

Geology uses finite regional host envelopes and broad geological associations,
not a tectonic model. Climate uses biome temperature, substrate, water and
terrain exposure rather than full plant physiology. Cultivated crop relatives
in wild stands remain an intentional historical/domestication abstraction.
Unspecified legacy fibers and treatment classes keep honest generic names.
