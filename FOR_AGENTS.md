# Инструкция для AI-агентов (Cursor и др.)

Проект: **Evolving Threats** — Fabric-мод для Minecraft.  
Автор **учится** и хочет **сам менять баланс и поведение**, не разбираясь глубоко в Java.

## Главное правило

**Всегда оставляй пояснения в коде**, как в `src/main/java/com/example/RandomWeapons.java`:

- блок **«НАСТРОЙКИ»** в начале класса с `public static final` и комментариями на русском;
- что меняется, если увеличить/уменьшить число;
- для шансов явно писать формат `0.0–1.0` и пример в процентах.

Агент **не должен** сливать всю логику в mixin без комментариев и без вынесенных констант.

## Куда смотреть

Одна задача — одна строка таблицы ниже и, если нужно поведение, **один** файл в `mobs/`. В заметке строка «Код:» уже перечисляет миксины. Папку `mixin/` целиком не обходить.

`entity/` и `entity/ai/` открывать только если класс механики ссылается на конкретный файл. `src/client` — только если задача про внешний вид.

Не читать `.gradle/`, `build/`, `run/logs/` (они в `.cursorignore`). `.\gradlew` не запускать и логи не вытаскивать, пока автор сам не попросил. Исходники Minecraft не открывать пачкой.

Пути в таблице — от `src/main/java/com/example/`.

### Общее для многих мобов

| В игре | Сначала этот файл | Заметка |
|--------|-------------------|---------|
| Случайное оружие и два оружия | `RandomWeapons.java` | `mobs/common/два-оружия.txt` |
| Случайная и крашеная кожаная броня | `RandomArmor.java` | `mobs/common/крашеная-кожаная-броня.txt` |
| Чары на оружии и броне | `MobEnchantments.java` | `mobs/common/чары-на-оружии.txt` |
| Щиты | `MobShields.java` | `mobs/common/щиты.txt` |
| Тотем бессмертия | `MobTotem.java` | `mobs/common/тотем-бессмертия.txt` |
| Зелья при появлении | `MobPotionBuffs.java` | `mobs/common/бафы-зелий.txt` |
| Случайное здоровье | `MobHealth.java` | `mobs/common/случайное-здоровье.txt` |

### Скелеты

| В игре | Сначала этот файл | Заметка |
|--------|-------------------|---------|
| Дальний быстрый лук | `skeleton/FastBowSkeleton.java` | `mobs/skeletons/скелет-дальний-лук.txt` |
| Маленький скелет | `skeleton/SmallSkeleton.java` | `mobs/skeletons/маленький-скелет.txt` |
| Вредная стрела | `skeleton/HarmfulArrowSkeleton.java` | `mobs/skeletons/скелет-вредная-стрела.txt` |
| «700 IQ», меч на спине | `skeleton/TacticianSkeleton.java` | `mobs/skeletons/скелет-700-iq.txt` |
| Раздатчик на голове, залп стрел | `RandomDispenserSkull.java` | заметки нет; цель — `entity/ai/DispenserBarrageGoal.java` |
| Спектральная стрела поднимает мобов | `skeleton/SpectralArrowHunt.java` | заметки нет |

### Зомби

| В игре | Сначала этот файл | Заметка |
|--------|-------------------|---------|
| Идут на свет | `light/LightSeeking.java` | `mobs/zombies/зомби-идут-на-свет.txt` |
| Ломают блоки к игроку | `digging/BlockBreaking.java` | `mobs/zombies/зомби-ломают-блоки.txt` |
| Помнит игрока и идёт за стену | `grudge/PlayerGrudge.java` | заметки нет; этим пользуются свет и копание |
| Маяк из тыквы | `beacon/JackLanternBeacon.java` | `mobs/zombies/маяк-тыквы.txt` |
| Носитель спавнера | `spawnerhead/SpawnerHeadMob.java` | `mobs/zombies/носитель-спавнера.txt` |
| Гнилой надсмотрщик | `overseer/ZombieOverseer.java` | `mobs/zombies/гнилой-надсмотрщик.txt` |

### Криперы, пауки, рейд

| В игре | Сначала этот файл | Заметка |
|--------|-------------------|---------|
| Метатель динамита | `creeper/DynamiteCreeper.java` | `mobs/creepers/крипер-метатель-динамита.txt` |
| Ломает щит взрывом | `creeper/ShieldBreakCreeper.java` | `mobs/creepers/крипер-ломает-щит.txt` |
| Подрывник | `creeper/SapperCreeper.java` | `mobs/creepers/крипер-подрывник.txt` |
| Ткач и матка | `spider/SpiderThreats.java` | `mobs/spiders/пауки-ткач-и-матка.txt` |
| Рой пауков | `spider/SpiderSwarm.java` | `mobs/spiders/рой-пауков.txt` |
| Рейдовый якорь | `raid/RaidAnchor.java` | `mobs/raids/рейдовый-якорь.txt` |

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
