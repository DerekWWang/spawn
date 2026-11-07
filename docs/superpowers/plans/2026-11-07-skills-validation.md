# Practiced skills validation

Validated 2026-11-07 on branch `realism-survival`, extending `db02d0d`.
Minecraft 26.3 / NeoForge 26.3.0.77 / Java 25, mod `spawn_9b5fbb9`.

## Automated verification

- `qube build`: successful; **39 pure survival behavior assertions** passed.
  Artifact: `build/libs/spawn_9b5fbb9-1.0.0.jar`, 460,275 bytes.
- `qube test`: **91/91 required GameTests passed** (90 mod tests and the
  Minecraft harness check). Qube elapsed time 17.6 seconds; the server reports
  1.463 seconds of actual GameTest execution.
- Pure coverage includes independent progression, invalid/overflow inputs,
  save round-trip, historical v1/v4 migration, and the existing physiology.
  The v4 fixture comes from the archived pre-change JAR, independently of the
  new encoder; see `src/test/resources/README.md`.
- New real-entity regressions cover respawn retention, one-time hunting XP,
  native/controller farming and foraging parity (including offhand input),
  actual NPC pursuit, range/weapon/capacity rejection, loot/wear/hunger,
  cancellation, grid/trade bypasses, matching workstation selection, clay-block
  pottery and quality/capacity, loom inputs, complete metalworking, smith/armor
  gates, novice training, and preservation of required stages/bindings on reload.

The core tests and the three integration review findings were observed failing
before their implementation fixes. The final full suite passed after fixture
interference was corrected; production rules were not weakened for tests.
See `2026-11-07-skills-review.md` for review findings and closure.

## Dedicated replay

`qube server up --stage` booted the built mod. The final
`qube shot --dedicated --replay` replayed all three recorded segments from the
stage reset: **7 proof captures on 4 contact sheets**, including a ten-frame
hunting burst. Every printed sheet was opened and inspected.

The segments are checked in under `validation/`:

| Segment | Evidence |
| --- | --- |
| `skills-workshop.json` | A real client, headless player p2, and NPC artisans; novice/expert clay pottery, black-wool spinning, copper blank formation, skills UI, synchronized levels. |
| `skills-fieldwork.json` | A live NPC pursuit and real animal kill, then actual player foraging and its XP increment. |
| `skills-reconnect.json` | Skill query/sync and the retained skills screen. Exploration used `--fresh` to restart the client; replay itself does not disconnect it. |

From workshop cameras A and B at labelled T+160, six stations and the workers
are visible from opposing elevated angles, with no missing-texture pattern.
The nearer potter view shows the workshop, fire, worker, and gathered supplies.
Explicit `tick sprint 4800` advances loaded production after the initial
160 stepped ticks; Qube's screenshot labels do not include the sprinted ticks.
During exploration, station queries returned a rough novice jar, fine expert
jar, four yarn from black wool, and a copper pickaxe blank. p2 completed pottery
for 25 XP (level 10) and foraging for 4 XP (level 4); the weaver earned 10 XP
(level 6). Dedicated replay getters agree on these client/server levels.

The field camera is at (4, -57.5, 9), looking toward (4, -59, 5).
The T+0 through T+40 burst shows the hunter closing the gap. At T+48 the cow
is red and falling; by T+64 it has died and dropped beef. The actual kill query
reports **25 hunting XP, level 10**, and hunger below its starting 100.
The client agrees with the server on hunting, hunger, and mental capacity.
The missing quarry warning is expected because this scenario kills its target.

At field T+172, the real player's health screen displays all six levels and
next-level XP without overlap. Its forage job changed actual foraging experience
from 100 to **104**, with 6.25 XP remaining to the next level. Main-player
starting levels were assigned for legibility; only the extra 4 forage XP is
claimed as earned by that player in this scene. p2 and the hunter started their
demonstrated skill at zero.

Proof paths (local generated artifacts):

- `run/qube/shots/proof/segment-0.sheet.png`
- `run/qube/shots/proof/segment-1.sheet.png`
- `run/qube/shots/proof/segment-2.sheet.png`
- `run/qube/shots/proof/skilled-hunt.sheet.png`

Replay reported **no desyncs**. Frozen-world `wait` notices correspond to UI
settling or waiting for an explicit tick sprint, not claimed biological/manual
progress. The final persistence screenshot is flagged as visually identical to
the preceding skills screen; identical values are intentional here.

## Persistence and limits

Before a real `--fresh` client restart, `save-all flush` completed and the
player query was saved in `run/qube/skills-before-reconnect.json`. The query
after reconnect is `skills-after-reconnect.json`. A comparison asserted the
same entity UUID, exact equality of all six skill records, and actual foraging
experience of 104. It passed. The reconnected skill screen was also opened.
Pure save tests and GameTests separately cover persistence and death retention.
This extension did not perform a new full operating-server restart test.

Only **one real game client** was used for the skills extension, alongside
headless p2 and NPCs. A second real client could not start because the host had
about 1.2 GB free against its 2 GB requirement. No second-client viewpoint claim
is made. The prior survival report's two-client run remains historical evidence
for the earlier feature set.

This provides shared, constrained actions and observations for external bots
and scripts. It does not claim a new general-purpose human-level autonomous
planner. Strategy and collaboration decisions remain with players/controllers.
Long-term game balance is tunable and has not been established by these short
controlled scenarios. The validation server was stopped after replay.
