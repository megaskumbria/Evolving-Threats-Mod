# Instructions for AI agents (Cursor, etc.)

Project: **Evolving Threats** — Fabric mod for Minecraft.  
The author is **learning** and wants to **tune balance and behavior** without deep Java knowledge.

## Main rule

**Always leave explanations in code**, as in `src/main/java/com/example/RandomWeapons.java`:

- a **SETTINGS** block at the top of the class with `public static final` and comments in **English**;
- what changes if you increase or decrease a value;
- for chances, use format `0.0–1.0` and a percent example.

Do **not** hide logic in mixins without comments or extracted constants.

## New mob or ability: ALWAYS ask about the threat scale

The mod has a rising difficulty scale (`threat/ThreatScale.java`, author notes — `mobs/common/threat-scale.txt`).  
Each mechanic is one of four types; **only the author** picks the type.

When adding a mob, ability, item, spawn chance, or event:

1. **Do not decide** how it ties to the scale. Ask the author (`AskQuestion`, one question, plain language).
2. Offer options and **your recommendation** (what changes in-game):
   - **FIXED** — chance stays as in code; does not scale with days.
   - **FIXED + “from day N”** — no mechanic until day N, then fixed chance (`threat/ThreatTable.java` thresholds).
   - **GROWING** — chance rises from day-0 start to day-100 value; moon affects it.
   - **GROWING + “from day N”** — nothing until day N, then grows.
3. If GROWING — confirm start value (or default via `ThreatScale.dangerousChance` / `growingChance` and `ThreatTable.DANGEROUS_START_RATIO`) and document it in `ThreatTable`.
4. If “from day N” — add a day constant in `ThreatTable` and check with `ThreatScale.reached(level, ThreatTable.XXX_DAY)`.
5. If the author is unsure — use your recommendation, use named constants, and **say it was an assumption**.
6. After implementation, add one short line to `mobs.txt` (see below) and update `mobs/common/threat-scale.txt` if the scale type or a day threshold changed.
7. If the player can notice the change, add one English bullet to the current version at the top of `CHANGELOG.md` before finishing. Write only what changed. Do not note that something else stayed the same. Do not open a new version number unless the author did. Skip comment-only edits and agent-instruction edits.

Do not leave new mechanics unlabeled; do not apply the scale silently.

## mobs.txt

`mobs.txt` only lists what the mod has. One line: the feature in a few words, then FIXED, GROWING, or from day N.

Do not write chances, timers, damage, how it works, or what it breaks. A bed line is "Bed does not skip a night for free — FIXED", not the path, the percents, or which blocks break. Numbers and behavior live in the note under `mobs/<folder>/` and in the SETTINGS comments.

## Where to look

One task — one table row. One variant = one file under `mobs/<folder>/`. Shared mechanics, siege, and threat scale live in `mobs/common/`. The note’s **Code:** line lists mixins; do not scan all of `mixin/`.

Open `entity/` and `entity/ai/` only when the mechanics class points there. Open `src/client` only for visuals.

Do not read `.gradle/`, `build/`, `run/logs/` (`.cursorignore`). Do not run `.\gradlew` or pull logs unless the author asks. Do not bulk-open Minecraft sources.

Paths in the table are under `src/main/java/com/example/`.

### Shared across many mobs

| In-game | Java first | Author note |
|--------|------------|-------------|
| Threat scale (day 0–100, moon, growing chances) | `threat/ThreatScale.java`, `threat/ThreatTable.java`, `ThreatData`/`ThreatEvents` | `mobs/common/threat-scale.txt`, `mobs.txt`, `idea.txt` |
| Threat HUD | `src/client/.../ThreatHud.java`, `tools/prepare_threat_textures.py`, `art/threat_hud/` | — |
| Advancements tab | `advancement/ModAdvancements.java` | icons and rewards in `data/evolving-threats/advancement`, item rewards in `data/evolving-threats/loot_table/advancement`, names in lang. Parents in the JSON are the tree only; do not put the mob's name in the title |
| Random weapons / dual wield | `RandomWeapons.java` | `mobs/common/dual-wield.txt` |
| Random / dyed leather armor | `RandomArmor.java` | `mobs/common/dyed-leather-armor.txt` |
| Full armor set | `RandomArmor.java` (`FULL_SET_CHANCE`) | `mobs/common/full-armor-set.txt` |
| Enchantments | `MobEnchantments.java` | `mobs/common/enchantments-on-gear.txt` |
| Shields | `MobShields.java` | `mobs/common/shields.txt` |
| Axe disables shield | `axe/AxeShieldDisable.java` | `mobs/common/axe-disables-shield.txt` |
| Sight range | `sight/MobSight.java` | `mobs/common/sight-range.txt` |
| Sleep shelter | `sleep/SleepShelterMath.java`, `sleep/SleepShelter.java` | `mobs/common/sleep.txt` |
| Totem of undying | `MobTotem.java` | `mobs/common/totem-of-undying.txt` |
| Spawn potion buffs | `MobPotionBuffs.java` | `mobs/common/potion-buffs.txt` |
| Random health | `MobHealth.java` | `mobs/common/random-health.txt` |
| Kill XP | `MobExperience.java` | `mobs/common/kill-experience.txt` |
| Floating tool | `tool/FloatingTool.java` | `mobs/common/floating-tool.txt` |
| Damaged golem | `golem/DamagedGolem.java` | `mobs/golems/golem.txt` |
| Bastion (zombie/skeleton) | `bastion/Bastion.java` | `mobs/common/bastion.txt` |
| Spiky | `spiky/Spiky.java` | `mobs/common/spiky.txt` |
| Mirror | `mirror/Mirror.java` | `mobs/common/mirror.txt` |

