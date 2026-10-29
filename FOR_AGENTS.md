# Инструкция для AI-агентов (Cursor и др.)

Проект: **Evolving Threats** — Fabric-мод для Minecraft.  
Автор **учится** и хочет **сам менять баланс и поведение**, не разбираясь глубоко в Java.

## Главное правило

**Всегда оставляй пояснения в коде**, как в `src/main/java/com/example/RandomWeapons.java`:

- блок **«НАСТРОЙКИ»** в начале класса с `public static final` и комментариями на русском;
- что меняется, если увеличить/уменьшить число;
- для шансов явно писать формат `0.0–1.0` и пример в процентах.

Агент **не должен** сливать всю логику в mixin без комментариев и без вынесенных констант.

## Новый моб или новая способность: ВСЕГДА спрашивай про шкалу угрозы

В моде есть шкала нарастающей сложности (`threat/ThreatScale.java`, описание для автора — `mobs/common/шкала-угрозы.txt`).
Каждая механика относится к одному из четырёх типов, и **выбирает тип только автор**.

Когда придумываешь или добавляешь нового моба, способность, предмет мобу, шанс спавна или событие:

1. **Не решай сам**, как его привязать к шкале. Спроси автора (`AskQuestion`, по одному вопросу, по-русски, простым языком).
2. Предложи варианты и **свою рекомендацию** с короткой причиной (что изменится в игре):
   - **ФИКС** — шанс всегда такой, как записан в коде, от дней не зависит.
   - **ФИКС + «С N-го дня»** — до дня N механики нет совсем, потом шанс фиксированный (пороги в `threat/ThreatTable.java`).
   - **РАСТЁТ** — шанс плавно растёт от стартового значения в день 0 до значения в коде в день 100, на него влияет луна.
   - **РАСТЁТ + «С N-го дня»** — комбинация: нет до дня N, потом растёт.
3. Если выбрано РАСТЁТ — уточни стартовое значение (или предложи по умолчанию через `ThreatScale.dangerousChance`
   / `growingChance` и `ThreatTable.DANGEROUS_START_RATIO`) и вынеси его в `ThreatTable` с комментарием.
4. Если выбрано «С N-го дня» — добавь константу дня в `ThreatTable` и проверяй её через `ThreatScale.reached(level, ThreatTable.XXX_DAY)`.
5. Если автор не знает ответа — возьми рекомендованный вариант, вынеси числа в именованные константы и **явно напиши в ответе, что это допущение**.
6. После реализации обнови `mobs.txt` (метка ФИКС/РАСТЁТ у новой строки) и, если нужно, `mobs/common/шкала-угрозы.txt`.

Не оставляй новую механику «без метки»: по умолчанию шкалу не применяй молча, всегда уточняй.

## Куда смотреть

Одна задача — одна строка таблицы ниже и, если нужно поведение, **один** файл в `mobs/`. В заметке строка «Код:» уже перечисляет миксины. Папку `mixin/` целиком не обходить.

`entity/` и `entity/ai/` открывать только если класс механики ссылается на конкретный файл. `src/client` — только если задача про внешний вид.

Не читать `.gradle/`, `build/`, `run/logs/` (они в `.cursorignore`). `.\gradlew` не запускать и логи не вытаскивать, пока автор сам не попросил. Исходники Minecraft не открывать пачкой.

Пути в таблице — от `src/main/java/com/example/`.

### Общее для многих мобов

| В игре | Сначала этот файл | Заметка |
|--------|-------------------|---------|
| Шкала угрозы мира (день 0–100, луна, рост шансов) | `threat/ThreatScale.java` (формула; дни считаются от момента установки мода в мир — `ThreatData`/`ThreatEvents`), `threat/ThreatTable.java` (дни-пороги и стартовые значения) | `mobs/common/шкала-угрозы.txt` (описание для автора), `mobs.txt` (метки ФИКС/РАСТЁТ), `идея.txt` |
| Шкала угрозы на экране (HUD) | `src/client/.../client/threat/ThreatHud.java`, картинки делает `tools/prepare_threat_textures.py` из `art/threat_hud/` | — |
| Случайное оружие и два оружия | `RandomWeapons.java` | `mobs/common/два-оружия.txt` |
| Случайная и крашеная кожаная броня | `RandomArmor.java` | `mobs/common/крашеная-кожаная-броня.txt` |
| Полный комплект брони | `RandomArmor.java` (`FULL_SET_CHANCE`) | `mobs/common/полный-комплект-брони.txt` |
| Чары на оружии и броне | `MobEnchantments.java` | `mobs/common/чары-на-оружии.txt` |
| Щиты | `MobShields.java` | `mobs/common/щиты.txt` |
| Тотем бессмертия | `MobTotem.java` | `mobs/common/тотем-бессмертия.txt` |
| Зелья при появлении | `MobPotionBuffs.java` | `mobs/common/бафы-зелий.txt` |
| Случайное здоровье | `MobHealth.java` | `mobs/common/случайное-здоровье.txt` |
| Опыт за убийство усиленных | `MobExperience.java` | `mobs/common/опыт-за-убийство.txt` |
| Парящий инструмент | `tool/FloatingTool.java` | `mobs/common/парящий-инструмент.txt` |
| Повреждённый голем | `golem/DamagedGolem.java` | `mobs/golems/повреждённый-голем.txt` |
| Бастион (зомби или скелет) | `bastion/Bastion.java` | `mobs/common/бастион.txt` |
| Зеркало (возврат удара) | `mirror/Mirror.java` | `mobs/common/зеркало.txt` |

