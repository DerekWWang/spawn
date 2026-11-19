# Practiced-skills review

## Initial core review

Reviewed the approved 2026-11-07 design/plan and the actual working tree against `db02d0d`, including untracked SkillSet, SkillApi and SkillEvents. Scope was the root-owned learning model, v5 migration/clone behavior, commands, HUD, network/view adapters, and related pure/core test sources. Content and WorldActions integration is intentionally deferred until the implementation-ready notification. No Java edits, builds, or tests were performed.

**No critical or important correctness finding established in this core scope.**

- Skill experience is independent per profession, bounded to 2500, and uses the approved level formula. Invalid practice amounts cannot subtract or create progress. Learned levels remain separate from physical capacity; the effective-score adapter applies current physical/mental capacity without mutating learning.
- Version 5 appends the six experience values after the v4 payload. Older versions leave novice skills while preserving their existing fields; malformed skill payloads fail closed. The archived v4 fixture and round-trip assertions exercise the intended transition. The added 48 bytes remain within the existing 8192-byte snapshot limit.
- Ordinary death cloning copies skills into the new body's state, preserving injury reset and the existing keep-inventory clothing path. Non-death cloning retains the whole body. No newly introduced stale-state overwrite was found in these core handlers.
- Normal skill awards require a living, nonexempt server humanoid. The only level-setting command is operator-gated. Network registration is server-to-client only and changes its protocol version; client view getters only inspect received state. Hunting practice has one core outcome hook, at real animal drops; completion adapters must not award the same hunting outcome again.

### Minor acceptance gap

`src/main/java/dev/qube/spawn_9b5fbb9/client/SurvivalHud.java:78-84`: the health screen shows levels and a level/100 bar, but no progress to the next level. Two bodies at the same level with different XP render identically. The approved visibility section asks for next-level progress through health inspection as well as commands/JSON/planning. Commands and JSON already expose it; show `untilNext` or a within-level progress bar in the health screen too. This does not affect progression accounting.

**Closed on source reinspection:** the health screen now renders `Next: N.N XP` from `untilNext`, or `Mastered` at level 100. Root owns visual replay verification.

## Integration review

Reviewed the frozen integration after root's implementation-ready notification: Production/WorkstationEntity, graded jars/aging, controller and native action paths, specialist grid/trade guards, planner/cancellation adapters, relevant recipes, and regression sources. No build/test job was started. Root's full suite was still in progress when this review was recorded.

### P2 — Native berry right-click bypasses learned work timing

`agriculture/SeasonalCrops.java:88-93,160-166`

For an eligible mature sweet-berry bush or cave vine, the native right-click path allows vanilla harvesting immediately and queues the practice award for the next entity tick. It does not create a manual job, spend work hunger, or apply the skill-dependent work rate. The equivalent controller/left-click harvest uses WorldActions' timed harvest. Reproduce with two eligible bushes: right-click one once and start `/survival harvest` on the other; the first yields instantly and earns practice while the second waits for loaded work. Route ordinary berry harvesting through the same timed completion path; only completion should reset the biological harvest record and award practice.

### P2 — Native patch breaking bypasses forage time and loses practice

`content/GroundResourceBlock.java:22-27`; `content/SurvivalContent.java:42-43`; `agriculture/SeasonalCrops.java:149-151`; `loot_table/blocks/{loose_stones,wild_plants}.json`

GroundResourceBlock redirects right-click gathering only. Both patches remain `instabreak`, and their break loot tables grant loose stone/plant fiber. The native left-click interceptor only handles scheduled crops/fruit, so a survival player can instantly break a patch and obtain real supplies without a forage job or FORAGING practice, including while an unrelated manual job is active. Ordinary gathering and controller gathering therefore use different skill/time rules. Redirect native patch breaking to the validated gather action, or otherwise enforce the same work/award boundary before useful yield.

### P2 — Supported recipe reloads can erase required smithing stages/materials

`content/Production.java:25-37,95-103`

The reload validator enforces the final anvil/skill/matching-blank gate, but it does not enforce required secondary supplies or heated manual blank formation. For example, `{"copper_pickaxe":{"second":""}}` is accepted and removes its binding requirement. Likewise an override of `forge_copper_pickaxe_blank` with `station:"kiln", forge:false, manual:false, heated:false, skill:"none", practice:0` is accepted and turns the forge stage into passive, unfueled kiln work. These are valid supported overrides, not malformed NBT or privileged direct state mutation. The approved plan specifically requires meaningful station/skill validation to survive reloads, and the design requires heated forming plus actual binding/riveting supplies. Preserve these structural invariants while allowing numeric tuning.

### Confirmed integration properties

- Station jobs persist exact primary/secondary stacks, output plan, skill/station/manual/heat requirements, progress, owner, quality score, and award flag. Normal cancellation returns both reserved item stacks and produces no practice. Completion clears reservations before the one-time award, and hunger is saved before the skill adapter reloads the body.
- Normal manual advancement rechecks current skill, manipulation/mental capacity, worker identity/range, and labor claims; loaded advancement is capped. Quality uses the observed effective pottery score; rough/standard/fine capacity and default preservation rates implement 2/3/4 slots/portions and 2/4/6 times preservation. Unmarked jars retain fine behavior.
- Specialist grid outputs are denied at pickup/shift-move and controller craft start/completion. Merchant specialist output is denied at pickup/shift-move and removed from generated offers. The current explicit shield/crossbow routes require actual blanks and anvil smithing, with crossbow stock supplied through the loom.
- Hunting rechecks the real quarry, weapon identity/damage, range, line of sight, current capacity, and loaded pursuit bounds. It uses actual damage/durability/loot, saves hunger before damage hooks, and relies on the sole kill-practice event rather than awarding XP again at job completion. No concrete duplicate hunting or station XP path was established.
- Planner entries expose station, minimum skill/manipulation, manual/heat requirements and practice; API cancellation falls back to nearby owned manual work. Early kiln/bloomery/anvil/loom recipes and novice useful metalwork avoid an iron/skill bootstrap cycle.

The three findings above are source-established issues; no independent runtime result is claimed. The previously closed health-screen progress item remains closed.

## Implementation closure and executed regression evidence

Root's combined run passed **91/91 GameTests in17.6s** after a fix pass with
observed failing tests first. All three findings are addressed:

- Berry right-click now starts the same timed hand-aware harvest job; the
  deferred instant-yield XP path was removed. `skill_native_berry_timing`
  failed before this change and now passes without weakening its assertions.
- Left-click patch and fruit interactions now enter timed gather/harvest jobs;
  explicit sneak-left dismantling is also timed. `skill_native_left_gather`
  and `skill_native_fruit_paths` failed before the fix and now pass.
- Reload validation retains required bindings/rivets and heated manual forge
  formation of known equipment blanks. Both practiced_reload_* invariant
  regressions failed before this validation and now pass; numeric time/XP
  tuning remains accepted.

The final suite includes actual NPC pursuit, native offhand planting, one-time
hunting XP/loot/wear, complete metalworking, graded jars, loom inputs,
reservation cancellation, grid/merchant extraction, and legacy body tests.
This closure reports executed root verification, not another independent
reviewer test run. No important reviewed finding remains open.
