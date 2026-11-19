# Realism implementation review

A fresh read-only reviewer examined the complete change against the approved
specification. No critical findings. Five important findings and four smaller
issues were accepted and resolved in one implementation pass.

| Finding | Resolution and evidence |
| --- | --- |
| Flax/cordage/loom bootstrap loop | Added a real hand-processing recipe; natural flax ? fiber ? cordage test failed before and passes after. |
| Kiln native mining interception | Block entities keep ordinary mining; empty kiln interaction regression passes. |
| Native/controller crafting rates differ | Shared physiological/skill rate; level 0 and 20 parity regression passes. Planner estimates use the same rate. |
| Neighbor tree generation order changes habitat | Stable biome habitat classification, own-chunk wood reads and tree-first decoration; actual adapter tested in opposite chunk orders with seed 1. |
| Repeated placed-stone extraction and reversible assembly XP | Persisted per-section world exclusions cover placements and extraction. Reload/placement regressions pass. Reversible mineral compression grants no construction XP. |
| Legacy tin tool tag | Restored the existing tin ore entry. |
| Misleading recipe practice query | Schema 6 planning exposes learningWeight and baseExperiencePerActiveTick. |
| Missing terrain exposure check | Heightmap exposure rejects solid roofs before lighting propagation; regression passes. |
| Last feed item/full animal practice | Capture feeding before consumption and require useful nutrition/breeding change; full-animal regression passes. |

The seven review regressions all failed on the prior behavior, then all passed.
The complete final integration suite passes 105 tests (104 mod tests plus the
harness test), including physical two-actor mineral extraction, transfer and
processing. The collaboration fixture initially attempted repeated workstation
updates in one tick; it now uses real scheduled ticks with a shortened recipe.

Scientific boundaries outside the approved model (full tectonics, crop
domestication history and additional vanilla mineral families) remain documented
approximations. Backstories, profession locks, currency, and autonomous trading
were explicitly excluded. External protection-mod interoperability and cross-file
crash atomicity were not demonstrated and are not claimed by this review.


Applied-terrain follow-up: the actual survey exposed that Minecraft 26.3's dirt
tag excludes grass blocks. An exposed-grassland regression failed, explicit
grass support was added to habitat and plant survival, and the full 105-test
suite passed again. Regenerated surveys now include plants and woodland sticks
rather than only rock debris. This was an implementation compatibility fix,
not a scope expansion or another review round.
