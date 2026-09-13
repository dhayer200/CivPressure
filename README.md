# CivPressure

CivPressure is a configurable Paper plugin that uses biome-specialized resources,
regional seasons, environmental hazards, and increased mob health to encourage
settlement, migration, trade, cooperation, and conflict.

## Requirements

- Minecraft/Paper 26.2
- Java 25
- Gradle wrapper included

The plugin does not implement random spawning, harder mining, golem nugget
changes, Herobrine, raid-totem changes, or dimension control.

## Build

```bash
./gradlew clean build
```

Output:

```text
build/libs/CivPressure.jar
```

Copy the JAR into the server's `plugins` directory and restart Paper. On first
start, CivPressure creates `plugins/CivPressure/config.yml`. New default keys are
merged into existing configuration files during plugin reloads while existing
values are retained.

## Modules

- Ore redistribution: biome-group ore rates, scans, one-time chunk processing,
  persistent processed-chunk records, and bounded radius jobs.
- Seasons: independent drought, wet, and normal states for every biome group,
  crop effects, farmland moisture, broadcasts, and persisted state.
- Animal flee: configured passive animals move away from non-sneaking players.
- Fall damage: configurable multiplier applied only to fall damage events.
- Slow regeneration: throttled satiated regeneration and successful-sleep
  regeneration.
- Durability pressure: category-based item wear multipliers for tools, weapons,
  armor, shields, bows/crossbows, and utility items.
- Freeze: vanilla freeze ticks in configured cold biomes with light, held-item,
  armor, game-mode, and world protections.
- Wind: telegraphed high-altitude gusts in configured mountain biomes.
- Drowning: scheduled additional air drain with Water Breathing and Respiration
  support.
- Mob buffs: configurable health multipliers for all Paper `Enemy` mobs and
  all remaining `Mob` entities, separate iron-golem settings, golem knockback
  resistance, and cold-biome skeleton-to-stray conversion.
- Giant events: rare, independently configured giant sightings with event
  attributes, pursuit, terrain damage, despawning, announcements, and modest
  loot.
- Mountain polar bears: configured mountain biomes can produce capped polar
  bear spawns alongside natural passive spawns.
- Nightfall: behind-the-scenes escalation where each night is more dangerous
  than the last (scaling with the world's age up to a configurable cap, default
  50 nights). Night hostiles start +15% on the first night and ramp to their
  caps (default +150% health, +50% damage) by the cap; only zombies (and zombie
  variants) also gain speed. Hunting packs spawn near players, and atmospheric
  sounds play under the MASTER sound category so they ignore per-category volume
  sliders. Add your own sound IDs to the configured list. Every night zombie is
  also made a door-breaker (needs Hard difficulty + mobGriefing to actually
  break; iron doors immune). Past a configurable night, hunting packs can arrive
  escorted by a terrain-damaging giant (reuses the giant-events system for
  stats, loot, and despawn). Designed to be the sole source of mob strength (use
  with the mob-buffs module disabled).
- Biome Compass: configurable recipe, marked item, selection GUI, cached biome
  searches, and periodic target updates.

Every module has an `enabled` setting under `modules`. Its detailed values,
lists, intervals, limits, and behavior toggles are in the corresponding root
configuration section.

## Commands

| Command | Description |
| --- | --- |
| `/civ` | Show command usage. |
| `/civ status` | Show module states. |
| `/civ reload` | Reload configuration and scheduled modules. |
| `/civhelp` | Show the player help overview. |
| `/civ ore info` | Show the current biome, group, and ore rates. |
| `/civ ore chunk` | Count ores in the current chunk. |
| `/civ ore radius <radius>` | Count ores in loaded nearby chunks. |
| `/civ ore debug` | Show current chunk processing information. |
| `/civ ore processchunk [force]` | Process the current chunk. |
| `/civ ore unprocesschunk` | Remove its processed flag without changing blocks. |
| `/civ ore process radius <radius> [force]` | Process chunks over multiple ticks. |
| `/civ ore unprocess radius <radius>` | Remove processed flags in a radius. |
| `/civ ore clearprocessed [world\|all]` | Clear persisted processed records. |
| `/civ season status` | Show all biome-group seasons. |
| `/civ drought <group\|all>` | Force drought. |
| `/civ wetseason <group\|all>` | Force wet season. |
| `/civ season end <group\|all>` | End active seasons. |
| `/civ season reload` | Reload season configuration and tasks. |
| `/civ durability status` | Show durability pressure settings. |
| `/civ compass give` | Give yourself a Biome Compass. |
| `/civ giant status` | Show giant-event state and loaded counts. |
| `/civ giant spawn` | Spawn a test event giant nearby. |
| `/civ giant clear [world\|all]` | Remove loaded event giants. |
| `/civ nightfall status` | Show current night, escalation percent, and mob multipliers. |

## Permissions

| Permission | Default |
| --- | --- |
| `civ.admin` | op |
| `civ.reload` | op |
| `civ.season` | op |
| `civ.ore` | op |
| `civ.ore.scan` | op |
| `civ.ore.process` | op |
| `civ.durability` | op |
| `civ.compass.give` | op |
| `civ.giant.admin` | op |
| `civ.nightfall` | op |
| `civ.help` | true |

## Biome Groups

- Plains: plains, sunflower plains, meadow, cherry grove, flower forest,
  forest, birch forest, old-growth birch forest.
- Iron: taiga, snowy taiga, old-growth pine/spruce taiga, savanna, savanna
  plateau, windswept savanna.
- Gold: desert and all badlands variants.
- Lapis: dark forest, swamp, mangrove swamp.
- Redstone: jungle, bamboo jungle, sparse jungle.
- Emerald: jagged/stony peaks, windswept hills/gravelly hills/forest, grove.
- Diamond: frozen peaks, snowy slopes/plains, ice spikes, frozen river/ocean,
  deep frozen ocean, snowy beach.
- Ungrouped: mushroom fields, dripstone caves, lush caves, deep dark, sulfur
  caves.

Water and coastal biomes use Plains ore rates at or above Y=0 and Ungrouped
rates below Y=0. Lush caves are always Ungrouped.

## Ore Rates

Percent of vanilla:

| Group | Iron | Gold | Lapis | Redstone | Emerald | Diamond |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Plains | 85 | 85 | 50 | 40 | 20 | 20 |
| Iron | 140 | 70 | 50 | 40 | 20 | 20 |
| Gold | 80 | 140 | 50 | 40 | 20 | 20 |
| Lapis | 80 | 70 | 120 | 40 | 20 | 20 |
| Redstone | 80 | 70 | 50 | 120 | 20 | 20 |
| Emerald | 70 | 60 | 50 | 40 | 200 | 80 |
| Diamond | 70 | 60 | 50 | 40 | 80 | 200 |
| Ungrouped | 80 | 80 | 80 | 80 | 60 | 60 |

Coal and copper remain at 100% and are never redistributed.

## Testing Workflow

1. Build with `./gradlew clean build`.
2. Install `build/libs/CivPressure.jar` and restart Paper.
3. Confirm `/plugins`, `/help civ`, `/civ status`, and `/civhelp`.
4. Test `/civ ore info`, `/civ ore chunk`, and `/civ ore radius 1`.
5. Use a disposable fresh area for ore processing tests.
6. Test forced seasons near crops and farmland.
7. Test each survival module independently by toggling the others off.
8. Craft or give a Biome Compass, select every group, and verify its target.
9. Spawn fresh mobs and reload existing chunks to verify health is applied once.

Runtime state is stored in `processed_chunks.dat` and `seasons.dat` under the
plugin data directory.
