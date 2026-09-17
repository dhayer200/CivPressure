# CivPressure Build Specification

Send Codex this. It’s written to force it to build from scratch, prove basics first, then add modules one-by-one instead of hallucinating a giant broken plugin.

Build a brand-new Minecraft Paper plugin from scratch.

Plugin name: CivPressure
Plugin version: 26.3
Minecraft/Paper target version: 26.3
Java: 25
Build tool: Gradle
Main package: com.deep.civpressure

Use the Paper Maven repository:
https://repo.papermc.io/repository/maven-public/

Use the Paper 26.3 API dependency:
io.papermc.paper:paper-api:26.3.build.+

Configure the Gradle Java toolchain for Java 25.

Do not target Paper 1.21.4.
Do not use Paper API 1.21.4 dependencies.
Paper 26.1+ no longer supports obfuscated plugin output, so do not configure or run reobfJar.

Core goal:
CivPressure is a biome-based civilization pressure plugin. The goal is to make geography, biome specialization, food instability, dangerous premium biomes, and survival pressure naturally push players toward trade, settlement, migration, cooperation, and conflict.

Do not add random RPG features. Every mechanic should support biome-based civilization gameplay.

Important exclusions:

* Do NOT implement random spawn.
* Do NOT implement harder mining.
* Do NOT implement golem nugget nerf.
* Do NOT implement Herobrine.
* Do NOT implement no-raid-totems.
* Do NOT implement Nether/dimension control.
    Those are handled separately by other plugins/datapacks.

Build this from scratch. Do not assume any old repository exists.

Before implementing features, first create a minimal working plugin that loads, registers commands, and proves command wiring.

PHASE 1 — SCAFFOLD AND COMMAND REGISTRATION

Create a Gradle Java project.

Required files:

* settings.gradle
* build.gradle
* src/main/resources/plugin.yml
* src/main/resources/config.yml
* src/main/java/com/deep/civpressure/CivPressurePlugin.java

Use standard plugin.yml, not only paper-plugin.yml, because command registration must work reliably.

plugin.yml must include:

* version: '26.3'
* api-version: '26.3'

plugin.yml must declare:

* /civ
* /civhelp

Permissions:

* civ.admin default op
* civ.reload default op
* civ.season default op
* civ.ore default op
* civ.ore.scan default op
* civ.ore.process default op
* civ.compass.give default op
* civ.help default true

In onEnable:

* saveDefaultConfig()
* load ConfigManager
* register /civ executor + tab completer
* register /civhelp executor
* log clear startup messages:
    * Config loaded
    * Registered command: /civ
    * Registered command: /civhelp
    * CivPressure enabled

If getCommand(“civ”) or getCommand(“civhelp”) returns null, log a SEVERE error.

Before adding any complex mechanics, make these commands work:

* /civ
* /civ status
* /civ reload
* /civhelp

/civ with no args should show usage.
/civ status should list all modules and whether they are enabled.
/civhelp should show a player help overview.

After Phase 1, run:
./gradlew clean build

Then verify the jar contains:
jar tf build/libs/CivPressure.jar | grep -E “plugin.yml|config.yml|CivPressurePlugin”

Do not continue to Phase 2 until Phase 1 compiles and command registration is correct.

PHASE 2 — CONFIG AND BIOME GROUPS

Create:

* ConfigManager
* BiomeGroupRegistry

All modules should be toggleable in config.yml:

modules:
ore-redistribution:
enabled: true
seasons:
enabled: true
animal-flee:
enabled: true
fall-damage:
enabled: true
slow-regen:
enabled: true
freeze:
enabled: true
wind:
enabled: true
drowning:
enabled: true
biome-compass:
enabled: true
mob-buffs:
enabled: true

Biome groups:

Plains:

* plains
* sunflower_plains
* meadow
* cherry_grove
* flower_forest
* forest
* birch_forest
* old_growth_birch_forest
* dappled_forest

Iron:

* taiga
* snowy_taiga
* old_growth_pine_taiga
* old_growth_spruce_taiga
* savanna
* savanna_plateau
* windswept_savanna

