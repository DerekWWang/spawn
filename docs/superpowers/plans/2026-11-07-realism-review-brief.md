# Fresh whole-change review brief

Review the current uncommitted implementation against HEAD `e2b07d9`.
The user reviewed and approved both the spec and plan and requested execution.
All implementation was performed natively; you are the single independent
whole-change reviewer required by the executing-plans skill.

Read the reviewer template at
`C:/Users/derek/.codex/skills/requesting-code-review/code-reviewer.md`.
Do not spawn subagents. Do not change files, index, HEAD, branch, or processes.
Do not run Qube build/test/shot: the executor owns its active validation jobs.
Inspect uncommitted tracked AND new Java, JSON, and tool sources; a plain git
diff omits new files. Ignore unrelated pre-existing .gitignore/AGENTS.md/CLAUDE.md.

Spec: `docs/superpowers/specs/2026-11-07-skills-ecology-geology-design.md`.
Plan: `docs/superpowers/plans/2026-11-07-skills-ecology-geology.md`.
Execution evidence and compatibility decisions:
`docs/superpowers/plans/2026-11-07-skills-ecology-geology-execution.md`.

Core code: survival/SkillSet.java, SurvivalState.java, SkillApi.java;
controllers/WorldActions.java and ResourceActions.java; content/Production.java,
WorkstationEntity.java, GroundResourceBlock.java, SurvivalContent.java;
agriculture/SeasonalCrops.java and Husbandry.java;
world/ResourceFields.java, NaturalResourceGeneration.java, and OreVeinRuleMixin;
client/SkillsScreen.java and SurvivalHud.java; network/SurvivalNetwork.java.

The old schema-5 fixture was captured before changing the encoder. There are
2,325 passing pure assertions, 96 passing GameTests before the last observable
resource test, and the visible-resource GameTest subsequently passed. A final
full run and dedicated visual replay are underway. The asset audit passes all
147 item definitions, with Qube-created 16x16 icons; three crop render-only
definitions are not registered gameplay items. All atlas images were viewed.
New-world geography is an explicit approximation; old terrain is preserved.
Backstories, arbitrary profession locks, currency, and an autonomous economy
are explicitly excluded. Documentation and final evidence collection are still
being completed; distinguish that ongoing work from defects in the code.

## Required review focus (from the plan)

1. Schema-5 experts and fractional XP must retain their relative competence, while invalid new records must never partially replace a valid body.
2. Jobs spanning midnight, cancellation, reconnect, and save/reload must neither repeat XP nor reset the daily cap.
3. Negative coordinates and reversed chunk-generation order must produce the same patches, hosts, and deposits.
4. Hidden reserves must not leak through bot discovery or prospecting, and exhausted nodes must stay exhausted after reload.
5. New terrain must support food, water, basic tools, farming, and pottery without requiring metal or universal seed drops.

Report concrete Critical/Important issues with paths/lines and observable
effects. Grade by what players or controllers actually get. Also list any
minor findings and every behavior you considered but declined to judge,
with reasons. The executor will adjudicate and fix important findings in one
verified pass; do not fix anything yourself.
