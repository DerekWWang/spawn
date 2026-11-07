# Survival validation — 2026-11-06

Final build: `build/libs/spawn_9b5fbb9-1.0.0.jar`, Minecraft 26.3,
NeoForge 26.3.0.77. `qube build` succeeded and ran **31 pure behavioral
assertions**. The final full GameTest run passed **62/62** in 21.2 seconds
(61 mod tests and `minecraft:always_pass`). The later client-only shortcut
correction uses `InputConstants.KEY_H`; a fresh second client's saved binding
is `key.keyboard.h`.

## Scope of automated checks

Tests cover hunger/thirst/capacities, conditions and archived-state migration;
actual supplied food/treatment/clothing and NPC care; forbidden entities/items;
finite vessels and boiling; processing, fuel, wear, freshness and preservation;
crop timing/frost; gestation, maturity, milk/wool/eggs and finite trough catch-up;
validated definitions; controller permissions, resource reservations, tools,
native drops, planting, planning pages, and no offline/manual-labor duplication.
Review reports record test-first failures and their fixes.

## Saved-world checks

A fresh normal dedicated world was generated with `generate-structures=true`
and `level-type=minecraft:normal`. The read-only `tools/audit-world.py` parsed
2,550 saved Overworld chunks: 339 full, with the remainder at intermediate
generation stages. All structure-start maps and reference maps were empty.
Palettes contained natural clay, copper/iron/coal ores, obsidian, water, and new
tin ore; a forest-region sample also contained loose stones, wild plants, and
fruit trees. Counts are palette occurrences, not block totals. The raw report
is `run/qube/world-audit.json`.

A real villager was injured and clothed, given hunger 43/thirst 57, saved,
and inspected after clean server stop/restart. UUID, anatomy (left arm 69),
bleeding, and wool jacket matched exactly. Hunger/thirst advanced by 360
simulation ticks, within the 20-tick cadence of 363 elapsed calendar ticks.
The normal-world files were retained.

The actual `Dev` client disconnected and restarted against the stage server.
Its UUID, injured anatomy, clothing, and hypothermia persisted; needs were not
refilled. Server/client capacity telemetry agreed after rejoining. Raw before/
after inspections are `run/qube/{persistence,player}-{before,after}.json`.

## Visual multiplayer proof

Recorded segments live in `validation/`. After exploration, a fresh dedicated
stage ran `qube shot --dedicated --fresh --replay`: **nine proof images on
three contact sheets**, all opened and inspected. No per-shot warnings,
missing textures, missing subjects, or desyncs were reported.

| Proof | Observed result |
| --- | --- |
| Segment 0, cameras A/B at T+40 | Workshop stations and crop row visible from two distinct angles; bot has jacket, pants, and shoes. |
| Segment 0, T+60 | Five-state HUD and season visible with low hunger/thirst. Villager and bot state getters agree across the network. |
| Segment 1, T+20 | Health screen shows left arm 40%, separate digits/senses, bare clothing slots, bleeding/hypothermia, and reduced capacities. |
| Segment 1, T+40 | Nearby bot consumes supplied bandage; bleeding clears while hypothermia remains. Iron chestplate covers the jacket with pants/shoes still visible. |
| Segment 2, second real client at T+20 | Own HUD shows hunger about 40/thirst 20. Own health screen shows healthy anatomy, bare clothing, and 30% mental capacity; values agree with the server. |

Proof sheets: `run/qube/shots/proof/segment-{0,1,2}.sheet.png`. The native
unsigned-chat toast appears in some captures. Intentional `wait: 10` steps
settle client screens while the world remains frozen; they are not claimed as
biological progress. Timed actions use `step`. Earlier singleplayer capture
startup stalled in Qube; dedicated startup/capture/replay completed successfully.

## Progression audit and practical limits

Wild patches provide stones/sticks/fiber/food/seeds. Knapped edges/cordage
bootstrap a stone pick. Stones/clay bootstrap a kiln and lit campfire; three
clay balls form a pot blank, then 2,000 heat ticks fire it and 1,000 loaded
ticks boil its water. Fiber cloth bootstraps clothing. Fired bricks/stones
bootstrap a forge without an anvil or metal. Copper/tin need crushing,
roasting, and smelting; bronze and copper tools reach iron blooms, which need
manual forging. No prohibited ingredient or material cycle was found. Serial
processing exceeds one day; parallel stations and collaboration shorten it.

This verifies the recipe graph, natural generation, and focused real actions;
it is not a complete multi-day human playthrough or long-run balance study.
Biological durations, climate, disease, and unloaded history are deliberately
compressed gameplay models described in the design and module reports.
Scripts use the shared server action layer; autonomous human-level planning
is the external controller's role. Additional mods can extend the data tags.
