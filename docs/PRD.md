# CivPressure — Product Requirements Document

**Audience:** research / design team  
**Product:** CivPressure, a Paper 26.3 Minecraft plugin  
**Repo:** https://github.com/dhayer200/CivPressure  
**Status:** live playtest. Linear Nightfall is shipped. Exponential escalation is explicitly deferred.  
**Date:** 18 September 2026

This document is a briefing, not a feature request list. Read it as the current product thesis, what is already on the server, and the questions we want ideas on. Propose systems that make *civilization* feel necessary. Do not propose generic RPG extras.

---

## 1. Why this plugin exists

Vanilla Minecraft is excellent at *surviving the first night* and weak at *making a lasting society*. After iron tools, the world stops pushing back. Players can turtle in one lucky biome, ignore their neighbors, and treat the map as a warehouse.

CivPressure is meant to reverse that. Geography, food, nights, and wear should make it cheaper to settle, trade, migrate, and fight than to live as a lone self-sufficient hermit.

The design sentence:

> The land is specialized, the nights get harder, and staying put has a cost. Players should *need* other people and other biomes.

If a proposed idea does not make that sentence truer, it does not belong here.

---

## 2. Design principles

1. **Pressure, not punishment.** The world should raise the cost of isolation and complacency. It should not randomly delete progress.
2. **Biome as policy.** Different regions should be good at different things. No region should be a dead end. A bad seed must still be playable.
3. **Nights are the clock.** Calendar time is the main difficulty lever. We are on a *linear* ramp to day 200. Do not assume exponential, logarithmic, or local-difficulty cloning unless you are arguing for the next phase.
4. **Settlements are targets.** Beds and villages are the things the night cares about. Lone explorers in the wild should not be treated like a town.
5. **Configurable, module-shaped.** Every system is a toggle plus a config table. Ideas should be describable as a module a playtester can turn off.
6. **Vanilla-readable.** Prefer amplifying things Minecraft already taught players (ores, seasons-ish weather, village sieges, skeleton traps, sleep) over new UI, classes, or currencies.
7. **No feature tourism.** If another plugin or datapack already owns a problem (random spawn, nether control, raids, Herobrine), we do not reimplement it.

---

## 3. What is live right now

Paper **26.3**, Java 25. Players see `/civhelp`. Ops use `/civ`.

### 3.1 Module board (current playtest)

| Module | On? | What it actually does |
| --- | --- | --- |
| Ore redistribution | On | Biased or flat ore rates by biome group. Live profile is **flat 90%** of vanilla everywhere. The specialized “biased” table exists but is commented out. |
| Seasons | **Off** | Per-region drought / wet / normal. Crops and farmland moisture. Ready to turn on later. |
| Fall damage | On | ×1.2 fall damage, players only. |
| Slow regen | On | Natural regen ×1.5 slower. Safe sleep grants a short Regeneration burst. |
| Durability pressure | On | Modest extra wear (tools/shields/utility ×1.025, weapons/armor/bows ×1.05). Mending still works. Unbreaking still respected. |
| Biome compass | **Off** | Craftable compass that points at a chosen biome group. Recipe and GUI exist. |
| Mob buffs | On | Passives ×1.5 health. Hostiles are **not** buffed here — Nightfall owns hostile strength. Golems at vanilla health with full knockback resist. |
| Mountain polar bears | On | Extra polar bears in mountain/snow biomes from y=60. |
| Nightfall | On | Linear 200-day night clock. See §4. |
| Skeleton traps | On | Storm lightning can spawn extra trapped skeleton-horse hordes. 0% night 1 → 25% at day 200. |

Giants are **not** a roaming world event anymore. They only appear as a Nightfall *siege boss* after night 100.

### 3.2 Biome groups (the map’s “countries”)

These are the economic regions. Display names are what players should think in.

| Group | Lands | Intended specialty (biased profile) |
| --- | --- | --- |
| Plains | Plains, forests, meadow, cherry, flower, birch, dappled forest | Balanced / slightly poor |
| Iron | Taigas and savannas | Iron |
| Gold | Deserts and badlands | Gold |
| Lapis | Swamps and dark forests | Lapis |
| Redstone | Jungles | Redstone |
| Emerald | Windswept highlands / peaks / grove | Emerald, some diamond |
| Diamond | Snowy peaks and frozen lands | Diamond, some emerald |
| Ungrouped | Caves, mushroom, deep dark, sulfur caves | Mild everything, no jackpot |