### Skeletons

| In-game | Java first | Author note |
|--------|------------|-------------|
| Fast long-range bow | `skeleton/FastBowSkeleton.java` | `mobs/skeletons/fast-bow.txt` |
| Small skeleton | `skeleton/SmallSkeleton.java` | `mobs/skeletons/small-skeleton.txt` |
| Harmful arrow | `skeleton/HarmfulArrowSkeleton.java` | `mobs/skeletons/harmful-arrow.txt` |
| Random effect arrows | `skeleton/RandomArrowSkeleton.java` | `mobs/skeletons/random-arrows.txt` |
| Sniper | `skeleton/SniperSkeleton.java` | `mobs/skeletons/sniper.txt` |
| Disarm arrow | `skeleton/SkeletonDisarm.java` | `mobs/skeletons/disarm-arrow.txt` |
| Tactician (“700 IQ”) | `skeleton/TacticianSkeleton.java` | `mobs/skeletons/tactician.txt` |
| Dispenser head barrage | `RandomDispenserSkull.java` | no note; see `entity/ai/DispenserBarrageGoal.java` |
| Spectral arrow hunt | `skeleton/SpectralArrowHunt.java` | no note |
| Wither skeleton + spawner | `spawnerhead/SpawnerHeadMob.java` | `mobs/skeletons/wither-spawner.txt` |
| Wither sword / XP | `skeleton/WitherSkeletonSword.java` | `mobs/skeletons/wither-sword.txt` |
| Captain | `skeleton/SkeletonCaptain.java` | `mobs/skeletons/captain.txt` |
| Basilisk | `skeleton/BasiliskSkeleton.java` | `mobs/skeletons/basilisk.txt` |
| Horseman | `skeleton/SkeletonHorseman.java` | `mobs/skeletons/horseman.txt` |
| Punch bow V–VI | `skeleton/SkeletonPunchBow.java` | `mobs/skeletons/punch-bow.txt` |
| Jack-o’-lantern (skeleton copy) | `beacon/JackLanternBeacon.java` | `mobs/skeletons/jack-o-lantern.txt` |
| Spawner head (skeleton copy) | `spawnerhead/SpawnerHeadMob.java` | `mobs/skeletons/spawner-head.txt` |
| Ritual band (skeleton copy) | `ritual/RitualBand.java` | `mobs/skeletons/ritual-band.txt` |
| Shared variants | — | `mobs/skeletons/shared-variants.txt` |

### Zombies

| In-game | Java first | Author note |
|--------|------------|-------------|
| Light seeking | `light/LightSeeking.java` | `mobs/zombies/light-seeking.txt` |
| Block breaking | `digging/BlockBreaking.java` | `mobs/zombies/block-breaking.txt` |
| Player grudge | `grudge/PlayerGrudge.java` | no separate note; used by light and digging |
| Jack-o’-lantern beacon | `beacon/JackLanternBeacon.java`, `JackLanternLight.java` | `mobs/zombies/jack-o-lantern.txt` and `mobs/skeletons/jack-o-lantern.txt` |
| Spawner head | `spawnerhead/SpawnerHeadMob.java` | `mobs/zombies/spawner-head.txt` |
| Rotten overseer | `overseer/ZombieOverseer.java` | `mobs/zombies/rotten-overseer.txt` |
| TNT zombie | `zombie/TntZombie.java` | `mobs/zombies/tnt-zombie.txt` |
| Sorcerer | `zombie/ZombieSorcerer.java` | `mobs/zombies/sorcerer.txt` |
| Excalibur | `zombie/ZombieExcalibur.java` | `mobs/zombies/excalibur.txt` |
| Mjölnir | `zombie/ZombieMjolnir.java` | `mobs/zombies/mjolnir.txt` |
| Ritual band | `ritual/RitualBand.java` | `mobs/zombies/ritual-band.txt` |
| Drowned | `drowned/DrownedThreats.java` | `mobs/zombies/drowned.txt` |
| Drowned fishing rod | `drowned/DrownedFishing.java` | `mobs/zombies/drowned-fishing-rod.txt` |
| Theft | `zombie/ZombieTheft.java` | `mobs/zombies/steals-item.txt` |
| Shared variants (bastion, spiky, mirror, raid, siege) | — | `mobs/zombies/shared-variants.txt` |

