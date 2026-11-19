# Execution record — realism survival

Plan: `docs/superpowers/plans/2026-11-06-realism-survival.md`

## Final status — supersedes the historical recovery notes below

Implemented persistent shared physiology/anatomy/conditions, supplied controller
actions and planning, capacity-based manual work, HUD/health/clothing rendering,
natural-world restrictions/resources, timed production/pottery/decay, seasonal
crops, delayed husbandry, and validated datapack tuning. Schema v4 decodes
v1/v2/v3 and is checked against an archived v1 fixture. All core/integration
review findings and the final four scoped findings were addressed.

Final verification: build passed with 31 pure assertions; 62/62 GameTests passed
(61 mod tests plus harness). Actual server restart and real-player reconnect
retained state. Normal terrain audit: 2,550 saved chunks, 339 full, no structure
starts/references; natural and new resource patches present. Fresh dedicated
replay: nine proof images, all three contact sheets opened, no reported desyncs
or missing textures, two real clients plus bot/villager. Resource graph has no
bootstrap cycle; a complete multi-day human balance playthrough was not run.
See `validation-report.md` for precise evidence and limits.

Qube singleplayer capture startup stalled; dedicated capture/replay succeeded.
Frozen-world `wait` warnings are intentional client-screen settling waits;
biological progress uses explicit tick steps. Existing `.gitignore`, AGENTS.md,
and CLAUDE.md work is excluded from the implementation commit.

## Historical recovery notes

Branch: `realism-survival`, baseline `e80064e`. Existing unrelated changes to
`.gitignore`, untracked `AGENTS.md`, and `CLAUDE.md` belong to the user.

User approved the spec and explicitly said to execute. Players, fake players,
bots, and script-enrolled humanoids share physiology and validated actions.
No further approval is needed for routine implementation.

## Implemented so far

- Pure `SurvivalState` simulation, binary persistence (current v3, v1/v2 migration),
  activity hunger, thirst/death, temperature/garments, recreation quota, anatomy,
  bleeding, poisoning, mild/deadly infection and immunity, three capacities.
- Server entity hooks, hunger integration replacing vanilla FoodData tick,
  injuries, food finish/cake intake, NPC food/water/company seeking, capacity
  modifiers, mental-incapacity AI/work shutdown, clothing/treatment interactions.
- Controller API, tag-driven humanoids and script enrollment; action inspection,
  timed eating, feeding/clothing companions, social sessions, medicine, dyed
  garments; player and operator commands; nearby workstation operation API.
- Bounded state payloads, channel negotiation guard for headless test/bot players,
  server season length synchronization, five-state HUD, health/clothing screen,
  tracked entity state, separate player clothing render layer covered by armor.
- Living-world worker owns `content/`, `world/`, `agriculture/` and corresponding
  resources. Its broad mechanics/report are in `living-world-report.md`.

## Verification already observed

- Baseline `qube build` passed before implementation.
- Initial world GameTest failed as expected when vanilla admitted a zombie.
- Pure simulation initially failed starvation-speed assertion, then passed.
- Additional configured-quota regression failed before its fix.
- `powershell -NoProfile -ExecutionPolicy Bypass -File tools/test-simulation.ps1`
  currently passes 28 behavioral assertions.
- Integrated `qube build` passed after 26.3 API corrections and Gradle JavaExec
  check wiring. Later source changes still need a fresh final build.
- Frozen integrated `qube test` passed all 12 registered GameTests. Further
  vessel/preservation refinements and root UI/controller changes require rerun.
- Six Qube-generated textures inspected by living-world worker, each reporting
  `bindings_missing: []`. No final game screenshot has been taken yet.

## Review and fixes

Read-only `review_core` agent reviewed initial core and wrote
`core-review-report.md`. All eleven concrete findings have been addressed in
source: clothing wear/death handling, timed eating, cake, vertical climbing,
config quota, mild disease onset, water path parity, offhand supplies, captured
sleep state/rate, and server/client season configuration. Sleep's one-tick
undercount was also fixed. Further review should verify those changes and the
remaining integration gaps, not repeat the original review as if unaddressed.

## Active work / next actions

- `living_world` worker is extending Pottery to shared LivingEntity vessel
  actions, lit-fire/open-pot rules, finite lids, spill-on-break, wider fantasy
  filtering, heat weathering, crop rebearing/harvest gating, and tests. It owns
  builds/tests while it says a Qube job is active; coordinate before running.
- Root must wire `/survival vessel <action>` and operator control vessel once
  `Pottery.action(LivingEntity,String):String` is available.
- Root must finish NPC clothing visuals, health screen responsiveness/body
  diagram/shortcut, and garment dye/wear display as needed.
- Add actual integration regressions for food duration, offhand treatment,
  garment wear/dye handoff/death/keep-inventory, controller parity, and remote
  state; expand crop/husbandry/structure tests as needed.
- Run a fresh `qube build`, full `qube test`, dedicated server check, normal-world
  structure/resource probes, visual stage segment plus `--replay` and dedicated
  replay. MUST open each printed contact-sheet image before visual claims.
- Read current living-world report's limitations and close material acceptance
  gaps. Do not claim full spec completion while required behavior is missing.
- Request final independent review (`review_core` can receive follow-up), fix
  material issues, update README and verification record, commit only task files.

## Runtime notes

- Qube commands require `sandbox_permissions=require_escalated`, prefix `qube`,
  because its auth/runtime files live outside the writable root.
- Minecraft26.3 uses `EntityTypes`, `Player.sendOverlayMessage`,
  `Minecraft.setScreenAndShow`, `GuiGraphicsExtractor.text/fill`,
  `Player.drop(stack,false,Prediction.SERVER_ONLY)`,
  `Entity.setInvulnerableTime`, and `DataComponents.DYE`.
- PlayerModel import is `net.minecraft.client.model.player.PlayerModel`.
- Compiled FML has `ModConfig.Type.LOCAL` (starter used it); SERVER does not
  compile despite the bundled older FML source mentioning it.
- Survival mixins MUST remain under isolated `survival.mixin`, not the entire
  `survival` package. World mixins live under `world.mixin`.
- Qube build cannot run with its dedicated server locking files; stop it or use
  `qube build --restart`. API lookups are safe concurrently.