Gold:

* desert
* badlands
* eroded_badlands
* wooded_badlands

Lapis:

* dark_forest
* swamp
* mangrove_swamp

Redstone:

* jungle
* bamboo_jungle
* sparse_jungle

Emerald:

* jagged_peaks
* stony_peaks
* windswept_hills
* windswept_gravelly_hills
* windswept_forest
* grove

Diamond:

* frozen_peaks
* snowy_slopes
* snowy_plains
* ice_spikes
* frozen_river
* frozen_ocean
* deep_frozen_ocean
* snowy_beach

Ungrouped:

* mushroom_fields
* dripstone_caves
* lush_caves
* deep_dark

Important:
Lush caves are Ungrouped, NOT Redstone.

Water/coastal biomes:

* ocean
* deep_ocean
* warm_ocean
* lukewarm_ocean
* cold_ocean
* frozen_ocean
* deep_lukewarm_ocean
* deep_cold_ocean
* deep_frozen_ocean
* river
* frozen_river
* beach
* snowy_beach
* stony_shore

Water biome ore rule:

* Above Y=0, use Plains rates.
* Below Y=0, use Ungrouped rates.

Add /civ ore info:

* Shows current biome.
* Shows resolved biome group.
* Shows ore rates for current location.

Do not continue until /civ ore info works.

PHASE 3 — ORE RATES AND ORE SCANNING

Implement OreRates.

Coal and copper are always 100% vanilla and untouched.

Ore rate table, percentage of vanilla:

Plains:
iron 85, gold 85, lapis 50, redstone 40, emerald 20, diamond 20

Iron:
iron 140, gold 70, lapis 50, redstone 40, emerald 20, diamond 20

Gold:
iron 80, gold 140, lapis 50, redstone 40, emerald 20, diamond 20

Lapis:
iron 80, gold 70, lapis 120, redstone 40, emerald 20, diamond 20

Redstone:
iron 80, gold 70, lapis 50, redstone 120, emerald 20, diamond 20

Emerald:
iron 70, gold 60, lapis 50, redstone 40, emerald 200, diamond 80

Diamond:
iron 70, gold 60, lapis 50, redstone 40, emerald 80, diamond 200

Ungrouped:
iron 80, gold 80, lapis 80, redstone 80, emerald 60, diamond 60

Add ore scan commands:

* /civ ore chunk
* /civ ore radius 

These should count:

* coal
* copper
* iron
* gold
* lapis
* redstone
* emerald
* diamond

Include both normal and deepslate variants.

Add config:
ore-redistribution:
max-scan-radius: 5

Radius scan:

* radius 1 = 3x3 chunks
* radius 2 = 5x5 chunks
* enforce max-scan-radius
* do not allow huge laggy scans

Do not continue until /civ ore chunk and /civ ore radius work.

PHASE 4 — ORE REDISTRIBUTION

Implement plugin-side ore redistribution for new chunks.

Requirements:

* Only process Overworld chunks.
* Process chunks once.
* Do not touch player-placed ores after initial processing.
* Coal and copper are untouched.
* For rates below 100%, probabilistically remove ore blocks and replace them with stone/deepslate depending on Y/material context.
* For rates above 100%, add extra ore blocks near existing ore blocks in valid stone/deepslate positions.
* Keep extra ore generation natural-looking; do not create artificial cubes.

Important:
Use the correct Paper 26.3 event/hook for post-generation chunk processing. Verify the API. If ChunkPopulateEvent is unavailable or deprecated, use the correct modern Paper equivalent. The ore processor must run after vanilla ores generate.

Add commands:

* /civ ore debug
    Shows current chunk coordinates, world, processed state, biome, resolved group.
* /civ ore processchunk
    Processes current chunk if unprocessed.
* /civ ore processchunk force
    Force-processes current chunk.
* /civ ore unprocesschunk
    Removes processed flag from current chunk without changing blocks.
* /civ ore clearprocessed [world]
    Clears processed chunk records for a world or all worlds.
