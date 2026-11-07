# Practiced survival skills

Extend the approved survival simulation so repeated useful work develops
specialists among players, bots, and scripted humanoids. Manipulation remains
physical capacity; learned pottery, smithing, weaving, farming, foraging, and
hunting are separate. No profession is permanently locked to an entity class.

Each skill starts at level 0, stores bounded experience in the body, and reaches
level `floor(2 * sqrt(experience))`, capped at 100. Completed resource-consuming
jobs award experience once; rejected/cancelled work, waiting, and relogging do
not. Different skills develop independently. Saved bodies and network snapshots
retain skills; respawn retains learned skills while resetting bodily injuries.
Old bodies load with novice skills. Diagnostics may set levels, but normal
controller actions cannot grant experience or bypass skill gates.

## Pottery

A block of clay, a kiln, fuel, and an able potter suffice to make a jar. No
potter's wheel or metal prerequisite is added. Pottery requires at least 25%
manipulation and active manual time alongside firing. Skill and current
manipulation determine quality. Rough/standard/fine jars hold 2/3/4 food stacks
and water portions and preserve sealed food for 2/4/6 times its ordinary life.
Fine jars require an effective pottery score of 60; standard starts at 25.
Unmarked pre-update jars keep their existing fine-jar behavior. A cancelled or
reloaded process cannot mint a finished jar or repeat its practice award.

## Smithing

Use a real chain of specialized blocks: bloomery for smelting, forge for heating
and forming blanks, anvil for finishing equipment. Fuel is consumed at heated
steps; anvil work consumes real blanks and binding/riveting supplies and requires
loaded manual time. Early masonry/stone versions avoid an iron bootstrap cycle.
Useful novice metalwork provides training. Copper tools require smithing 20,
bronze 40, iron 60; metal armor requires a practiced smith (at least 60, with
iron armor 70). Skill and physical capacity are rechecked during manual work.
Normal grid recipes cannot bypass these stations or gates.
Virtual merchant offers cannot mint specialist equipment outside the production
chain. Humanoids can still exchange actual goods made by their collaborators.

## Weaving

A dedicated loom turns wool into yarn, yarn into cloth, and plant-fiber cordage
into cloth, then cloth into garments. Every step consumes the actual recipe
inputs and loaded manual work; it needs no kiln fuel. Existing resource ratios
remain the baseline (four yarn per wool block, four yarn or three cordage per
cloth). Simple garments train novices; more demanding garments require weaving
practice. Instant grid cloth/garment recipes are replaced so humans and scripts
use the same process.

## Farming, foraging, and hunting

Keep the existing validated till/sow/harvest/gather actions. Their completed work
develops farming/foraging and becomes faster with practice, within physiological
capacity. Expose an explicit forage alias and a shared hunting action for real
animals. Hunting requires a usable weapon and actual reach/line of sight; it
uses real movement, damage, item wear, hunger, and animal drops. NPC navigation
can pursue quarry; player controllers supply their own movement when needed.
No remote kills, created food, offline labor, or simultaneous manual jobs.
Ordinary player actions also develop the corresponding skills.

## Visibility and validation

Expose skills and next-level progress through health inspection, commands,
query JSON, and controller planning. Planning also reports exact station and
skill requirements. Use schema migration, pure learning tests, real GameTests
for station/quality/gate/conservation/action parity, full regression suite,
dedicated startup, and Qube replay of the new workshop and skill inspection.
Keep unrelated existing workspace files out of commits.
