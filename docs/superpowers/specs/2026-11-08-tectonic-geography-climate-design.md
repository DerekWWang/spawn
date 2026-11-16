# Tectonic geography, climate and resource integration

Status: draft for user review. No product implementation or runtime dependency
installation has started. The existing skills/resource work remains in place.

## Intended result

The user wants Tectonic's geography to shape survival, technological development
and society. Success means that choosing a home, locating supplies, organizing
work and moving goods have different consequences in different regions. A
mountain mining settlement should face different practical problems from a wet
farming valley or an isolated island.

Working scope assumption, pending the user's requested preference: first connect
terrain, regional weather and resources to the existing survival systems. New
technology chains and autonomous settlement/trading AI are subsequent milestones
on the same foundation. This does not discard the broader goal.

## Verified compatibility and source findings

- This project targets Minecraft 26.3 and NeoForge 26.3.0.77.
- Modrinth lists a stable Tectonic 3.0.32 NeoForge build for 26.3, published
  October 25, 2026. The newer 3.1.0 beta2 is a beta; do not silently select it.
- Prefer the official mod distribution for its configuration and loader
  integration. Do not install both its terrain datapack and mod distribution.
- Modrinth declares Lithostitched as a required project dependency. Its current
  26.3 NeoForge release is 2.0.6. The exact Tectonic artifact also declares
  Apollib and bundles Apollib 1.2.5 through NeoForge jar-in-jar metadata.
- The downloaded reference artifact's SHA-512 matches the official release
  metadata. It is under ignored `reference/tectonic/`, outside the runtime.
- Tectonic's documented scope includes continents, deep oceans, high mountains,
  underground rivers, canyons, wetlands and biome climate-noise settings. It is
  terrain generation, not a ready-made simulation of regional weather,
  geochemical deposits, technology or societies.
- The installed game's `NoiseBasedChunkGenerator.getBaseHeight(...)` accepts a
  height accessor and RandomState. Its climate sampler exposes temperature,
  humidity and continental terrain parameters. These are candidate integration
  APIs; their behavior with the actual pinned Tectonic/Lithostitched stack must
  be verified in implementation, not assumed from signatures.

The repository's default GitHub branch and wiki can lag current artifacts.
Dependency verification must inspect the pinned artifact as well as web metadata.

## Approaches considered

1. **Shared terrain and climate foundation, recommended.** Tectonic owns the
   landforms; Spawn samples that effective generator and applies geology,
   ecology and regional weather through shared services. It provides the
   environmental causes that later technology and societies can respond to.
2. **Compatibility-only integration.** Install Tectonic and leave the current
   survival/resource rules mostly intact. Smaller, but fixed ore elevations,
   global rain and inconsistent crop/body climates remain.
3. **Complete atmosphere, hydrology, technology and society simulation now.**
   Closest to the full aspiration, but several major independent systems would
   be coupled before any of their consequences were validated. Decompose it
   into milestones even if the user chooses to pursue all of them.

## Proposed first milestone

### 1. Explicit Tectonic world profile

Pin Tectonic 3.0.32 and the required compatible dependency set in a reproducible
development/runtime manifest. Verify NeoForge loader constraints and bundled
libraries. Keep upstream attribution and licenses. No network downloads during
world loading and no copied fork of Tectonic's noise-router data.

Offer a documented Tectonic realism profile for new worlds. Existing worlds
require an explicit migration decision; never silently swap their generator or
rewrite generated chunks. The adapter may support vanilla terrain for tests or
an explicit fallback, but an absent Tectonic installation must not be presented
as an active Tectonic world.

Leave Tectonic's optional increased-height setting off by default. Sample the
effective world's actual minimum, maximum and sea level so enabling it does not
produce out-of-bounds deposits or fixed-height assumptions. Disable generated
river lanterns in the realism profile; retain naturally navigable rivers.

### 2. Deterministic terrain sampling

Introduce one immutable terrain description: elevation relative to sea level,
local relief/slope, continentalness, climate-noise temperature/humidity, and
coastal or river context. Sample the final configured generator through a
bounded, world-scoped cache. Do not synchronously generate neighboring chunks
from a feature or perform a large terrain scan on every actor tick.

Keep baseline terrain/climate sampling independent of current weather, calendar,
player-built structures and chunk decoration order. Queries used for generation
must give the same result before and after neighboring chunks are generated.

### 3. Geography-conditioned resources

Replace absolute ore-center elevations with deterministic geological anchors
derived from the unmodified generator at a district reference point. A body
remains one coherent inclined volume across chunks; it must not follow every
surface column as a uniformly mineralized blanket.

Use geological provinces initially 2,048 blocks wide to influence the existing
256-block copper, tin, iron and coal districts and their quarry distributions.
These are explicit tuning parameters saved in the world profile. Relief and terrain setting change probabilities,
depth and exposure, rather than guaranteeing ore in every mountain. Preserve
barren areas and variable grades. Sedimentary basin settings favor coal and
some iron/limestone associations; intrusive settings can favor tin-bearing
granite. These are declared geological approximations, not inferred tectonic
plates. Unsupported settings retain useful ordinary rock rather than fake ore.

Rivers and wet margins influence clay and sediment patches. Slope and stable
climate affect vegetation density and species. Rocky exposures carry debris;
steep slopes and cold heights limit agriculture. Mineral bodies remain finite,
and placed/recovered blocks retain the existing extraction-practice exclusions.

### 4. One climate model for every consumer