* /civ ore process radius  [force]
    Processes chunks in radius. Spread work over multiple ticks to avoid lag.
* /civ ore unprocess radius 
    Removes processed flags in radius.

Add optional dev command:

* /civ ore testarea 
    Finds a far-away fresh location, teleports admin there, generates chunks in radius, processes them once, then prints ore totals.
    Each run should use a new far-away coordinate.

Config:
ore-redistribution:
max-scan-radius: 5
max-process-radius: 5
testarea-start-radius: 10000
testarea-step: 5000

PHASE 5 — SEASONS

Implement independent drought/wet seasons per biome group.

Each group independently has:

* NORMAL
* DROUGHT
* WET

Groups:

* plains
* iron
* gold
* lapis
* redstone
* emerald
* diamond
* ungrouped

Season rules:

* Rolls once per Minecraft day per group.
* Drought blocks 75% of crop growth ticks.
* Wet season gives 40% bonus growth chance.
* Cold biomes are more impacted by season crop effects.
* Deserts should NOT be more drought-prone than everyone else.
* Gold/desert group should use same drought/wet odds as normal groups.
* Do not exempt premium biomes by default.
* Season start/end should broadcast to chat.
* Active seasons should have rising end chance the longer they last.
* Default end chance should start around 30% and climb to near-certain by day 16.

Commands:

* /civ season status
* /civ drought <group|all>
* /civ wetseason <group|all>
* /civ season end <group|all>
* /civ season reload

Persist season state in seasons.dat.

Also implement farmland behavior:

* Drought dries farmland around players in affected groups.
* Wet season keeps farmland saturated in affected groups.
* Use efficient scheduled checks; do not scan huge areas.

PHASE 6 — SURVIVAL HAZARDS

Implement these modules, all toggleable and configurable.

1. Animal flee

* Passive animals flee from non-sneaking players.
* Default radius: 8 blocks.
* Sneaking players do not trigger flee.
* Leashed/ridden animals do not flee.
* Use nearby-entity scans around players, not full-world entity scans.

Affected animals:

* chicken
* cow
* mooshroom
* pig
* sheep
* rabbit
* horse
* donkey
* llama

2. Fall damage

* 1.5x vanilla fall damage.
* Only modify actual fall damage events.

3. Slow regen + sleep regen

* Natural health regeneration is 4x slower.
* Implement by throttling SATIATED EntityRegainHealthEvent.
* Use a Map<UUID, Integer> counter, not player metadata.
* Sleeping grants Regeneration I for 2 minutes.
* Apply on successful wake from bed.
* Do not stack into higher amplifier.

4. Freeze in cold biomes

* Players freeze in cold/frozen/mountain biomes when lacking light.
* Safe if holding torch, soul torch, lantern, or soul lantern in main hand OR offhand.
* Safe if block light is at least 8.
* Sunlight alone should not count.
* Full leather armor protects by default; configurable.
* Use vanilla freeze ticks if possible.
* Gradual freeze, not instant damage.

5. Wind

* High-altitude wind in mountain/cold biomes.
* Applies above Y=150.
* Strength increases with height, peaking around Y=256.
* Sneaking reduces push by 70%.
* Telegraph gust with sound/particles around 15 ticks before push.
* Wind can push players off cliffs; intended.

6. Faster drowning

* Implement as scheduled task, not PlayerMoveEvent.
* Submerged players lose air faster.
* Respect Water Breathing.
* Respect Respiration if practical by reducing extra drain.
* Do not instantly kill players.

PHASE 7 — MOB BUFFS

Implement mob health buffs.

Rules:

* Hostile mobs have 1.5x max health.
* Passive animals have 2.25x max health.
* Iron golems have 2x max health.
* Iron golems have full knockback resistance.
* Hostile damage remains vanilla. Do NOT increase hostile damage.
* In frozen/snowy biomes, skeletons spawn as strays if configured.
* Mark buffed mobs with PersistentDataContainer so they are not multiplied repeatedly.
* Verify correct Paper 26.3 Attribute enum names before coding.

PHASE 8 — BIOME COMPASS

