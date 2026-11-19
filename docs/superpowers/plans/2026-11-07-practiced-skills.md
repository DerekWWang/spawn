# Practiced Skills Implementation Plan

> **For agentic workers:** Use test-driven implementation and independent review. Steps use checkbox syntax.

**Goal:** Make survival craftsmanship and food acquisition develop learned skills equally for players and controlled humanoids.

**Architecture:** A pure SkillSet is part of the persistent body. Shared station and world-action adapters validate skill/capacity and grant practice at actual completion. Clients and planners inspect the same server-owned state.

**Tech Stack:** Minecraft 26.3, NeoForge 26.3.0.77, Java 25, Qube.

**Spec:** `docs/superpowers/specs/2026-11-07-practiced-skills-design.md`

## Constraints and review focus

- Preserve existing survival rules and the unrelated `.gitignore`, AGENTS.md, CLAUDE.md files.
- No free experience, duplicate completion, station/grid bypass, or iron prerequisite for early stations.
- Old saves migrate; skills survive ordinary respawn while anatomy resets.
- Recheck changed capacities, distance, held tools, target permissions, and supplies during work.
- Datapack reload must preserve meaningful station/skill validation and unfinished-item conservation.

## Interfaces and ownership

Root owns `survival/SkillSet.java`, `SkillApi.java`, `SkillEvents.java`, body persistence,
inspection/commands/HUD, pure tests, documentation, and final integration.
`SkillSet.Skill` contains POTTERY, SMITHING, WEAVING, FARMING, FORAGING, HUNTING.
`SkillSet.level(Skill)`, `experience(Skill)`, `practice(Skill,double)`, `copyFrom(SkillSet)`;
`SkillApi.level(LivingEntity,Skill)`, `award(LivingEntity,Skill,double)`,
`workRate(LivingEntity,Skill)` and `effective(LivingEntity,Skill)` are shared adapters.
Body field: `public final SkillSet skills`.

Content worker owns content/workstation/pottery/aging resources and corresponding
GameTests. Production recipe requirements must expose station, skill/minimum,
manual work, and fuel information to the world-action planner; coordinate exact
accessors before touching shared consumers.

World-action worker owns controllers and their GameTests, plus crop action
practice hooks in `SeasonalCrops` if needed. Root owns native hunting death
practice in SkillEvents; communicate how to avoid duplicate awards.

### Task 1: Persistent learned skills

- [x] Add failing pure tests for independent practice, diminishing progression, bounded invalid input, migration and save round-trip.
- [x] Observe red, implement SkillSet and body v5 migration, run pure checks green.
- [x] Implement server skill adapters, death retention, operator diagnostics, JSON/command/HUD inspection.

### Task 2: Skilled production

- [x] Add red GameTests for novice/advanced smith gating without consumed rejected inputs, clay-block kiln quality, physical incapacity, real loom costs, completion-only practice, and old pot behavior.
- [x] Register bootstrap-safe bloomery/anvil/loom blocks, bind models/items/loot/recipes, and implement chained metal blanks/armor and loom recipes.
- [x] Apply station/skill rules during start/progress/finish; preserve fuel, elapsed work, reservations, quality, and worker identity across saves.
- [x] Remove grid bypasses, update tuning definitions, and run targeted content checks green.

### Task 3: Skilled food acquisition

- [x] Add red real-entity tests for forage/farm practice and timing, hunting range/weapon/capacity, damage/loot/wear, serial labor, and rejected/cancelled actions awarding nothing.
- [x] Implement shared hunt/forage commands/API and NPC pursuit with real costs; integrate native farming/foraging/hunting learning.
- [x] Extend bounded planning output with skill/station constraints and run controller checks green.

### Task 4: Integration and delivery

- [x] Run full Qube build and GameTest suite; fix regressions and review bypass/conservation risks independently.
- [x] Run dedicated new-workshop and skill-screen replay; open every contact sheet.
- [x] Document progression/requirements/API, record exact verification and limitations, commit only task files.

## Execution rulings

The user explicitly requested this implementation as an extension of the approved
survival simulation. Routine numerical tuning and station choices follow the
spec above within that authorization; implementation continues without a new
permission round. Shared API contracts allow content and world-action work in
separate files, with Qube compilation/test jobs coordinated by root.