### Скелеты

| В игре | Сначала этот файл | Заметка |
|--------|-------------------|---------|
| Дальний быстрый лук | `skeleton/FastBowSkeleton.java` | `mobs/skeletons/скелет-дальний-лук.txt` |
| Маленький скелет | `skeleton/SmallSkeleton.java` | `mobs/skeletons/маленький-скелет.txt` |
| Вредная стрела | `skeleton/HarmfulArrowSkeleton.java` | `mobs/skeletons/скелет-вредная-стрела.txt` |
| Стрела выбивает вещи из рук | `skeleton/SkeletonDisarm.java` | `mobs/skeletons/скелет-выбивает-предмет.txt` |
| «700 IQ», меч на спине | `skeleton/TacticianSkeleton.java` | `mobs/skeletons/скелет-700-iq.txt` |
| Раздатчик на голове, залп стрел | `RandomDispenserSkull.java` | заметки нет; цель — `entity/ai/DispenserBarrageGoal.java` |
| Спектральная стрела поднимает мобов | `skeleton/SpectralArrowHunt.java` | заметки нет |
| Иссушитель со спавнером | `spawnerhead/SpawnerHeadMob.java` | `mobs/skeletons/иссушитель-со-спавнером.txt` |
| Капитан скелетов | `skeleton/SkeletonCaptain.java` | `mobs/skeletons/капитан-скелетов.txt` |
| Василиск | `skeleton/BasiliskSkeleton.java` | `mobs/skeletons/василиск.txt` |
| Скелет на костяной лошади | `skeleton/SkeletonHorseman.java` | `mobs/skeletons/скелет-наездник.txt` |
| Лук с Откидыванием V–VI | `skeleton/SkeletonPunchBow.java` | `mobs/skeletons/лук-откидывание.txt` |

### Зомби

| В игре | Сначала этот файл | Заметка |
|--------|-------------------|---------|
| Идут на свет | `light/LightSeeking.java` | `mobs/zombies/зомби-идут-на-свет.txt` |
| Ломают блоки к игроку | `digging/BlockBreaking.java` | `mobs/zombies/зомби-ломают-блоки.txt` |
| Помнит игрока и идёт за стену | `grudge/PlayerGrudge.java` | заметки нет; этим пользуются свет и копание |
| Маяк из тыквы | `beacon/JackLanternBeacon.java`, свет — `beacon/JackLanternLight.java` | `mobs/zombies/маяк-тыквы.txt` |
| Носитель спавнера | `spawnerhead/SpawnerHeadMob.java` | `mobs/zombies/носитель-спавнера.txt` |
| Гнилой надсмотрщик | `overseer/ZombieOverseer.java` | `mobs/zombies/гнилой-надсмотрщик.txt` |
| Зомби с динамитом | `zombie/TntZombie.java` | `mobs/zombies/зомби-с-динамитом.txt` |
| Зомби-чародей | `zombie/ZombieSorcerer.java` | `mobs/zombies/зомби-чародей.txt` |
| Зомби Excalibur | `zombie/ZombieExcalibur.java` | `mobs/zombies/зомби-экскалибур.txt` |
| Зомби Mjölnir | `zombie/ZombieMjolnir.java` | `mobs/zombies/зомби-мьельнир.txt` |
| Ритуальная стая | `ritual/RitualBand.java` | `mobs/zombies/ритуальная-стая.txt` |
| Утопленник | `drowned/DrownedThreats.java` | `mobs/zombies/утопленник.txt` |
| Удочка утопленника | `drowned/DrownedFishing.java` | `mobs/zombies/утопленник-удочка.txt` |
| Зомби ворует вещь | `zombie/ZombieTheft.java` | `mobs/zombies/зомби-ворует-предмет.txt` |

### Криперы, пауки, ведьмы, рейд

