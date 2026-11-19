---
title: Weather Climate and Geography
redirect_from:
  - /weather-and-fire
---
{% include nav.html %}

# Weather Climate and Geography

The world has real geography. With the Tectonic terrain profile installed, air temperature, rain, snow and wind are computed for every place from the terrain itself: height above the sea, distance from the sea, the mountains that stand upwind, and the season and hour. Every body, crop, animal, vehicle and gun reads the same weather, and each player sees the rain or snow of the spot they stand on. This page explains how that weather is produced, what it does, and how to read it. Worlds without Tectonic run a simpler legacy climate, described near the end.

## Two world profiles

A world is created with one of two profiles and keeps it for life.

| | Tectonic profile | Legacy profile |
| --- | --- | --- |
| Chosen when | Tectonic 3.0.32 and Lithostitched 2.0.6 are installed and the world is new | Tectonic is not installed |
| Terrain | Tectonic | Vanilla |
| Weather | Regional, computed per position | Vanilla global rain cycle |
| Air temperature | Continuous, from terrain, season, hour and fronts | A fixed value per biome band, shifted by season |
| Soil water and irrigation | Yes | No, farmland uses vanilla moisture |
| Mineral districts | Follow geological provinces and terrain height | Fixed depth bands |
| Where it applies | Overworld only | Everywhere |

Everything from "How terrain is read" to "What weather does" describes the Tectonic profile.

## Creating a world

1. Install the mod, Tectonic 3.0.32 and Lithostitched 2.0.6 on the server and on every client. Other versions of Tectonic or Lithostitched are refused at start.
2. Use the supplied Tectonic configuration. `tectonic.json` must have `general.mod_enabled` set to `true` and `continents.river_lanterns` set to `false`, or the server refuses to start.
3. Set `seasonDays` in the mod's configuration before the first start if you want a season length other than the default.
4. Create a new world.

| Option | Default | Range | Where |
| --- | --- | --- | --- |
| `seasonDays` | 28 game days per season | 4 to 112 | Mod configuration |
| Tectonic configuration | supplied file | fixed for the life of the world | `config/tectonic.json` |
| `-Dspawn.adoptEnvironment=true` | off | JVM flag | Lets Tectonic adopt an existing world that has no saved profile |

The profile is written to `data/spawn_9b5fbb9-environment.json` inside the world folder. It records the profile, the season length and a fingerprint of the Tectonic configuration. After that the server refuses to open the world if any of these no longer match:

- A Tectonic world opened without Tectonic.
- A legacy world opened with Tectonic installed.
- A Tectonic world whose `tectonic.json` has changed.
- A Tectonic world whose `seasonDays` differs from the saved value.
- An existing world with no saved profile, opened with Tectonic, unless the adopt flag is set. Chunks generated before adoption keep their old terrain and resources.

Restore the original configuration to reopen the world. Do not delete the profile file.

## How terrain is read

The mod never looks at what players have built. It samples the terrain generator on a lattice of points 64 blocks apart and blends between them. Each point has six values.

| Value | Meaning | How it is found |
| --- | --- | --- |
| Elevation | Height of the natural ground above sea level, in blocks | Generator height minus the generator's sea level |
| Relief | Roughness | Highest minus lowest ground among the point and its four neighbours 64 blocks away |
| Temperature | Climate warmth of the region | The generator's temperature field, scaled to between -0.4 and 1.8 |
| Humidity | Climate wetness of the region | The generator's humidity field, scaled to between 0 and 1 |
| Continentalness | Distance from the sea | The generator's continentalness field; negative is maritime, positive is inland |
| Rain shadow | How much high ground stands upwind | See "Rain shadow" |

For a body or a crop, the height used is its own height above sea level, so climbing a mountain or a tower really is colder.

## Temperature

The air temperature at a place is the sum of the parts below, limited to between -65 and 55 °C.

