# Skills, ecology, geology, and item identity

Status: approved by the user's “this looks good” response; the reviewed
implementation plan was subsequently authorized for execution. Current
implementation and verification evidence is recorded in the execution ledger.

## Intent and boundaries

Make the existing survival simulator support believable specialists and places
worth working together. Agents, players, fake players, and enrolled humanoids
use the same rules. Learned competence stays separate from bodily capacity.
Resources have recognizable habitats, spatial continuity, and finite supply.
Names and 16 by 16 item icons represent their materials and processing stages.

The user explicitly excludes backstories. Do not add biographies, childhoods,
profession classes, inherited skill bonuses, or backstory-based incapabilities.
Do not add a currency, auction house, autonomous settlement manager, or a
scripted trading economy. The existing controller interface and actual item
exchange remain the means of coordination.

Approved assumptions:

- Preserve distinct pottery, smithing, weaving, farming, foraging, and hunting
  skills. Extend them with mining and other existing useful work rather than
  making every craft share a single competence. The user approved the design
  retaining these distinct trades rather than RimWorld's exact twelve skills.
- World-generation changes apply to new terrain. Do not erase old generated
  resources or alter player construction automatically.
- A coherent, deterministic geological approximation is sufficient for this
  change; full tectonics and historical crop domestication are separate work.

## Evidence in the current project

- `SkillSet` has six XP doubles, square-root progression, and levels 0 through
  100. `SurvivalState` schema 5 serializes those values by enum order.
- Recipe requirements, pottery quality, debug commands, health inspection,
  network snapshots, and controller JSON all consume that scale.
- `loose_stones` has twelve placement attempts per chunk, `wild_plants` five,
  and `fruit_tree` one. All target grassy locations across Overworld biomes.
- `GroundResourceBlock.completeGather` makes one wild plant yield rice, maize,
  fiber, medicinal, wheat, beetroot, melon, and pumpkin seeds, carrots, potatoes,
  sticks, and sometimes berries. Rocks also create a stick as an extra drop.
- Tin has twelve eight-block vein attempts per chunk over a uniform height
  interval. Vanilla ore distributions otherwise remain in place.
- There are 119 item-definition JSON files but only four custom item texture
  files. Crushed tin uses the bronze-ingot icon; seeds use the fiber icon;
  bronze tools use the knapped-stone pickaxe icon.

## Approaches considered

Recommended: connect a richer skill state to the existing work system, replace
universal resource scatter with ecological patches, and generate deterministic
mineral districts with finite deposits. This changes the causes of the current
behavior and makes exploration and hauling useful.

Reducing placement counts alone is a smaller alternative. It improves clutter
but leaves universal seed drops, uniform ore access, and interchangeable item
icons intact.

A full geological and evolutionary simulation is a larger alternative. It
would require new terrain formation, stratigraphy, hydrology, and domestication
systems. It is beyond this connected revision to existing gameplay.

## Agent skill state