**Invariant:** even in the biased table, every ore still generates in every group. The lowest historical floors were ~20%. A seed missing jungles is inconvenient, not unwinnable. Coal and copper are never redistributed.

Water/coast at or above y=0 uses Plains rates; below y=0 uses Ungrouped. Lush caves are always Ungrouped.

### 3.3 Ore philosophy (important for ideas)

We currently play **flat 90%** so the first playtest is “vanilla, a bit stingier,” not “you must found an iron republic.” The biased table is the *intended* long-term world, sitting commented in `config.yml`.

When you propose resource ideas, say whether they assume **flat** (current) or **biased** (intended).

---

## 4. Nightfall — the flagship system

Night 1 is vanilla. Danger is a linear fraction `nights / 200`.

Admins can jump the *Nightfall clock* with `/civ nightfall set` without rewinding the world’s actual age. `/civ nightfall siege` forces a wave for testing.

### 4.1 What ramps (linear, cap day 200)

| Lever | Night 1 | Day 200 |
| --- | --- | --- |
| Hostile health | vanilla | +50% (×1.5) |
| Hostile damage | vanilla | +25% |
| Zombie speed | vanilla | +15% (zombies only) |
| Extra natural hostiles | +0% | +50% |
| Spider-jockey chance on natural spiders | 1% | 35% |
| Settlement siege chance | 5% | 50% |
| Siege pack size | 12 | 24 |
| Skeleton-trap extra chance on storm lightning | 0% | 25% |

Sleep is slightly harder: monster-near-bed check is **1.2×** vanilla’s 8×5×8.

Zombies can break doors (still needs Hard + `mobGriefing`).

### 4.2 Settlement sieges

- Fire only near **beds or villages**, never around a lone traveler.
- At most one attempt per settlement cell per calendar night.
- Composition: zombies, zombie villagers, skeletons, strays, husks.
- 1–2 spider jockeys. Riders are weighted toward **skeletons**, with some strays/husks. Baby zombies are 1.25× a base rider slot, still a minority.
- From **night 100**: phantoms can appear; a giant may join a siege (unlock 5% → 30% at cap). One giant per world. Giants cut TNT-sized craters and do not spare player builds except unbreakables (bedrock, portals, command blocks, etc.).

### 4.3 Explicitly not in this phase

- Exponential or log curves
- Chest-density targeting
- “Most players nearby, minimum 3” targeting
- Roaming wilderness giants
- Nightfall rewriting vanilla local/regional difficulty (they stack; we did not replace Mojang’s formula)

Those are the next implementation conversation, not the current live rules.

---

## 5. Player-facing fantasy

A new player should feel, in order:

1. **Week 1.** Nights are almost vanilla. Food and fall damage are a little meaner. Gear wears a bit faster. You can still be a hermit.
2. **First settlement.** The moment you place beds or sit in a village, the night starts *noticing* you. Sieges are rare, then less rare.
3. **Month 1+.** Packs get bigger. Jockeys show up. Storms can drop skeleton-horse traps. Sleeping takes more perimeter lighting.
4. **After day 100.** Phantoms and a siege giant turn a town night into a civil-defense event. This is when walls, bells, roles, and neighbors should matter.
5. **Day 200.** The linear cap. If the server still feels easy, *that* is a research finding, not a bug.

Seasons and the biome compass are built but off, so food geography and “go find a jungle” are **latent**. Say so when you pitch ideas that depend on them.

---

## 6. Hard exclusions

Do **not** spend research time on:

- Random / forced respawn relocation
- Harder mining (slower break speed, extra stone hardness)
- Iron golem nugget nerfs
- Herobrine or ARG horror
- Raid / totem changes
- Nether, End, or dimension travel control
- New player classes, skill trees, mana, custom weapons
- A second economy plugin (shops, coins) unless it is strictly in service of biome trade
- Exponential Nightfall numbers *as if they were already shipping* — argue for them as Phase 2

Those are either owned elsewhere or rejected on purpose.

---

## 7. What we want from you

We want **playable civilization ideas**, ranked by how well they create:

- **Trade** between specialized regions
- **Migration** when a home biome is the wrong tool
- **Settlement** that is worth defending
- **Cooperation** that is cheaper than everyone turtling
- **Conflict** that is political (who owns the emerald ridge) not just PvP kits

Write ideas as one-pagers with:

1. **Player story** — what a group does on a Tuesday because this exists  
2. **Pressure type** — resource, night, food, information, logistics, reputation  
3. **Module shape** — on/off, what config knobs, what happens if it is off  
4. **Failure mode** — how this becomes grief, farm, or busywork  
5. **Phase** — can ship on linear Nightfall now, or needs biased ores / seasons / exponential nights  

### 7.1 Open questions we actually care about

**A. The hermit problem**  
What finally makes a competent solo player *want* a neighbor, without making solo unplayable?

**B. The turtle problem**  
A well-lit bunker with farms can ignore Nightfall. Should pressure reach underground / interior bases? How, without invalidating the fun of building a fortress?

**C. The lucky-biome problem**  
Flat 90% ores delay this. When we turn biased ores on, how do we teach “go to the taiga for iron” without a wiki? Is the compass enough, or do we need signs in the world?

**D. Food as politics**  
Seasons are implemented and off. Is drought/wet the right lever, or do we want something more social (granaries, shared animal stock, village crop claims)?

**E. What a “capital” is**  
Sieges already key off beds + villages. Should later targeting prefer *dense* bed clusters, chest rooms, or “at least N players slept here”? What is the smallest thing that should count as a town?

**F. Day 100 as a holiday**  
Phantoms + giants unlock together. Is that a server event we should name and ritualize (watch night, militia, tribute), or should it stay a quiet mechanical unlock?

**G. Giants as infrastructure damage**  
They crater builds. Is that the right “civilization-scale” threat, or does it just make people quit building? What would a giant do that feels like a siege engine rather than a greifer?

**H. Information**  
Players cannot see Nightfall % unless an op runs `/civ nightfall status`. Should civilians get a rumor, a sky tell, a villager line, or stay ignorant?

**I. Cooperation tools we refuse to build vs. ones we should**  
We will not build a town plugin. We *can* make the world reward claimed, shared space. What’s the thinnest mechanic (shared sleep bonus, wall blocks that giants respect, village bells that actually do something) that creates a town without becoming Towny?

**J. Conflict without forcing PvP**  
How do two groups compete for a diamond range or a village without the plugin spawning a faction system?

**K. Phase 2 curve**  
When we do exponential, what should explode — pack size, chance, health, or *variety*? We are biased toward “more kinds of problem,” not “200 HP zombies.”

**L. Offline / low-pop nights**  
A 3-player weeknight vs. a 20-player Saturday. Should Nightfall care about who is online, or only about the calendar and the beds that exist?

---

## 8. Success looks like

Qualitative playtest signals, not KPIs.

- People talk about *regions* (“we’re the iron camp”) instead of coordinates.
- Someone leaves a safe base because their biome cannot feed the next project.
- A night after day 100 is planned for: roles, walls, who sleeps, who fights.
- A group that ignored villages/beds is safer than a group that founded a town — and they feel the tradeoff.
- Nobody says the plugin is “just extra mob health.”
- Turning a module off is a meaningful server decision, not a crash.

Failure signals:

- Players stop placing beds.
- Players stop building above ground.
- One farm / one gold farm / one AFK spot trivializes the cap.
- New players cannot explain why last night was worse.
- Ops spend more time on `/civ config` than players spend on the world.

---

## 9. Technical envelope (so ideas stay shippable)

- Paper plugin, config-driven YAML, `/civ reload` and `/civ config get|set`.
- Nightfall clock is persisted per world in `nightfall.dat` and is *not* the vanilla world age.
- Ore processing is one-time per chunk and persisted. Rate changes do not rewrite already-processed chunks unless ops reprocess.
- Seasons persist in `seasons.dat`.
- No database. No website. No extra client mods.
- Ideas that need new blocks, dimensions, or resource packs are heavier than ideas that retune mobs, weather, sleep, villages, and loot.

If you need the live knobs, read `src/main/resources/config.yml` in the repo. That file is the source of truth for numbers in this PRD.

---

## 10. How to send ideas back

Prefer a short brief per idea over a brainstorm dump.

```text
Title:
Player story:
Pressure type:
Needs (flat ores / biased ores / seasons / compass / night 100 / exponential):
Config sketch:
Grief / farm risk:
Why this is civilization, not content:
```

Out of scope one-liners (“add more bosses”, “make creepers nuclear”) will be discarded. Arguments that change the *social map* of the server will be kept.

If two ideas conflict, we will keep the one that makes settlement, trade, or a named region more likely.
