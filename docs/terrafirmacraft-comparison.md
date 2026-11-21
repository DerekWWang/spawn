# What to take from TerraFirmaCraft

Written 2026-11-20 from the TerraFirmaCraft `1.21.x` source, its field guide and pack-maker docs, set
against the code on `realism-survival`. TerraFirmaCraft (TFC) is EUPL-1.2, a copyleft licence: the
ideas below can be rebuilt here, its code and textures cannot be copied in.

TFC stops at steel. This mod already goes far past it in scope and in honesty of quantities
(kilograms, man-hours, kilowatts, disease, clothing wear, predators). What TFC still does better is
the first hundred hours, stone age to iron, and a handful of engineering habits.

## Features worth taking, in order

| # | Feature | TFC does | We have | Why take it |
| --- | --- | --- | --- | --- |
| 1 | Food preservation | Salted, pickled, vinegar-sealed and vessel-preserved foods decay at 0.5x to 0.1x. Applying a trait rewrites the creation date once, so nothing ticks | Shelf lives of 0.5 to 7 days and one sealed pot at 1/6 rate. Rock salt has no food use | Winter stores were the whole point of salt. Our food already ages from a timestamp, so traits fit the same way |
| 2 | Mine cave-ins and timbering | Mining raw rock: 10% real collapse, 35% warning only, radius 3 to 18, each block 55%. A horizontal beam protects 9x5x9. Runs only on a mining event | Built structures collapse; natural rock never does | Mining killed people and ate timber. It gives wood a logistics role underground |
| 3 | Surface ore signs | Loose nuggets within 15 blocks of a vein; a pick that reads "traces" to "very large" with fewer false negatives at higher skill | Feeders that reach the surface; prospecting reads visible blocks only | "Float" above a lode is how ore was really found, and it is one more per-column field like loose stones |
| 4 | Diet | Five nutrients averaged over recent meals move max health between 0.2x and 3x | None | For this mod it should be deficiency disease over weeks, not a health bonus |
| 5 | Soil fertility | N, P and K per plot; cereals take nitrogen, legumes return it; manure, bone, ash, saltpetre and guano each add a stated amount | Soil water only; the fertiliser item is neutralised | Rotation, fallow and manure were pre-industrial farming, and it ties stock, farming and the nitre trade together |
| 6 | Salt water | Sea water costs thirst | All surface water is equally unsafe | A few lines |
| 7 | Torches and lamps | Torches last 3 days; lamps burn tallow (2 h a unit) or oil (6 h) from a stored timestamp | Nothing burns out | Light becomes a consumable with a supply chain; tallow already exists |
| 8 | Leather in survival | Limewater soak, scrape, water, tannin; three timed barrel steps | Only a tech-tree hand function; quicklime exists | Leather clothing and harness |
| 9 | Sexed stock | Breeding needs a male and a female | No sex | One bull to many cows is how herds are kept |
| 10 | Early walls | Thatch, mud brick that dries in a day, wattle and daub | Thatch as a physics tag only | Real first shelters, cheap to add |

## Features not to take

- **The anvil pointer game, knapping grids and clay shaping.** They test a human's mouse hand. TFC's
  own guide calls anvil work tedious and later versions added a trip hammer to skip it. Our timed,
  skill-rated jobs are the better model and are the only one an ordered actor can do.
- **Per-item temperature.** It exists to feed the anvil game.
- **Familiarity grinding and size classes.** Mass already limits carrying here.

## Engineering habits worth copying

1. **One clock for timestamps.** TFC keeps a monotonic tick for everything stored and a separate
   calendar tick for season and hour, and forbids storing the second. Here the survival side uses
   game time plus the sleep offset, while tech-tree code reads raw game time
   (`engineering/Engineering.java:388`, `logistics/Gestation.java:115`), so a night slept through
   ages food but not buildings or pregnancies. Worth fixing on its own account.
2. **Self-tests on every dev start.** TFC fails the run if a block lacks a loot table, model or
   name, or if a recipe input has no definition behind it. Two of our worst past bugs were exactly
   this kind (loot counts silently ignored after a field rename; recipe stubs shadowed by the
   loader). The reachability check that exists for industry should cover survival grid recipes too.
3. **One table per fact.** TFC loads each kind of definition through one generic reloader and looks
   it up by item through an index (they measured 11 microseconds against 847 for the recipe
   manager). Here charcoal burns 8000 ticks in survival stations and 1600 in tech-tree stations
   (`industry/Industry.java:324`, `station/StationBlockEntity.java:342`), and tech-tree hand work
   ignores body and skill where survival jobs do not. Those are two answers to one question.
4. **Rewrite on read, never sweep.** TFC cleans a stack's timestamps when something touches it. We
   pass over every player's whole inventory each second.
5. **Decide what an empty server means.** TFC stops both clocks. We inherit whatever
   `pause-when-empty-seconds` is set to.

## What their players complain about

Repetitive metalworking, laborious glass, ores so sparse that people walk thousands of blocks, a
spawn with no copper, a thin endgame. Later versions softened collapses (warnings, wider supports),
slowed thirst and dropped tool-tier gates on ore. The lesson for us: make hardship cost labour and
logistics, which can be delegated, never dexterity; and guarantee the first rung is reachable from
any start, which is what the missing woodland fibre was.