Use the learning mechanics in the supplied [RimWorld skills reference](https://rimworldwiki.com/wiki/Skills)
as the model: independent levels 0 through 20; increasing XP requirements;
none, interested, and burning passions; reduced learning after a daily soft
cap; and increasing XP loss at expert levels. Do not import fiction-specific
traits, implants, genes, or backstories.

Each skill record stores a stable skill identifier, displayed level, XP within
that level, passion, net daily learning, and practice timing. Store the actor's
learning multiplier and simulated-day bookkeeping with the skill set. Stable
identifiers and a bounded record count replace persistence by enum ordinal.

Use the reference's XP thresholds: 1,000 XP from level 0 to 1, increasing costs
thereafter, 55,000 cumulative XP at 10, and 265,000 at 20. Learning multipliers
are 0.35, 1.0, and 1.5 for the three passions. The default global multiplier is
1.0. After 4,000 net XP per skill per simulation day, additional learning is
20% of its usual rate; split an award that crosses the threshold correctly.

Apply the reference decay schedule from level 10 onward, scaled to this mod's
24,000-tick day. Preserve a 1,000-XP loss buffer before downgrading a displayed
level. Decay can return an expert to level 9, where it stops. Net learning
accounts for decay. Saturation at level 20 must not create an unbounded XP
reserve. Paused world time and offline wall time do not advance skill clocks.
Ordinary loaded sleep advances decay and the daily clock but earns no work XP.

Default new actors to level zero and no passions; the data model supports
explicitly assigned interests without inventing a personal history. Store
passions once and retain them through save/load and respawn. Operator setup
commands may assign passions; ordinary bot actions may not rewrite them. A
future mood system can use passions, but this change does not invent a mood
bonus in the existing recreation meter.

Earn practice from actual loaded, validated useful work. Manual jobs accumulate
bounded work exposure and commit the corresponding learning once with a real
outcome. This keeps task speed from multiplying XP per second and preserves
the project's no-XP-on-cancellation rule. Treatment and real animal kills may
grant bounded outcome awards. Reject waiting, passive heating, harvesting an
empty plant, invalid work, cancelled jobs, duplicate completion, and reconnect
as sources of practice. Native and controller actions share the same adapters.

Initially connect every displayed skill to existing work:

| Skill | Useful work |
| --- | --- |
| Pottery | Manual shaping and finishing clay vessels |
| Smithing | Active metal preparation, forging, riveting, and tool finishing |
| Weaving | Spinning, weaving, and garment construction |
| Farming | Tilling, sowing, and harvesting cultivated crops |
| Foraging | Gathering identified wild plants, fruit, and surface resources |
| Hunting | Successful real hunting outcomes |
| Mining | Actual stone, seam, and ore extraction; prospecting interpretation |
| Construction | Successful placed construction and existing wooden assembly |
| Cooking | Attended food preparation using real ingredients |
| Animals | Successful livestock tending and product collection |
| Medicine | Effective treatment consuming a real suitable supply |

Do not add disconnected cosmetic skills or repeatable placement/breaking XP
loops. Construction practice must use actual manual construction or assembly
costs; instant placement alone is not a practice farm. Passive cooking must not
silently become an attended job merely to award XP.

Normalize existing work-speed and effective-skill formulas onto the new scale
before tuning their balance. Migrate recipe gates 20/40/60/70 to 4/8/12/14 and
pottery quality gates 25/60 to 5/12. Audit every scale consumer, including
datapack validation and defaults. Existing physical requirements still apply.

For schema-5 saves, interpret the six doubles using the old formula, map the
continuous score onto 0 through 20, and preserve fractional progress between
the corresponding new thresholds. Keep each original trade independent. New
skills start at zero; older schemas remain supported. Reject non-finite or
out-of-bounds records without partially applying invalid state. Persist the
new state in a versioned schema and bump the network protocol with it.

Inspection shows level, passion, XP bar, next-level cost, learning multiplier,
daily learning limit, and decay rate. Use a readable skills view with scrolling
or a separate page; eleven skills must not be squeezed into the present six
small cells. Controller JSON exposes the same values, effective capacity,
schema version, and recipe requirements. Document the 0-through-20 API change.

## Surface ecology

Replace independent chunk-wide scatter with seeded patch centers, low overall
coverage, and explicit substrate, climate, moisture, and light checks. Adjacent
chunks sample the same coordinate field. A patch crossing a chunk edge is one
patch, and evaluating chunks in a different order does not change its contents.
Generation only modifies its own chunk, replaces eligible natural blocks, and
never forces adjacent chunks to load.

Initial surface defaults are one eligible plant-patch center per eight chunks
on average, a radius of three through six blocks, and five through twelve
occupied positions per patch. A location rejected by habitat checks does not
trigger a search that fills another habitat instead. Away from rocky or
alluvial contexts, stone occupancy is at most 0.1% of eligible surface
positions. Eligible rocky patches can occupy up to 3% locally. Fruit groves
average one candidate per sixty-four chunks, contain two through five trees,
and maintain at least five blocks between trunks. These are simulation tuning
defaults, not measured ecological frequencies.

- Loose fragments cluster near exposed rock, gravel bars, and steep rocky
  slopes. Keep occasional isolated fragments for early survival, with a low
  density away from those contexts. Several small uneven rock shapes replace
  the repeated identical slab. Rocks yield rock material, not bonus sticks.
- Fallen sticks occur near real trees and woody debris. Keep them separate
  from rocks and crop seed sources.
- Identify wild sources independently: fiber plants in suitable grassland,
  yarrow in open temperate habitat, brambles near suitable woodland edges, and
  rice relatives in warm wet margins. Each source yields its own material and
  propagules with finite seasonal harvests. Never return an all-crops bundle.
- Retain existing crop access through distinct, named habitat patches for
  wheat, rice, maize, root crops, and gourds. Explicitly document any retained
  domesticated crop in unmanaged terrain as a gameplay approximation. Do not
  relabel teosinte as modern maize, or American wild rice as Asian rice.
- Generate fruit trees as occasional compatible groves with separation between
  trunks. Make their appearance and name correspond to the apples they yield.
  Harvest fruit without producing a free plantable tree every harvest.
- Preserve existing crop-season, establishment, and harvest recovery rules.
  Planted species use suitable substrates, and plant support rules remain true
  after terrain changes. Resources must not float after their support is lost.

Bot resource discovery returns visible nearby resource identities, positions,
habitats, harvest readiness, and known methods. It must not reveal hidden ore
coordinates. Scarcity must still leave nonmetal routes to food, water, basic
tools, pottery, and agricultural establishment.

The habitat choices are design approximations informed by the referenced
articles, rather than a claim to reproduce real biogeographic ranges.

## Mineral districts and quarry sites

Use world seed and coordinates to define mineral districts extending over
multiple chunks. Select host-rock envelopes, deposit centers, depth, orientation,
thickness, and grade deterministically. Sample these fields from each chunk
without placing into neighboring chunks or storing a global mutable RNG.

Provide large but finite workable deposits separated by meaningful unproductive
terrain. Initial defaults use 256-block district cells with jittered centers,
20% barren cells, and one main mineral family in each productive cell. Family
weights are copper 35%, iron 30%, tin 15%, and coal 20%. Main ore bodies span
twenty through forty-eight blocks horizontally and four through eight blocks
vertically, with orientation, thickness, and grade variation inside their host
envelopes. They are not solid rectangular masses of ore. Use average ore grades
of 15% through 30% within mineralized envelopes; hosts and discontinuities
account for the remainder. Quarry-rock bodies and sediment sources have their
own more common spatial fields. Survey these defaults across several world
seeds before making availability claims. Connected barren host rock around ore
makes excavation, waste removal, access ramps, stockpiles, and hauling useful.

| Resource | Deposit and extraction identity |
| --- | --- |
| Building stone | Coherent granite, limestone, and sandstone bodies or beds; quarry as dimension stone and aggregate |
| Copper | Malachite-bearing weathered copper deposits with green outcrop material and mineable host rock |
| Tin | Cassiterite in granite-associated veins, with limited alluvial concentrations where appropriate |
| Iron | Hematite-rich ironstone bands with intervening lower-grade host rock |
| Coal | Layered seams in sedimentary settings |
| Clay, sand, gravel | Separate workable sediment bodies appropriate to wetland, river, and sedimentary contexts |

A quarry extracts rock; metal ore sites are mines or open-pit workings. Keep
those names accurate. The distinction and deposit choices follow
[quarry](https://en.wikipedia.org/wiki/Quarry),
[cassiterite](https://en.wikipedia.org/wiki/Cassiterite),
[hematite](https://en.wikipedia.org/wiki/Hematite), and
[coal](https://en.wikipedia.org/wiki/Coal) references.

Remove replaced vanilla copper, iron, and coal scatter features, including
their small, large, buried, and depth variants. Remove the current tin scatter.
Audit remaining natural ore features so old generation cannot quietly defeat
regional scarcity. Preserve unrelated world-generation rules and document the
remaining supported resources rather than inventing replacement geology for
every vanilla collectible in this pass.

Deposits exhaust through actual block extraction and never respawn on chunk
reload. No node timer, remote mining, free quarry output, or created trading
inventory. Retain tool requirements, hunger costs, equipment wear, loaded work,
permissions, and bodily capacity. Mining skill changes work efficiency and
the interpretation of exposed evidence, not the quantity of metal in a block.

Expose a bounded prospecting action using a real nearby outcrop or extracted
sample. Report observed mineral, host rock, and a limited confidence/quality
estimate. Do not reveal unobserved reserves or a guaranteed exact direction to
buried ore. Controllers can plan and share discoveries through the existing
API, then workers extract and haulers transport actual goods. Regional supply,
specialist labor, and distinct material chains provide the collaboration
incentive without mandatory parties or arbitrary profession locks.

Maintain compatibility recipes for existing ore items while new ore blocks
drop identified material. Crushed ore, roasted ore, extracted metal, bloom,
ingot, blank, and finished tool remain distinct forms. Do not make a crushed
ore look or read like an ingot. Preserve real fuel and material conservation.

## Item identity and icon work

The accompanying `2026-11-07-item-reference-audit.csv` covers the 119 existing
item definitions with a proposed display name, Wikipedia reference, icon
silhouette, and notes. Registry IDs remain stable for save compatibility;
display names and assets can change. New mineral and plant identities receive
new stable identifiers where reusing a generic item would falsely identify
its contents.

Use Wikipedia to establish identity, material, and recognizable physical form;
create original pixel art rather than copying photographs. Some entries are
honestly generic classes: the current recipe does not identify a particular
antibiotic or analgesic compound. Do not rename those to a specific substance
without implementing its real provenance. Botanical identity is not evidence
that a plant produces the current medicine's simulated effects.

Every item gets a 16 by 16 transparent icon appropriate to its form: irregular
ore pieces, granular crushed ore, fibrous bundles, twisted cord, woven cloth,
distinct seeds and kernels, clay vessels, fasteners, and recognizable blanks.
Closely related materials may share a silhouette but require material-specific
palettes and details. A blank lacks the completed tool's handle or fittings.
Wool and plant textiles, and tin, copper, bronze, and iron, remain distinguishable.

Tools use flat item icons. No new 3D tool modeling is required. Use
`qube texture` templates where suitable and palette grids for other shapes.
Verify actual 16 by 16 dimensions, transparent borders, complete modern item
definitions and model bindings, and an empty `bindings_missing` list. Open the
enlarged texture previews and a labelled inventory contact sheet before
accepting the art. Source names and visual references must also cover new
items introduced during implementation.

## Validation and acceptance

Add behavioral tests for XP thresholds, independent trades, passion rates,
crossing the daily cap, day rollover, decay and its buffer, capped XP, invalid
inputs, duplicate outcomes, save migration, reconnect, and respawn retention.
Test native/controller parity and material/XP conservation at actual jobs.

Survey deterministic generated terrain at several seeds and chunk orders.
Measure patch coverage, habitat violations, ore counts, connected deposit
extent, barren spacing, cross-chunk continuity, and the absence of replaced
vanilla scatter. Verify no resource replenishment after reload and no forced
neighbor chunk generation. Include first-day survival routes that do not need
metal and a two-agent extraction/hauling/production scenario with actual items.

Run `qube build` and `qube test`. Then use fresh generated terrain and controlled
workshop stages for `qube shot`, followed by `qube shot --replay`. Open every
printed contact sheet. Capture sparse grassland, a realistic resource patch,
two quarry/mine angles, ore exposure, the skills view, and the item atlas.
Check the real client, a headless player, and an NPC against the same server
rules. Report observed results, remaining approximations, and generated-terrain
compatibility limits without claiming a build alone proves visual quality.

## Reference notes

Research performed with the required gstack browse skill on 2026-11-07.
Sources inform identity and appearance; numerical game tuning remains a design
choice unless explicitly attributed to the RimWorld skill mechanics.

- [Skills](https://rimworldwiki.com/wiki/Skills): progression, passions, daily learning and expert decay.
- [Scree](https://en.wikipedia.org/wiki/Scree): rockfall fragments and slope context.
- [Placer deposit](https://en.wikipedia.org/wiki/Placer_deposit): sedimentary mineral concentration.
- [Malachite](https://en.wikipedia.org/wiki/Malachite): copper ore identity and green appearance.
- [Banded iron formation](https://en.wikipedia.org/wiki/Banded_iron_formation): connected iron-rich and iron-poor bands.
- [Granite](https://en.wikipedia.org/wiki/Granite), [limestone](https://en.wikipedia.org/wiki/Limestone), [sandstone](https://en.wikipedia.org/wiki/Sandstone), [clay](https://en.wikipedia.org/wiki/Clay): host and quarry materials.
- [Pale flax](https://en.wikipedia.org/wiki/Linum_bienne), [flax](https://en.wikipedia.org/wiki/Flax), [yarrow](https://en.wikipedia.org/wiki/Achillea_millefolium), [blackberry](https://en.wikipedia.org/wiki/Blackberry): identifiable plants and fiber sources.
- [Oryza rufipogon](https://en.wikipedia.org/wiki/Oryza_rufipogon), [rice](https://en.wikipedia.org/wiki/Rice), [maize](https://en.wikipedia.org/wiki/Maize), [teosinte](https://en.wikipedia.org/wiki/Teosinte), [emmer](https://en.wikipedia.org/wiki/Wild_emmer): distinguish domesticated crops from wild relatives.
- [Bloomery](https://en.wikipedia.org/wiki/Bloomery), [smelting](https://en.wikipedia.org/wiki/Smelting), [knapping](https://en.wikipedia.org/wiki/Knapping): processing stages and suitable material identities.
- The item-reference audit links the corresponding articles for every existing
  item-definition family, including textiles, garments, tools, vessels,
  treatment classes, metalwork, and stations.