### Creepers, spiders, witches, raids

| In-game | Java first | Author note |
|--------|------------|-------------|
| Dynamite thrower | `creeper/DynamiteCreeper.java` | `mobs/creepers/dynamite.txt` |
| Charged creeper | `creeper/ChargedCreeper.java` | `mobs/creepers/charged.txt` |
| Shield-break creeper | `creeper/ShieldBreakCreeper.java` | `mobs/creepers/shield-break.txt` |
| Sapper creeper | `creeper/SapperCreeper.java` | `mobs/creepers/sapper.txt` |
| Super-fast creeper | `creeper/FastCreeper.java` | `mobs/creepers/super-fast.txt` |
| Feint creeper | `creeper/FeintCreeper.java` | `mobs/creepers/feint.txt` |
| Magnet creeper | `creeper/MagnetCreeper.java` | `mobs/creepers/magnet.txt` |
| Spider weaver and matriarch | `spider/SpiderThreats.java` | `mobs/spiders/weaver.txt` |
| Spider swarm | `spider/SpiderSwarm.java` | `mobs/spiders/swarm.txt` |
| Cave spider poison | `spider/CaveSpiderPoison.java` | `mobs/spiders/cave-poison.txt` |
| Raid anchor | `raid/RaidAnchor.java` | `mobs/common/raid-anchor.txt` |
| Siege | `siege/SiegeNight.java`, `SiegeWaves.java`, `SiegeComposition.java` | `mobs/common/siege.txt` |
| Phantom bite and flock | `phantom/PhantomThreats.java` | `mobs/phantoms/bite-and-flock.txt` |
| Phantom weakness | `phantom/PhantomWeakness.java` | `mobs/phantoms/weakness.txt` |
| Phantom blindness | `phantom/PhantomBlindness.java` | `mobs/phantoms/blindness.txt` |
| Patrol at the base | `illager/IllagerPatrol.java` | `mobs/illagers/patrol.txt` |
| Pillager firework | `illager/PillagerFirework.java` | `mobs/illagers/firework-crossbow.txt` |
| Pillager and vindicator armor | `illager/PillagerArmor.java` | `mobs/illagers/armor.txt` |
| Vindicator diamond axe | `illager/VindicatorDiamondAxe.java` | `mobs/illagers/diamond-axe.txt` |
| Vindicator chops wood | `illager/VindicatorWoodBreak.java` | `mobs/illagers/chops-wood.txt` |
| Ravager breaks walls | `illager/RavagerWallBreak.java` | `mobs/illagers/ravager.txt` |
| Vex extra heart | `vex/VexDamage.java` | `mobs/illagers/vex.txt` |
| Blaze alchemist | `blaze/BlazeAlchemist.java` | `mobs/blazes/blaze.txt` |
| Witch potions and fog | `witch/WitchThrownPotions.java` | `mobs/witches/potions-and-fog.txt` |
| Witch invisibility | `witch/WitchInvisibility.java` | `mobs/witches/invisibility.txt` |
| Nullifier witch | `witch/WitchNullifier.java` | `mobs/witches/nullifier.txt` |
| Slime poison | `slime/SlimePoison.java` | `mobs/slimes/poison.txt` |
| Magma cube | `magma/MagmaCubeThreats.java` | `mobs/slimes/magma-cube.txt` |
| Enderman swap | `enderman/EndermanSwap.java` | `mobs/endermen/swap-places.txt` |
| Enderman moves blocks | `enderman/EndermanBlocks.java` | `mobs/endermen/moves-blocks.txt` |
| Shulker shots and teleport | `shulker/ShulkerThreats.java` | `mobs/shulkers/shots-and-teleport.txt` |
| Shulker closed shell | `shulker/ShulkerShellDurability.java` | `mobs/shulkers/closed-shell.txt` |
| Piglin brute health | `piglin/PiglinBruteHealth.java` | `mobs/piglins/health.txt` |
| Piglin brute armor damage | `piglin/PiglinBruteArmorHit.java` | `mobs/piglins/armor-damage.txt` |

Misc: `EvolvingThreats.java` — entrypoint; `command/EvolvingThreatsCommands.java` — test commands; `idea.txt` — design notes. New mechanics → new class; mixin → thin hook only.

## Comments

- Author-facing comments: **English**.
- Code names: **English**.
- Explain mixins, `EntitySpawnReason`, server vs client when it matters.

## Build and test

- Dev: `.\gradlew runClient` (restart after `.java` changes).
- Release JAR: `.\gradlew build` → `build/libs/`.

## Cursor

Rules: `.cursor/rules/explain-code-for-author.mdc`, `.cursor/rules/ask-dont-assume.mdc`.  
Heavy folders are in `.cursorignore`.
