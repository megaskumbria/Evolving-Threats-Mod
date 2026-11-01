# Instructions for AI agents (Cursor, etc.)

Project: **Evolving Threats** — Fabric mod for Minecraft.  
The author is * * learning * * and wants to ** change the balance and behavior** himself, without understanding Java deeply.

The principal rule:

**Always leave explanations in the code** as in `src/main/java/com/example/RandomWeapons.java`:

- block **"SETTINGS"** at the beginning of the class with `public static final` and comments in Russian;
- what changes if you increase/decrease the number;
- for chances to explicitly write the format `0.0–1.0` and the example as a percentage.

The agent ** should not ** merge all the logic in mixin without comments and without the constants taken out.

## New mob or new ability: ALWAYS ask about the threat scale

In fashion, there is a scale of increasing complexity (`threat/ThreatScale.java`, description for the author — `mobs/common/threat-scale.txt`).
Each mechanic belongs to one of four types, and ** only the author * * chooses the type.

When you invent or add a new mob, ability, mob item, spawn chance, or event:

1. **Не решай сам**, как его привязать к шкале. Спроси автора (`AskQuestion`, по одному вопросу, по-русски, простым языком).
2. Предложи варианты и **свою рекомендацию** с короткой причиной (что изменится в игре):
   - **FIXED** — шанс всегда такой, как записан в коде, от дней не зависит.
   - **FIXED + «С N-го дня»** — до дня N механики нет совсем, потом шанс фиксированный (пороги в `threat/ThreatTable.java`).
   - **GROWING** — шанс плавно растёт от стартового значения в день 0 до значения в коде в день 100, на него влияет луна.
   - **GROWING + «С N-го дня»** — комбинация: нет до дня N, потом растёт.
3. Если выбрано GROWING — уточни стартовое значение (или предложи по умолчанию через `ThreatScale.dangerousChance`
   / `growingChance` и `ThreatTable.DANGEROUS_START_RATIO`) и вынеси его в `ThreatTable` с комментарием.
4. Если выбрано «С N-го дня» — добавь константу дня в `ThreatTable` и проверяй её через `ThreatScale.reached(level, ThreatTable.XXX_DAY)`.
5. Если автор не знает ответа — возьми рекомендованный вариант, вынеси числа в именованные константы и **явно напиши в ответе, что это допущение**.
6. После реализации обнови `mobs.txt` (метка FIXED/GROWING у новой строки) и, если нужно, `mobs/common/threat-scale.txt`.

Do not leave the new mechanics "unmarked": by default, do not apply the scale silently, always clarify.

Where do I look?

One task — one row of the table below. The behavior of one entity is collected by sections in one folder file (`mobs/zombies/zombie.txt` and so on). Common to many mobs, the siege and threat scale are separate files in `mobs/common/`. In the note, the "Code:" line already lists myxins. The `mixin/` folder is not completely bypassed.

`entity/` and `entity/ai/` open only if the mechanics class refers to a specific file. `src/client` — only if the task is about appearance.

