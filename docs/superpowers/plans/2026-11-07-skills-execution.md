# Skills execution ledger

Base `db02d0d`, branch `realism-survival`. User explicitly requested learned
skills, quality jars from clay/kiln, a smithing workstation chain, looms, and
farm/forage/hunt support. No further approval gate; execute within that scope.
Unrelated `.gitignore`, untracked AGENTS.md/CLAUDE.md remain excluded.

Root owns body/SkillSet/SkillApi/SkillEvents, core tests, commands, HUD/network,
LaborGuard/CraftingGuard, documentation and Qube jobs. `living_world` owns
content/ and its assets/data/tests; `controller_world_actions` owns controllers/
and native crop practice in SeasonalCrops. `review_core` is read-only, reviewing
core now and waiting for final integration readiness. No simultaneous Qube jobs.

Implemented core: six independent XP values, level=floor(2sqrt(XP)), cap100 /
2500XP; invalid practice ignored. Body v5 retains v1-v4 and skills across
respawn. Packet channel revision2 prevents incompatible old snapshots. Skills
in health UI, `/survival skills`, query JSON, operator-only `debug_skill`.
LivingDropsEvent awards25 hunting XP for an actual animal kill with humanoid
source. New LivingEntity telemetry getters expose all six synchronized levels.

Observed checks:

- Pure learning tests failed on missing SkillSet/body fields, then **39 assertions
  passed**. `survival-v4.bin` was generated using the archived Nov6 v4 JAR,
  independently of current encoding; v1 fixture also retained.
- Core `skills_respawn` and `skills_native_hunt` first failed their intended
  assertions, then passed in the integrated red run.
- World-action tests first failed compilation on missing forage/hunt. Minimal
  facades enabled behavioral red. Full run: **8/74 failed,66 passed**. Expected
  failures: skill_forage_practice, skill_farming_parity, skill_hunt_validation,
  skill_hunt_outcome, practiced_clay_pot_quality,
  practiced_pottery_physical_capacity, practiced_smith_gate_conservation,
  practiced_loom_real_inputs. Both workers released for implementation.

Contracts: SkillSet.Skill {POTTERY,SMITHING,WEAVING,FARMING,FORAGING,HUNTING};
body.skills level/experience/practice/copyFrom/setLevel; SkillApi level/award/
workRate (.5+level/200)/effective (level*min manipulation,mental).
Production Recipe keeps its8arg constructor and adds station,skill,minimumSkill,
manual,heated,practice,minimumManipulation accessors. New station IDs bloomery,
anvil (Stone Anvil),loom; kiln/forge retained. LaborGuard now checks
WorkstationEntity.hasUsableHeat(), so unheated manual stations can be claimed.

Pending root work: `skills_no_grid_bypass` test added AFTER the74test snapshot;
CraftingGuard specialist-output guard intentionally absent until this test is
observed red. Then call Production.requiresStation(result) in tick/mayTake to
deny alternate grid recipes (worker implements helper). Need final actual-skill
gate test on correct anvil/blank, not merely old forge-route rejection; worker
notified. Native farm practice belongs to SeasonalCrops and skips controller
completion; hunting kill XP belongs solely to root event hook. World worker
asked to test actual NoAI=false NPC pursuit, not just synthetic job ticks.

## Integrated checks and fix pass

Both implementations landed, including all16wool colors, new native metal gear
routes (shield/crossbow/common utilities),7initial field-action regressions and
11content regressions. Corrected three26.3 API assumptions (ChunkPos accessors,
three-argument swing, no DataComponents.SWING_ANIMATION).

Core targeted red:4/6 failed exactly grid bypass, jar action success, required
station selection, virtual merchant minting. Root fixed all: CraftingGuard
specialist result policy, MerchantMenu shift guard, hidden virtual offers,
quality-independent vessel result prefix, matching loaded station selection,
fuel/collect/cancel selection and shared cancellation result prefix. Initial
full86run passed85, with a null connection in the mock-player overlay notice;
notice now skips headless players safely. All11crafting and7field tests passed,
including real NPC pursuit, both hands, and cancellation.

Reviewer core found no important flaw; small UI progress gap fixed with Next XP.
Final integration review identified3important gaps: instant native berry use,
instant left patch/fruit breaking, and reloads removing binding/forge stages.
Workers added regressions FIRST. Full91red: expected5new regressions failed
(3native,2reload), plus known shared-definition full-metal fixture race and
two old-fixture interference failures (controller_vessels lid count and timed
consumption). Other83passed, including all6core.

Content worker released fixes: both reload invariants, shared-definition fixture
isolation, and vessel fixture counts only newly returned lid UUIDs. Root moved
the timed-food actor above adjacent test inventories and added diagnostic values;
focused timed_consumption run is in progress. World worker is fixing native
berry/left gathering/fruit dispatch through actual timed jobs, no duplicateXP.
No production behavior was weakened to accommodate tests.

Validation scripts prepared: `validation/skills-workshop.json` (actual novice/
expert clay pottery, black wool spinning, forge blank, skill UI and network
getters; explicit tick sprint accelerates real loaded work), and
`skills-fieldwork.json` (actual NPC pursuit/kill). Not run yet. Existing Qube
singleplayer launch previously stalled; prefer fresh dedicated stage + replay.
Newroot skillpacket channel2 requires currentclient/serverbuilds.

Final source candidate: **91/91 GameTests passed in17.6s**, including all3native
review fixes, both reload-invariant fixes, all6core new tests and13practiced
content tests. The isolated food test also passed; the full suite confirms the
fixture fixes. Build succeeded with **39pure assertions**; JAR460275bytes.
All Java/resources frozen.

Dedicated workshop exploration succeeds: clay produces rough/fine jars according
to actual workers; blackwool produces4yarn; forge produces copper pickaxe blank;
novice bot learns pottery10/foraging4, weaver learns6, getter values match client.
All printed sheets opened; healthscreen inspected at fullsize with6skills+nextXP.
Exploration water steps were removed from the final skill-only scene after an
unreliable assumed hotbar slot; water is covered by the actual vessel regressions.
Current recorded workshop segment is the clean simplified version with no failed
vessel commands. Fieldwork (real hunter+secondrealclientforage) capture is running.

Next: open fieldwork sheets, proof replay both scripts and inspect every sheet,
verify actual earned skills across reconnect/restart as appropriate, stop server,
update exact verification/README/checklists and commit only task files. Branch
realism-survival, base db02d0d; unrelated workspacefiles remain excluded.

## Final closure

This section supersedes pending-work notes above. All workers are finished;
the independent review has no important open finding. The final dedicated
replay completed all three segments: seven proof captures on four sheets,
including the ten-frame real hunting burst. Every sheet was opened. The
hunter approaches, strikes, kills, drops beef and earns 25 XP; the real player
earns 4 foraging XP (100 to 104). Client/server skill values agree, with no desyncs.
The final repeated skill screenshot warning is expected. Frozen waits settle
UI or accompany explicit tick stepping/sprinting and are not claimed labor.

One real client plus a headless player and NPCs were validated. The proposed
second real client was unavailable because of host RAM, so the final field
scene uses the main real client. Actual --fresh reconnect retained the same
UUID and all six skill records exactly, including a foraging total of 104 XP.
No new full server-restart check is claimed for this extension. Server stopped.
Java/resources remain the build that passed 39 pure assertions and 91 GameTests.
Exact evidence and limitations are in 2026-11-07-skills-validation.md; README
now documents skills, station chains, actions and current validation commands.
