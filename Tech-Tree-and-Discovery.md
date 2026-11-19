---
title: Tech Tree and Discovery
---
{% include nav.html %}

# Tech Tree and Discovery

On top of the survival rules sits a tech tree from stone tools to servers: 526 functions, of which 109 are theories and 417 are practical recipes. Nothing is granted by an era or a research budget. A society can rely on a function only once it has **discovered** it, by holding everything the function needs and taking the time to work it out.

Press **J** to open the journal.

## How the tree and the survival rules fit together

The tree and the survival rules are one game. Where they cover the same ground, the survival rules win.

| Shared ground | What happens |
| --- | --- |
| 63 things that are both survival items and tree items, such as cordage, plant fibre, pig iron, muskets, cannon, wagons and silicon wafers | There is one item, the survival one. The tree's functions use it and make it |
| The kiln, bloomery, forge, loom, lead chamber, machine shop, crystal puller, diffusion furnace and power station | These are the survival workstations ([Building Crafting and Workstations](Building-Crafting-and-Workstations), [Industrial Manufacturing](Industrial-Manufacturing)). A survival workstation within 16 blocks counts as that station. The survival stationary engine counts as the steam engine |
| Work the tree did at those stations (120 functions) | Done by hand from the journal while you stand within 16 blocks of the workstation |
| Bauxite, borax, cassiterite, fluorite, galena, malachite, rock salt, sulfur, wolframite and limestone | Placed by the survival geology ([Natural Resources and Mining](Natural-Resources-and-Mining)). The tree has no deposits of its own for these |
| Food | Aged by the survival rules ([Water Pottery and Food](Water-Pottery-and-Food)). The tree's spoilage covers only goods they do not age |
| Breeding | The survival husbandry ([Agriculture and Livestock](Agriculture-and-Livestock)) |

## Playing

1. **Gather.** Pick wild plants for fibre, break gravel for flint, and so on. An item's tooltip says which functions make it and whether your society knows how.
2. **Pick a goal.** Discovery is a means, not a ladder to climb. Choose any item or function as a goal (the *Goal* filter, or `/techtree plan <name>`). The journal then lists only the undiscovered steps between you and it, in an order you can follow.
3. **Discover.** Hold every consumed input and every means of production, then press *Study*. Means of production are tools in your inventory, stations built and complete within 16 blocks, and phenomena where you stand, such as a night sky or flowing water. Study takes time: a few seconds for a simple craft, longer for deep ideas. Scientific method and recorded scholarship shorten it. Nothing is consumed.
4. **Make.** Functions with no station are done by hand from the journal (*Make x1* or *x8*). They take time, and the inputs are taken only when each run finishes. The same goes for work done beside a survival workstation. Everything else runs at a station: see [Tech Tree Stations](Tech-Tree-Stations).

Hunting and harvesting happen in the world.

- **Hunting.** Kill game with a hunting weapon once your society knows Hunting, and the animal leaves a carcass. The carcass is the meat, to be butchered. The cuts the animal would otherwise drop are not left as well. Its hide, wool and bones are.
- **Harvesting.** Cut ripe crops in a formed grain field with a sickle once your society knows Harvesting, and they give grain instead of the wheat the crop would otherwise drop. Crop rotation, a seed drill in your pack and an irrigation canal within 12 blocks each raise the yield.

## Theory does not block practice

People smelted copper for thousands of years before anyone could say why it worked. A practical function can be discovered and run without the theories behind it. The price is spoiled batches:

- Each missing theory wastes half the batches at first.
- Every run is experience, and cuts the waste by 15%.
- Learning the theory removes the waste for every craft that depends on it.

Theories are still needed to reach other theories. So a society learns a theory when the waste hurts more than the study, which is a decision, not a gate.

Servers that want stricter rules can turn on `quizMode` (pick the true mechanism from four to discover a function) and `theoryRequiredForPractice` in `config/spawn_9b5fbb9-local.toml`.

## Societies

Knowledge belongs to a **society**: a scoreboard team, whose members share everything, or a lone player. Knowledge is saved with the world. A player who joins a team brings what they know.

Death in Spawn is permanent ([Survival and Physiology](Survival-and-Physiology)), but what a society knows outlives any one of its members.

## Knowledge that changes the world

Some theories do more than unlock recipes.

| Theory or thing | Effect |
| --- | --- |
| Writing | Lets a society write treatises that teach a theory to another literate society, and count stores on paper |
| Counting, Arithmetic, Statistics, computing | Make the society's records more accurate ([Logistics and Records](Logistics-and-Records)) |
| Scientific method | Halves the time to study a theory |
| Recorded scholarship | Takes off another quarter |
| Thermodynamics | Stretches every fuel by a quarter |
| Prospecting | Adds a *Survey ground* button that reports deposits beneath you |

## Commands

Everything a player can do from the journal can be done by command, so a bot can run a society.

| Command | What it does |
| --- | --- |
| `/techtree plan <item or function>` | The undiscovered steps to a goal, in order |
| `/techtree status <function id>` | What is still missing to discover or run it here |
| `/techtree discover <function id>` | Study a function |
| `/techtree make <function id> [times]` | Do hand work; `/techtree stop` stops it |
| `/techtree list [player]` | What a society knows |
| `/techtree info <function id>` | A function's inputs, outputs, means and theory |
| `/techtree grant <players> <function id or all>`, `/techtree revoke ...` | Operators only |