Do not read `.gradle/`, `build/`, `run/logs/` (they are in `.cursorignore'). `.\gradlew` do not run and do not pull out the logs until the author himself asked. Do not open Minecraft sources in a pack.

The paths in the table are from `src/main/java/com/example/`.

### Common to many mobs

| В игре | Сначала этот файл | Заметка |
|--------|-------------------|---------|
| Шкала угрозы мира (день 0–100, луна, рост шансов) | `threat/ThreatScale.java` (формула; дни считаются от момента установки мода в мир — `ThreatData`/`ThreatEvents`), `threat/ThreatTable.java` (дни-пороги и стартовые значения) | `mobs/common/threat-scale.txt` (описание для автора), `mobs.txt` (метки FIXED/GROWING), `idea.txt` |
| Шкала угрозы на экране (HUD) | `src/client/.../client/threat/ThreatHud.java`, картинки делает `tools/prepare_threat_textures.py` из `art/threat_hud/` | — |
| Случайное оружие и два оружия | `RandomWeapons.java` | `mobs/common/dual-wield.txt` |
| Случайная и крашеная кожаная броня | `RandomArmor.java` | `mobs/common/dyed-leather-armor.txt` |
| Полный комплект брони | `RandomArmor.java` (`FULL_SET_CHANCE`) | `mobs/common/full-armor-set.txt` |
| Чары на оружии и броне | `MobEnchantments.java` | `mobs/common/enchantments-on-gear.txt` |
| Щиты | `MobShields.java` | `mobs/common/shields.txt` |
| Топор сбивает щит | `axe/AxeShieldDisable.java` | `mobs/common/axe-disables-shield.txt` |
| Дальность взгляда | `sight/MobSight.java` | `mobs/common/sight-range.txt` |
| Тотем бессмертия | `MobTotem.java` | `mobs/common/totem-of-undying.txt` |
| Зелья при появлении | `MobPotionBuffs.java` | `mobs/common/potion-buffs.txt` |
| Случайное здоровье | `MobHealth.java` | `mobs/common/random-health.txt` |
| Опыт за убийство усиленных | `MobExperience.java` | `mobs/common/kill-experience.txt` |
| Парящий инструмент | `tool/FloatingTool.java` | `mobs/common/floating-tool.txt` |
| Повреждённый голем | `golem/DamagedGolem.java` | `mobs/golems/golem.txt` |
| Бастион (зомби или скелет) | `bastion/Bastion.java` | `mobs/common/bastion.txt` |
| Spiky (укол брони на 7–8 сердец) | `spiky/Spiky.java` | `mobs/common/spiky.txt` |
| Зеркало (возврат удара) | `mirror/Mirror.java` | `mobs/common/mirror.txt` |

Skeletons

| В игре | Сначала этот файл | Заметка |
|--------|-------------------|---------|
| Дальний быстрый лук | `skeleton/FastBowSkeleton.java` | `mobs/skeletons/skeleton.txt` (раздел «СКЕЛЕТ С ДАЛЬНИМ БЫСТРЫМ ЛУКОМ») |
| Маленький скелет | `skeleton/SmallSkeleton.java` | `mobs/skeletons/skeleton.txt` (раздел «МАЛЕНЬКИЙ СКЕЛЕТ») |
| Вредная стрела | `skeleton/HarmfulArrowSkeleton.java` | `mobs/skeletons/skeleton.txt` (раздел «СКЕЛЕТ С ВРЕДНОЙ СТРЕЛОЙ») |
| Случайные стрелы | `skeleton/RandomArrowSkeleton.java` | `mobs/skeletons/skeleton.txt` (раздел «СКЕЛЕТ СО СЛУЧАЙНЫМИ СТРЕЛАМИ») |
| Скелет-снайпер | `skeleton/SniperSkeleton.java` | `mobs/skeletons/skeleton.txt` (раздел «СКЕЛЕТ-СНАЙПЕР») |
| Стрела выбивает вещи из рук | `skeleton/SkeletonDisarm.java` | `mobs/skeletons/skeleton.txt` (раздел «СКЕЛЕТ: СТРЕЛА ВЫБИВАЕТ ВЕЩИ ИЗ РУК») |
| «700 IQ», меч на спине | `skeleton/TacticianSkeleton.java` | `mobs/skeletons/skeleton.txt` (раздел «СКЕЛЕТ «700 IQ»») |
| Раздатчик на голове, залп стрел | `RandomDispenserSkull.java` | заметки нет; цель — `entity/ai/DispenserBarrageGoal.java` |
| Спектральная стрела поднимает мобов | `skeleton/SpectralArrowHunt.java` | заметки нет |
| Иссушитель со спавнером | `spawnerhead/SpawnerHeadMob.java` | `mobs/skeletons/skeleton.txt` (раздел «СКЕЛЕТ-ИСУШИТЕЛЬ СО СПАВНЕРОМ») |
| Меч и опыт иссушителя | `skeleton/WitherSkeletonSword.java` | `mobs/skeletons/skeleton.txt` (раздел «СКЕЛЕТ-ИССУШИТЕЛЬ: МЕЧ И ОПЫТ») |
| Капитан скелетов | `skeleton/SkeletonCaptain.java` | `mobs/skeletons/skeleton.txt` (раздел «КАПИТАН СКЕЛЕТОВ») |
| Василиск | `skeleton/BasiliskSkeleton.java` | `mobs/skeletons/skeleton.txt` (раздел «ВАСИЛИСК») |
| Скелет на костяной лошади | `skeleton/SkeletonHorseman.java` | `mobs/skeletons/skeleton.txt` (раздел «СКЕЛЕТ НАЕЗДНИК») |
| Лук с Откидыванием V–VI | `skeleton/SkeletonPunchBow.java` | `mobs/skeletons/skeleton.txt` (раздел «ЛУК С ОТКИДЫВАНИЕМ V И VI») |

Zombie

| В игре | Сначала этот файл | Заметка |
|--------|-------------------|---------|
| Идут на свет | `light/LightSeeking.java` | `mobs/zombies/zombie.txt` (раздел «ЗОМБИ ИДУТ НА СВЕТ») |
| Ломают блоки к игроку | `digging/BlockBreaking.java` | `mobs/zombies/zombie.txt` (раздел «ЗОМБИ ЛОМАЮТ БЛОКИ») |
| Помнит игрока и идёт за стену | `grudge/PlayerGrudge.java` | заметки нет; этим пользуются свет и копание |
| Маяк из тыквы | `beacon/JackLanternBeacon.java`, свет — `beacon/JackLanternLight.java` | `mobs/zombies/zombie.txt` и `mobs/skeletons/skeleton.txt` (раздел «МАЯК») |
| Носитель спавнера | `spawnerhead/SpawnerHeadMob.java` | `mobs/zombies/zombie.txt` и `mobs/skeletons/skeleton.txt` (раздел «НОСИТЕЛЬ СПАВНЕРА») |
| Гнилой надсмотрщик | `overseer/ZombieOverseer.java` | `mobs/zombies/zombie.txt` (раздел «ГНИЛОЙ НАДСМОТРЩИК») |
| Зомби с динамитом | `zombie/TntZombie.java` | `mobs/zombies/zombie.txt` (раздел «ЗОМБИ С ДИНАМИТОМ») |
| Зомби-чародей | `zombie/ZombieSorcerer.java` | `mobs/zombies/zombie.txt` (раздел «ЗОМБИ-ЧАРОДЕЙ») |
| Зомби Excalibur | `zombie/ZombieExcalibur.java` | `mobs/zombies/zombie.txt` (раздел «ЗОМБИ EXCALIBUR») |
| Зомби Mjölnir | `zombie/ZombieMjolnir.java` | `mobs/zombies/zombie.txt` (раздел «ЗОМБИ MJÖLNIR») |
| Ритуальная стая | `ritual/RitualBand.java` | `mobs/zombies/zombie.txt` и `mobs/skeletons/skeleton.txt` (раздел «РИТУАЛЬНАЯ СТАЯ») |
| Утопленник | `drowned/DrownedThreats.java` | `mobs/zombies/zombie.txt` (раздел «УТОПЛЕННИК: ШЛЕМ, ВОДА, ГРОЗА») |
| Удочка утопленника | `drowned/DrownedFishing.java` | `mobs/zombies/zombie.txt` (раздел «УТОПЛЕННИК С УДОЧКОЙ») |
| Зомби ворует вещь | `zombie/ZombieTheft.java` | `mobs/zombies/zombie.txt` (раздел «ЗОМБИ: ВОРУЕТ ВЕЩЬ ИЗ РУК») |

### Creepers, Spiders, Witches, Raid

| В игре | Сначала этот файл | Заметка |
|--------|-------------------|---------|
| Метатель динамита | `creeper/DynamiteCreeper.java` | `mobs/creepers/creeper.txt` (раздел «КРИПЕР-МЕТАТЕЛЬ ДИНАМИТА») |
| Заряженный крипер | `creeper/ChargedCreeper.java` | `mobs/creepers/creeper.txt` (раздел «ЗАРЯЖЕННЫЙ КРИПЕР») |
| Ломает щит взрывом | `creeper/ShieldBreakCreeper.java` | `mobs/creepers/creeper.txt` (раздел «КРИПЕР, КОТОРЫЙ ЛОМАЕТ ЩИТ») |
| Подрывник | `creeper/SapperCreeper.java` | `mobs/creepers/creeper.txt` (раздел «КРИПЕР-ПОДРЫВНИК») |
| Супербыстрый крипер | `creeper/FastCreeper.java` | `mobs/creepers/creeper.txt` (раздел «СУПЕРБЫСТРЫЙ КРИПЕР») |
| Крипер-обманка | `creeper/FeintCreeper.java` | `mobs/creepers/creeper.txt` (раздел «КРИПЕР-ОБМАНКА») |
| Ткач и матка | `spider/SpiderThreats.java` | `mobs/spiders/spider.txt` (раздел «ПАУКИ: ТКАЧ И МАТКА») |
| Рой пауков | `spider/SpiderSwarm.java` | `mobs/spiders/spider.txt` (раздел «ПАУКИ: РОЙ») |
| Яд пещерного паука может убить | `spider/CaveSpiderPoison.java` | `mobs/spiders/spider.txt` (раздел «ПЕЩЕРНЫЙ ПАУК: ЯД СНОВА МОЖЕТ УБИТЬ») |
| Рейдовый якорь | `raid/RaidAnchor.java` | `mobs/common/raid-anchor.txt` |
| Осада (редкая ночь, волны, запрет сна и порталов) | `siege/SiegeNight.java`, `siege/SiegeWaves.java`, `siege/SiegeComposition.java` (фракции и доли мобов) | `mobs/common/siege.txt` |
| Укус и стая фантомов | `phantom/PhantomThreats.java` | `mobs/phantoms/phantom.txt` (раздел «ФАНТОМЫ: УКУС И СТАЯ») |
| Слабость с укуса фантома | `phantom/PhantomWeakness.java` | `mobs/phantoms/phantom.txt` (раздел «ФАНТОМЫ: СЛАБОСТЬ С УКУСА») |
| Слепота с укуса фантома | `phantom/PhantomBlindness.java` | `mobs/phantoms/phantom.txt` (раздел «ФАНТОМЫ: СЛЕПОТА С УКУСА») |
| Патруль у дома | `illager/IllagerPatrol.java` | `mobs/illagers/illager.txt` (раздел «ПАТРУЛЬ У ДОМА») |
| Ракета разбойника | `illager/PillagerFirework.java` | `mobs/illagers/illager.txt` (раздел «РАЗБОЙНИК: РАКЕТА ИЗ АРБАЛЕТА») |
| Алмазный топор поборника | `illager/VindicatorDiamondAxe.java` | `mobs/illagers/illager.txt` (раздел «ПОБОРНИК: АЛМАЗНЫЙ ТОПОР») |
| Поборник рубит дерево | `illager/VindicatorWoodBreak.java` | `mobs/illagers/illager.txt` (раздел «ПОБОРНИК РУБИТ ДЕРЕВО») |
| Разоритель пробивает стены | `illager/RavagerWallBreak.java` | `mobs/illagers/illager.txt` (раздел «РАЗОРИТЕЛЬ: ПРОБИВАЕТ СТЕНЫ») |
| Урон и слабость досаждателя | `vex/VexDamage.java` | `mobs/illagers/illager.txt` (раздел «ДОСАЖДАТЕЛЬ: +1 СЕРДЦЕ К УДАРУ») |
| Блейз-алхимик | `blaze/BlazeAlchemist.java` | `mobs/blazes/blaze.txt` |
| Ведьма-обнулитель | `witch/WitchNullifier.java` | `mobs/witches/witch.txt` (раздел «ВЕДЬМА-ОБНУЛИТЕЛЬ») |
| Невидимость ведьмы | `witch/WitchInvisibility.java` | `mobs/witches/witch.txt` (раздел «ВЕДЬМА: ЗЕЛЬЕ НЕВИДИМОСТИ») |
| Зелья ведьмы в игрока | `witch/WitchThrownPotions.java` | `mobs/witches/witch.txt` (раздел «ВЕДЬМА: НОВЫЕ ЗЕЛЬЯ И ТУМАН») |
| Яд слизней | `slime/SlimePoison.java` | `mobs/slimes/slime.txt` (раздел «ЯД СЛИЗНЕЙ») |
| Магмовый куб | `magma/MagmaCubeThreats.java` | `mobs/slimes/slime.txt` (раздел «МАГМОВЫЙ КУБ») |
| Обмен местами эндермена | `enderman/EndermanSwap.java` | `mobs/endermen/enderman.txt` (раздел «ЭНДЕРМЕН МЕНЯЕТСЯ МЕСТАМИ») |
| Чаще берёт и ставит блоки | `enderman/EndermanBlocks.java` | `mobs/endermen/enderman.txt` (раздел «ЭНДЕРМЕН БЕРЁТ И СТАВИТ БЛОКИ») |
| Снаряды шалкера | `shulker/ShulkerThreats.java` | `mobs/shulkers/shulker.txt` (раздел «ШАЛКЕРЫ: СНАРЯДЫ И ПЕРЕСАДКА») |
| Панцирь шалкера | `shulker/ShulkerShellDurability.java` | `mobs/shulkers/shulker.txt` (раздел «ШАЛКЕР: ЗАКРЫТЫЙ ПАНЦИРЬ ПОРТИТ ОРУЖИЕ») |
| Удар брута по броне | `piglin/PiglinBruteArmorHit.java` | `mobs/piglins/piglin.txt` (раздел «БРУТАЛЬНЫЙ ПИГЛИН: ПОРТИТ БРОНЮ») |
| Здоровье брута | `piglin/PiglinBruteHealth.java` | `mobs/piglins/piglin.txt` (раздел «БРУТАЛЬНЫЙ ПИГЛИН: ЗДОРОВЬЕ») |

Service if the task is not about mob: `EvolvingThreats.java` — mod input, `command/EvolvingThreatsCommands.java` — test commands, `idea.txt` — general idea. Put new mechanics in a separate class; mixin — only call 1–2 methods.

Comments

- Explanatory language for the creator: **Russian**.
- Code (class names, methods): **English**.
- Explain: mixins, `EntitySpawnReason`, server vs client (`src/main` vs` src/client `) if this affects the edit.

## Assembly & Test

- Development: `.\gradlew runClient` (restart after changes` .java `).
- Jar for players: `.\gradlew build` → `build/libs/`.

## Cursor

Permanent rules: `.cursor/rules/explain-code-for-author.mdc` and `.cursor/rules/ask-dont-assume.mdc`.  
Heavy folders are closed in `.cursorignore`.