| Part | Size | Notes |
| --- | --- | --- |
| Baseline | -8 + 38 x temperature value, limited to -55 to 48 °C | The climate of the region |
| Height | -0.065 °C per block above sea level | 100 blocks higher is 6.5 °C colder. Below sea level there is no gain |
| Rain shadow | up to +2 °C | Dry descending air in the lee of mountains is warmer |
| Season | plus or minus 5 to 9 °C | A sine wave over the year. Warmest in the middle of summer, coldest in the middle of winter. The swing is 7 °C, less 2 on the open sea and plus 2 deep inland |
| Hour | plus or minus 4 °C inland, 2 °C by the sea | Warmest 9,000 ticks into each calendar day, coldest at 21,000. "By the sea" means continentalness below -0.1 |
| Weather anomaly | plus or minus 3.5 °C | Warm and cold spells about 3,072 blocks across that change over about three days |
| Rain | down to -2 °C | Proportional to rain strength |

Snow falls instead of rain whenever the air temperature is 0 °C or below.

## Rain, snow and fronts

Rain strength at a place is a number from 0 to 1.

1. A field of weather fronts covers the world. Its features are about 2,048 blocks across, it drifts 840 blocks a day toward positive X and 432 blocks a day toward positive Z, and it reshapes itself over about two days. At any place it gives a front value between 0 and 1.
2. Rain strength = (front + 0.42 x humidity - 0.42 x rain shadow - 0.5) x 3, limited to 0 to 1.
3. Rain or snow actually falls, wets bodies, fouls powder and bogs wheels when the strength is above 0.15.

What that means for planning:

| Place | Front value needed for rain to fall |
| --- | --- |
| Wettest climate (humidity 1), no shadow | above 0.13 |
| Middling climate (humidity 0.5), no shadow | above 0.34 |
| Dry climate (humidity 0.2), no shadow | above 0.47 |
| Middling climate in full rain shadow | above 0.76 |

A wet region is rained on by almost every passing front. A fully shadowed valley sees rain only from the strongest ones.

## Rain shadow

Each world has one prevailing wind direction, fixed by the seed. For every lattice point the mod looks at the ground 256, 512, 768 and 1,024 blocks upwind and takes the highest.

- If that barrier is less than 24 blocks higher than the point, there is no shadow.
- The shadow grows from 0 to 1 as the barrier rises from 24 to 216 blocks above the point.
- A full shadow takes as much from rain strength as the wettest climate adds, and warms the baseline by 2 °C.

The windward side of a range is wet. The lee side, within about a thousand blocks, is dry and slightly warmer. Settle crops on the windward side or beside water.

## Wind

Wind speed is 2 m/s plus up to 11 m/s from the same weather anomaly that drives warm and cold spells, plus 0.025 m/s for each block of relief, limited to 24 m/s. Rough, high country is windier. The direction is the prevailing direction of the world, swinging slowly about a quarter of a radian either way.

Wind matters in three ways: it chills exposed bodies and animals, it dries soil, and walls stop it.

## The sea

- Sea level is the terrain generator's sea level. All heights for climate are measured from it.
- Maritime places (continentalness below -0.1) have half the daily temperature swing, and the seasonal swing shrinks toward 5 °C on the open sea.
- Open water freezes only where the air is below -2 °C and the surface block is dim (light below 10). Ice melts above 2 °C.

## Seasons

The year has four seasons of `seasonDays` days each: spring, summer, autumn, winter, starting from spring day 1 of year 1. One game day is 24,000 ticks. Time skipped by sleeping counts. The season moves air temperature as described above and sets the sowing and harvest windows in [Agriculture and Livestock](Agriculture-and-Livestock).

## Snow and ice on the ground

The ground is updated from the same local weather.

| Condition at a surface block | Effect |
| --- | --- |
| Air above 2 °C | Snow layers are removed and ice turns to water |
| Air below -2 °C, still water, light below 10 | Water freezes |
| Rain strength above 0.15 and air at or below 0 °C | Snow builds one layer at a time, up to 8 layers, or the snow accumulation height game rule if that is lower |
| Rain strength above 0.15 | Cauldrons and other rain-catching blocks fill |

## Shelter