Implement craftable biome compass.

Recipe:

* 8 saplings around 1 compass.
* Any sapling type works.
* Result: custom Biome Compass item with name/lore and PersistentDataContainer metadata.

Behavior:

* Right-click opens GUI.
* GUI lets player choose:
    * Plains
    * Iron
    * Gold
    * Lapis
    * Redstone
    * Emerald
    * Diamond
    * Ungrouped
* Store selected group on the compass item.
* Compass points toward nearest biome of that group within configurable radius.
* Default search radius: 6000 blocks.
* If no biome found, tell player.
* Compass should update as player moves.

Important implementation details:

* Use plugin-based NamespacedKey creation.
* Do not use hardcoded NamespacedKey constructors that may break.
* Use a robust custom InventoryHolder or equivalent marker for the GUI.
* Do not rely on colored inventory title equality.
* Run biome search asynchronously if expensive, then return to main thread to set compass target.

Command:

* /civ compass give 

PHASE 9 — FINAL CONFIG

config.yml should include all module toggles and settings.

Include these defaults:

ore-redistribution:
max-scan-radius: 5
max-process-radius: 5
testarea-start-radius: 10000
testarea-step: 5000

seasons:
drought-chance: 0.12
wet-chance: 0.12
drought-block-chance: 0.75
drought-block-chance-cold: 0.90
wet-bonus-chance: 0.40
wet-bonus-chance-cold: 0.60
farmland-check-radius: 8
farmland-check-interval-ticks: 100

animal-flee:
radius: 8.0
speed: 0.35
interval-ticks: 10

fall-damage:
multiplier: 1.5

slow-regen:
slow-factor: 4
sleep-regen-duration-ticks: 2400
sleep-regen-amplifier: 0

freeze:
light-threshold: 8
leather-protects: true
freeze-ticks-per-second: 35

wind:
min-y: 150
peak-y: 256
max-strength: 1.2
sneak-reduce: 0.70
gust-interval-ticks: 100
telegraph-ticks: 15

drowning:
air-drain-multiplier: 2.0
interval-ticks: 20

biome-compass:
search-radius: 6000
update-interval-ticks: 40

mob-buffs:
hostile-health-multiplier: 1.5
passive-health-multiplier: 2.25
golem-health-multiplier: 2.0
cold-skeletons-as-strays: true

PHASE 10 — README

Create README.md explaining:

* Setup
* Required Paper version
* Java version
* Build command
* Modules
* Commands
* Permissions
* Biome groups
* Ore table
* Testing workflow

Build command:
./gradlew clean build

Jar output:
build/libs/CivPressure.jar

PHASE 11 — BUILD AND VERIFY

Run:
./gradlew clean build

Fix all compile errors.

Then verify jar contents:
jar tf build/libs/CivPressure.jar | grep -E “plugin.yml|config.yml|CivPressurePlugin”

Expected:

* plugin.yml exists at jar root.
* config.yml exists at jar root.
* CivPressurePlugin.class exists.

Final acceptance checklist:

* Plugin appears in /plugins.
* /help civ shows CivPressure commands.
* /civ works.
* /civ status works.
* /civhelp works.
* /civ ore info works.
* /civ ore chunk works.
* /civ ore radius 1 works.
* /civ season status works.
* Biome compass recipe works.
* Biome compass GUI opens.
* Mob health buffs apply once.
* Freeze works in cold biomes.
* Wind works above Y=150.
* Animal flee works.
* Fall damage multiplier works.
* Drowning drains faster.
* No random spawn exists.
* No harder mining exists.
* Lush caves are Ungrouped, not Redstone.

Start Codex with that, and don’t let it jump ahead. The key is: Phase 1 must work first — /civ status has to work before it touches ore generation.

FUTURE OPTIONAL MODULE — GIANT EVENTS

Do not implement until the existing core modules pass in-game review.

Purpose:
Rare giant sightings create civilization-scale danger and encourage cooperation,
scouting, walls, militias, trade, and alliances. Giants must be rare world
events, not normal farmable mob spawns.