Replace the separate biome-only temperature rules in `SurvivalEvents`,
`SeasonalCrops` and `NaturalResourceGeneration` with one shared service.
It combines the baseline terrain climate, altitude, season, daily cycle and
regional weather. Apply elevation cooling once, and document the chosen
vertical scale for Minecraft's compressed mountains.

Model coherent moving wet/dry weather regions, precipitation intensity, wind and
temperature anomalies. Neighboring positions transition smoothly; weather must
not restart at chunk borders, player login, sleeping or server reload. Windward
and leeward terrain may modify long-term moisture using a bounded terrain sample.
This is an explicit approximation, not a numerical atmosphere solver.

World generation uses long-term climate only. Runtime simulation uses the current
weather. Authoritative server values feed players, fake players and NPCs alike.
Client rain/snow, sound and reported conditions must agree with local server
weather; vanilla's global rain flag alone cannot deliver that result.

### 5. Practical environmental consequences

- **People:** altitude, wind and wet clothing affect heat loss and water needs.
  A roof blocks precipitation; enclosing a room or making a windbreak has a
  different effect from simply standing under leaves. Existing wool, fiber,
  fire and shelters retain their physical roles.
- **Farming:** track bounded soil-water history at cultivated plots. Rain and
  nearby water recharge it; heat, exposure and wind dry it. Growth and frost
  use the same local climate as bodies. A manual irrigation action can consume
  actual stored pot water, giving water hauling a useful role without granting
  free crops or resetting their biological clocks.
- **Livestock:** grazing, water availability and exposure consume the same
  climate information, retaining real feed/water/product conservation.
- **Work and supplies:** expose conditions relevant to choosing a dry workplace
  and preserving supplies. Keep existing station recipes and fuel costs in this
  milestone; exposure-dependent fuel efficiency belongs to the later technology
  milestone and must preserve actual fuel accounting.
- **Discovery:** provide observed local conditions and bounded nearby terrain
  information to players/controllers. Preserve visible-evidence prospecting;
  do not disclose unseen deposits, global resource maps or perfect forecasts.

Regional scarcity and travel terrain give existing boats, bridges, clothing,
food storage and coordinated production practical advantages. Do not claim that
players will form societies automatically. That behavior must be demonstrated
with people/controllers, or implemented as a later explicit AI system.

## Connection to later technology and society milestones

Build capability chains in response to demonstrated environmental bottlenecks:
water collection and irrigation, insulated shelter/heating, preservation and
seasonal reserves, haulage/river transport, and deeper or drained mines. Each
requires physical materials, labor and maintenance. Avoid rewards for simply
clicking a research button or forced permanent professions.

Autonomous settlements need additional explicit systems: shared work planning,
stockpile reservations, hauling, ownership/access, exchange, transport risk and
failure recovery. Those systems must use the same finite resources and actor
capacities as players. They are not supplied by Tectonic or this integration
alone. Their scope remains the outstanding preference question.

## Persistence, performance and compatibility

Persist a versioned environment configuration for each world. Specify climate
epoch, generator/profile identity and tuning so updates do not silently move
existing deposits or reset weather/soil histories. Retain existing actor saves
and skills. Bounded soil catch-up must evaluate elapsed conditions without
performing unloaded manual labor or granting duplicate harvests.

Reuse bounded terrain caches across ecology, weather and resource consumers.
Normal actor updates should be constant work after sampling. Generation adapters
write only their own chunk. Reloads must invalidate configuration-dependent
caches deliberately. Standard and increased-height worlds need explicit bounds
tests. Unsupported custom generators need a clear supported fallback or
diagnostic, not a crash or a false claim of full climate support.

## Acceptance evidence

1. Boot the exact dependency stack on this project's NeoForge target; prove
   that Tectonic actually changes generated terrain and that old fantasy
   structure/ore exclusions remain effective.
2. Test negative coordinates, borders, reversed generation order, height bounds,
   body continuity and resource conservation. Generation must be independent of
   weather and the date chunks were generated.
3. Test temperature ordering, wind/wetness/shelter behavior, smooth weather
   transitions, drought/rain soil response, frost and real irrigation costs.
   Sleeping/reloading must not mint water, crops, fuel or manual XP.
4. Survey three fixed Tectonic seeds across mountains, valleys, coasts, wetlands
   and dry regions. Record actual heights, exposed/buried deposits, vegetation,
   useful early supplies and safe routes. Flag impossible starts or resource
   availability claims unsupported by the sample.
5. Run controlled equal-work scenarios for people, fake players and NPCs in
   contrasting regions. Demonstrate that clothing, shelter, stored water and
   transport change outcomes rather than adding unavoidable damage everywhere.
6. Run the complete regression suite, asset checks and performance measurements.
   Capture two terrain angles, local weather, agricultural response and actual
   collaboration through Qube dedicated replay; inspect every contact sheet.

## Research references

- [Official Tectonic project](https://modrinth.com/datapack/tectonic)
- [Stable target release](https://modrinth.com/datapack/tectonic/version/tzhzB8SC)
- [Official release metadata](https://api.modrinth.com/v2/version/tzhzB8SC)
- [Official configuration wiki](https://github.com/Apollounknowndev/tectonic/wiki/Config)
- [Tectonic source](https://github.com/Apollounknowndev/tectonic)
- [Lithostitched target release](https://modrinth.com/mod/lithostitched/version/sQznZoKs)

Scientific tuning and integration behavior require validation during
implementation. No claim of a full meteorological, hydrological or tectonic
simulation is made by this draft.