Three things are measured around a body, an animal or a field.

| Shelter | How it is measured | What it does |
| --- | --- | --- |
| Roof | Any movement-blocking block above the position, leaves included | Stops rain wetting you and stops rain reaching soil. It does not warm the air |
| Walls | In each of the four horizontal directions, a full block at foot or head height within 4 blocks counts. The result is 0, 25, 50, 75 or 100 percent | Cuts wind chill by that share |
| Fire | Fire, lava or a lit campfire within 3 blocks horizontally and 1 vertically | Holds body temperature up, and counts as comfort for livestock |

An unheated hut is as cold as the air outside. It keeps you dry and out of the wind, and that is all.

## What weather does

### To people

Full rules are on [Survival and Physiology](Survival-and-Physiology) and [Clothing and Medical Care](Clothing-and-Medical-Care). The weather inputs are:

- **Wet.** You are wet when you are in water, or when rain (not snow) is falling and you have no roof. Wet clothing keeps 65 percent of its insulation and the air acts 4 °C colder.
- **Wind.** Below 20 °C, the air acts colder by wind speed x 0.35 x the unwalled share, growing to full strength as the air approaches 0 °C. At 0 °C in a 10 m/s wind with no walls, that is 3.5 °C colder.
- **Heat.** Above 26 °C thirst rises, up to 2.5 times the normal rate at 42 °C.
- **Fire.** Near a fire the body does not cool below a normal temperature.

### To crops and soil

Every farmed plot keeps its own soil water between 0 and 1. Rain fills it, heat and wind dry it, a roof keeps rain off it, and a crop grows only while it holds at least 0.25. Frost below -2 °C kills frost-tender crops. The details and the numbers are in [Agriculture and Livestock](Agriculture-and-Livestock).

### To livestock

Pasture is grazeable only between 2 and 34 °C and while the pasture's own soil water is above 0.25. Animals are comfortable when the wind-chilled temperature is above -8 °C (-18 °C for sheep) and the air is below 38 °C, or when a fire is near. Time spent uncomfortable does not count toward growth or products. See [Agriculture and Livestock](Agriculture-and-Livestock).

### To fire

There is no thunder in a regional world, so no lightning.

Rain in Spawn is regional. A front moves across the map, wet country gets more of it and
the lee of a mountain range gets less, and each player sees the weather where they stand.
It can pour on one valley while the next is dry.

Rain does to fire what rain does.

#### Open fire

- Fire that rain can reach is put out. Each time a burning block is checked while rain
  falls on it or on the ground beside it, it has at least a one-in-five chance of going
  out, and the chance rises as the fire ages.
- Fire does not spread onto ground the rain is falling on.
- Fire under a roof is untouched. A roof is any solid block overhead, glass included.
  The cells beside the fire count too: a fire at the edge of a roof is still rained on.
- Snow does not put fire out.

A forest fire started in dry weather burns until it runs out of fuel or the weather
turns. A fire started in the rain barely takes.

#### Campfires

A campfire is a small fire with a little shelter of its own.

- Drizzle does not put it out.
- Steady, heavy rain (more than half the scale) drowns a campfire that is open to the
  sky. It does not happen at once: a campfire left out in a downpour lasts a few
  minutes.
- A campfire under a roof keeps burning whatever the weather.
- In a world without the regional climate profile, any rain does the same.

A drowned campfire has to be relit. If it was boiling water or keeping
you warm, that stops.

#### What to do about it

| You want | Do this |
| --- | --- |
| A fire that survives the night | Put a roof over it, one block wider than the fire on every side |
| Warmth in the rain | Shelter first. A wet body loses a third of its clothing's warmth and feels four degrees colder |
| To clear land by burning | Wait for dry weather; check `/survival climate` |
| To stop a fire | Rain will do it. So will removing the fuel around it |

#### Why it matters for predators

Wolves, bears and lions will not come within six blocks of open fire. A campfire by
the sheepfold protects the flock only while it burns. A rainy night with an unroofed
fire is a night with no fire.

### To vehicles