Suggested configuration:

modules:
  giant-events:
    enabled: true

giant-events:
  overworld-only: true
  spawn-check-interval-ticks: 24000
  spawn-chance-per-check: 0.003
  max-giants-per-world: 1
  min-distance-from-spawn: 1000
  min-distance-from-players: 80
  max-distance-from-players: 180
  require-night: true
  despawn-after-ticks: 36000
  health: 160.0
  damage: 12.0
  movement-speed: 0.23
  knockback-resistance: 0.75
  announce-spawn: true
  announce-exact-coordinates: false
  block-damage: false

Allowed biome groups:

* plains
* iron
* gold
* emerald
* diamond
* ungrouped

Behavior:

* Roll a rare chance on each spawn check.
* Pick an eligible online player and a safe location between the configured
  minimum and maximum player distance.
* Enforce configured world, night, biome-group, spawn-distance, and per-world
  caps.
* Mark giants with PersistentDataContainer.
* Do not grief blocks by default.
* Despawn after the configured lifetime unless later engagement rules say
  otherwise.
* Announce vague directional sightings, not coordinates by default.
* Add /civ giant spawn, /civ giant clear, and /civ giant status.
* Use modest configurable iron, gold, emerald, rare diamond, trophy, and XP
  rewards. Giants must not become the best diamond source.

FUTURE OPTIONAL MODULE — NIGHTFALL (ESCALATING NIGHTS)

Do not implement until the existing core modules pass in-game review.

Purpose:
Fear drives cooperation. Nights get progressively more dangerous the longer a
world has existed, up to a cap, pushing players to build defenses, group up,
and post guards. Everything is behind the scenes — there is no meter or UI, just
mobs that grow stronger, hunting packs, and creepy atmospheric sound.

Escalation clock:

* Measured by the world's age in days (fullTime / 24000), so it persists across
  restarts with no extra state file.
* progress = min(nightsSurvived, cap-nights) / cap-nights, in [0, 1].
* Default cap-nights: 50. Escalation is monotonic and never resets.

Suggested configuration:

modules:
  nightfall:
    enabled: false

nightfall:
  overworld-only: true
  cap-nights: 50
  tick-interval-ticks: 200
  mob-strength:
    affect-natural-spawns: true
    restore-when-disabled: true
    first-night-bonus: 0.15
    max-health-bonus: 1.5
    max-damage-bonus: 0.5
    max-speed-bonus: 0.15   # zombies only
    zombies-break-doors: true   # needs Hard + mobGriefing to actually break
  spawns:
    enabled: true
    mob-types: [zombie, skeleton, spider, husk, stray]
    min-pack-size: 2   # packs spawn from the first night
    max-pack-size: 6
    max-nearby-per-player: 24
  sounds:
    enabled: true
    category: master
    night-curve: true
    list: [ambient.cave, entity.warden.heartbeat, ...]
  giant:
    enabled: true
    first-night: 15         # earliest night a giant escorts a pack
    chance-per-pack: 0.15
    max-per-world: 1
    announce: false

Behavior:

* Only acts at night in eligible worlds for survival/adventure players.
* Strength buffs are AttributeModifiers (MULTIPLY_SCALAR_1) keyed by the plugin.
  Nightfall is intended to be the sole source of mob strength (run it with the
  mob-buffs module disabled). Health and damage start at first-night-bonus on
  the first night and ramp to their caps; speed has no first-night floor and is
  applied only to zombies (and zombie variants). Buffs are applied to hostiles
  that spawn at night and to spawned packs, and removed from loaded entities
  when the module is disabled if restore-when-disabled is true.
* Hunting packs spawn near players on the surface, scale in size and frequency
  with progress, are capped per player, and target the nearest player.
* Sounds play under the configured category (MASTER by default) so per-category
  volume sliders do not silence them; frequency scales with progress and, if
  night-curve is on, peaks toward midnight. Any vanilla sound id is accepted and
  server operators can add their own.
* Add /civ nightfall status (civ.nightfall) showing the current night,
  escalation percent, and effective mob multipliers per world.