| В игре | Сначала этот файл | Заметка |
|--------|-------------------|---------|
| Метатель динамита | `creeper/DynamiteCreeper.java` | `mobs/creepers/крипер-метатель-динамита.txt` |
| Заряженный крипер | `creeper/ChargedCreeper.java` | `mobs/creepers/крипер-заряженный.txt` |
| Ломает щит взрывом | `creeper/ShieldBreakCreeper.java` | `mobs/creepers/крипер-ломает-щит.txt` |
| Подрывник | `creeper/SapperCreeper.java` | `mobs/creepers/крипер-подрывник.txt` |
| Супербыстрый крипер | `creeper/FastCreeper.java` | `mobs/creepers/крипер-быстрый.txt` |
| Ткач и матка | `spider/SpiderThreats.java` | `mobs/spiders/пауки-ткач-и-матка.txt` |
| Рой пауков | `spider/SpiderSwarm.java` | `mobs/spiders/рой-пауков.txt` |
| Яд пещерного паука может убить | `spider/CaveSpiderPoison.java` | `mobs/spiders/пещерный-паук-яд.txt` |
| Рейдовый якорь | `raid/RaidAnchor.java` | `mobs/raids/рейдовый-якорь.txt` |
| Осада (редкая ночь, волны, запрет сна и порталов) | `siege/SiegeNight.java`, `siege/SiegeWaves.java`, `siege/SiegeComposition.java` (фракции и доли мобов) | `mobs/raids/осада.txt` |
| Укус и стая фантомов | `phantom/PhantomThreats.java` | `mobs/phantoms/фантомы-укус-и-стая.txt` |
| Слабость с укуса фантома | `phantom/PhantomWeakness.java` | `mobs/phantoms/фантомы-слабость.txt` |
| Патруль у дома | `illager/IllagerPatrol.java` | `mobs/illagers/патруль-у-дома.txt` |
| Ракета разбойника | `illager/PillagerFirework.java` | `mobs/illagers/разбойник-фейерверк.txt` |
| Алмазный топор поборника | `illager/VindicatorDiamondAxe.java` | `mobs/illagers/поборник-алмазный-топор.txt` |
| Поборник рубит дерево | `illager/VindicatorWoodBreak.java` | `mobs/illagers/поборник-рубит-дерево.txt` |
| Разоритель пробивает стены | `illager/RavagerWallBreak.java` | `mobs/illagers/разоритель-пробивает-стены.txt` |
| Урон и слабость досаждателя | `vex/VexDamage.java` | `mobs/illagers/досаждатель-урон.txt` |
| Блейз-алхимик | `blaze/BlazeAlchemist.java` | `mobs/blazes/блейз-алхимик.txt` |
| Ведьма-обнулитель | `witch/WitchNullifier.java` | `mobs/witches/ведьма-обнулитель.txt` |
| Невидимость ведьмы | `witch/WitchInvisibility.java` | `mobs/witches/ведьма-невидимость.txt` |
| Зелья ведьмы в игрока | `witch/WitchThrownPotions.java` | `mobs/witches/ведьма-зелья.txt` |
| Яд слизней | `slime/SlimePoison.java` | `mobs/slimes/слизни-отравление.txt` |
| Магмовый куб | `magma/MagmaCubeThreats.java` | `mobs/slimes/магмовый-куб.txt` |
| Обмен местами эндермена | `enderman/EndermanSwap.java` | `mobs/endermen/обмен-местами.txt` |
| Чаще берёт и ставит блоки | `enderman/EndermanBlocks.java` | `mobs/endermen/блоки.txt` |
| Снаряды шалкера | `shulker/ShulkerThreats.java` | `mobs/shulkers/шалкеры-снаряды.txt` |
| Панцирь шалкера | `shulker/ShulkerShellDurability.java` | `mobs/shulkers/шалкер-панцирь.txt` |
| Удар брута по броне | `piglin/PiglinBruteArmorHit.java` | `mobs/piglins/брут-ломает-броню.txt` |
| Здоровье брута | `piglin/PiglinBruteHealth.java` | `mobs/piglins/брут-здоровье.txt` |

Служебное, если задача не про моба: `EvolvingThreats.java` — вход мода, `command/EvolvingThreatsCommands.java` — тестовые команды, `идея.txt` — общий замысел. Новую механику класть в отдельный класс; mixin — только вызов 1–2 методов.

## Комментарии

- Язык пояснений для автора: **русский**.
- Код (имена классов, методов): **английский**.
- Объяснять: mixins, `EntitySpawnReason`, сервер vs клиент (`src/main` vs `src/client`), если это влияет на правку.

## Сборка и тест

- Разработка: `.\gradlew runClient` (перезапуск после изменений `.java`).
- JAR для игроков: `.\gradlew build` → `build/libs/`.

## Cursor

Постоянные правила: `.cursor/rules/explain-code-for-author.mdc` и `.cursor/rules/ask-dont-assume.mdc`.  
Тяжёлые папки закрыты в `.cursorignore`.
