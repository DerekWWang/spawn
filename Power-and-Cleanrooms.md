---
title: Power and Cleanrooms
---
{% include nav.html %}

# Power and Cleanrooms

Electricity is made by burning coal under a boiler and carried along cable ducts to the works that need it. A cleanroom is a sealed, filtered, powered room that semiconductor work cannot do without. Both are layouts you build to rules; neither is a single magic block.

## The power station

A power station is the switchboard block (built at an assembly yard) plus the plant around it.

| It needs | Where |
| --- | --- |
| At least one generating set: a generator and a stationary engine to turn it | Both within three blocks of the switchboard. Each generator needs its own engine |
| A steam boiler | Within four blocks |
| A chimney at least 6 high, open to the sky | Within three blocks of the boiler |
| Feed water | Within six blocks of the boiler |
| Fuel | Stoked into the switchboard: coal, coke, charcoal, logs or sticks. It bunkers 8,000 sacks (200 tonnes) |

`/survival works status` at the switchboard shows whether it is running, its generators and capacity, the load in the current second, and fuel in sacks.

## Kilowatts

| Rule | Value |
| --- | --- |
| Capacity | 35 kW per generating set, which is what a fifty-horsepower engine can turn |
| Coal burned | 2.5 kg per kilowatt-hour: 16 fuel ticks a second for each kilowatt of demand |
| Cable loss | About two percent per hundred metres: demand at the switchboard is the load times (1 + cable length / 5,000) |
| Longest run | 1,600 blocks of cable duct from consumer to switchboard |

A consumer is connected when a continuous run of power cable blocks leads from it to the switchboard. Cable ducts may pass through walls and floors, including a cleanroom shell. Each block of cable is a metre of lead-sheathed main: 6 kg of copper and 8 kg of lead. Each block of cable is a metre of lead-sheathed main: 6 kg of copper and 8 kg of lead.

Each electric recipe draws its real load while its crew works. If the station cannot carry the load (too few generators, or out of fuel) the job stops and says so. The energy a batch costs follows the work actually done, so a batch costs the same kilowatt-hours whether three people work it or one.

| Process | Load | Specific energy |
| --- | --- | --- |
| Furnace silicon (arc furnace) | 400 kW | 12 MWh per tonne |
| Phosphorus (arc furnace) | 100 kW | 12.5 kWh per kg |
| Polysilicon | 150 kW | 150 kWh per kg |
| Aluminium (electrolytic cell) | 100 kW | 25 kWh per kg |
| Tungsten (electrolytic cell) | 50 kW | |
| Crystal pulling | 30 kW | |
| Wafer stages | 15 to 25 kW | |
| Other electronics work | 10 kW | |
| Cleanroom filters | 3 kW plus half a kilowatt per filter unit | |

An arc furnace making silicon needs twelve generating sets to itself. A potline needs three, and a small fab with its filters two.

## Cleanroom rules

Every wafer stage, dicing and packaging must be done at an electronics works that stands inside a cleanroom. The works surveys the room it stands in every five seconds.

| Rule | Requirement |
| --- | --- |
| Sealed | The space the works stands in must be fully enclosed. A room larger than 15 x 15 x 7 counts as unsealed |
| Size | At least 5 x 5 inside and 3 high |
| Shell | Every block facing the inside must be cleanroom panel, glass, a filter unit, a cable duct or an iron door |
| Filters | One filter unit in the ceiling for every four square blocks of floor, rounded up |
| Airlock | At least one iron door |
| Power | The works must be on a cable to a running power station; the filters draw power whenever the room is being held clean |
| Dress | Everyone inside wears a cleanroom suit (chest slot) |

Plant blocks other than panels, filters and cable ducts count as equipment standing in the room, not as wall.

### Cleanliness

The room has a cleanliness from 0 to 100%. Each five-second survey changes it:

| Condition at the survey | Change |
| --- | --- |
| Sealed, powered, door shut, everyone suited | +5 |
| Filters unpowered | -10 |
| Door open, or anyone inside without a suit | -15 |
| Any fault in the room itself (a hole, a dirty wall block, too few filters, too small, no door) | Falls to 0 at once |

From nothing, a sound room takes 100 seconds to reach full cleanliness.

### Yield

No wafer stage, dicing or packaging starts below 95%. Defects show at dicing and test: the number of good dice cut from each wafer is scaled by cleanliness at that moment, from none at 90% through half at 95% to all 160 at 100%. The wafer stages and packaging pass on what they are given.

## Building one that works

1. Lay a floor of cleanroom panel at least 5 x 5, walls three high, and a ceiling of filter units (a 5 x 5 room needs seven, and a full filter ceiling is simplest).
2. Fit an iron door. Glass windows are allowed.
3. Stand the electronics works inside, with the equipment its recipes name within five blocks.
4. Run cable duct under the floor to the switchboard.
5. Suit up before entering, shut the door, and wait for cleanliness to reach 100% before starting a lot.

## Common mistakes

- A wall or floor block of ordinary masonry. The status line names the first offending block.
- A power station sized for the works but not for the filters as well.
- Opening the door while dicing. One open door costs fifteen points, which stops all work until the room recovers.
- Sending in a visitor without a suit.