Off a made road, a wheeled vehicle keeps only 80 percent of its ground speed while rain is falling on it. Snow, mud, sand and ploughed land already carry wheels at 40 percent, so rain on bad ground is severe. Made roads are not affected. See [Transport and Logistics](Transport-and-Logistics).

### To gunpowder

| Weapon | Dry | In falling rain |
| --- | --- | --- |
| Musket | 10 percent misfire, plus 1 point per round of fouling | 50 percent misfire, plus fouling |
| Cannon, bombard, field gun | no weather penalty | 25 percent chance the priming is wet and the charge is spoiled |

Rain counts only where it actually falls: above 0.15 strength, not snow, and no roof overhead. Firing from under cover keeps the priming dry. See [Military Service and Combat](Military-Service-and-Combat) and [Siege Artillery](Siege-Artillery).

### To things left on the ground

Goods lying on the ground weather twice as fast in rain, and one and a half times as fast in the hottest biomes. See [Natural Resources and Mining](Natural-Resources-and-Mining).

## What you see

- Rain and snow are drawn, and heard, from your own position. Two players a few hundred blocks apart can stand in different weather.
- The survival panel shows a line of the form `Air 12.4 C | Wind 6 m/s | Rain`, with `Snow` or `Dry` as the case may be.
- The server sends each player a fresh observation every second. An observation is used for up to 5 seconds and within 192 blocks horizontally and 32 vertically of where it was taken.
- Wind is not drawn. Rain always falls straight.

## Legacy profile

Without Tectonic, the climate is simple.

| Biome base temperature | Air temperature |
| --- | --- |
| 0 or below | -15 °C |
| below 0.5 | 5 °C |
| 0.5 to 1 | 18 °C |
| above 1, up to 1.5 | 32 °C |
| above 1.5 | 42 °C |

Summer adds 7 °C, autumn takes 3 °C, winter takes 12 °C, and each block above Y 100 takes 0.05 °C. Being under any cover moves the air halfway toward 18 °C. Rain is the vanilla global cycle. There is no wind, no soil water and no irrigation.

## Commands

| Command | Who | What it does |
| --- | --- | --- |
| `/survival climate` | any player | Reports the profile, air temperature, whether you are roofed, whether a fire is near, and in a regional world the rain strength, wind speed in m/s, whether it is snowing, and your wind shelter as a share |
| `/survival calendar` | any player | Reports the season, the day of the season and the year |
| `/survival environmentSurvey <x> <z>` | operators | Reports terrain height, relief, baseline temperature, humidity, rain shadow, current rain and temperature at a point, and the mineral and deposit position of its district. This reveals what ordinary prospecting hides |
| `/survival control <actor> climate` | operators | The same report as `/survival climate` for another humanoid |

Other commands also exist in the form `/survival control <actor> ...` for operators directing another humanoid.

## Common mistakes

- **Expecting a roof to be warm.** A roof stops rain and nothing else. Walls stop wind. Only fire and clothing deal with cold air.
- **Building high.** Every block of height costs 0.065 °C. A hilltop 150 blocks above the sea is about 10 °C colder than the shore below it.
- **Farming in the lee of a range.** The valley behind the mountains is dry. Check `/survival climate` over several days before clearing fields, or plan to irrigate.
- **Using `/weather`.** In a regional world the global rain level is held at zero. The command changes nothing you can feel.
- **Trusting powder in the rain.** Half of all musket shots misfire in rain. Fight from under a roof or wait for the front to pass. Fronts move about 840 blocks a day.
- **Standing in snow thinking you are dry and therefore safe.** Snow does not wet you, but it falls only at 0 °C and below, where wind chill is at full strength.
- **Changing `seasonDays` or the Tectonic configuration later.** The world will refuse to load until the saved values are restored.

## Limits of the model

This is a gameplay model, not an atmosphere simulation. Rain shadow and the influence of the sea are approximations. There are no thunderstorms. Crops and animals that catch up after being unloaded assume the roof, the neighbouring water and the ground they have now were there the whole time. Catch-up is limited to one year.
