# Evolving Threats

A Fabric mod that makes hostile mobs more dangerous as the world gets older.

A new world starts relatively calm. Across about **100 in-game days**, mobs gain better gear, stronger enchantments, potion effects, and special abilities. The moon changes how dangerous the night is. Some variants and events stay hidden until a set day.

The full list of mob changes is in **[Features](FEATURES.md)**.

## Requirements

- Minecraft **26.3**
- [Fabric Loader](https://fabricmc.net/) **0.19.5** or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java **25**

## Building

The project is built with the Gradle Wrapper (`gradlew`), the script in this folder. It uses the Gradle version the mod was made with, so Gradle does not need to be installed separately.

Windows (PowerShell):

```powershell
.\gradlew build
```

macOS and Linux:

```bash
./gradlew build
```

The mod jar is written to `build/libs/`.

A development client (with the game, so you can try the mod) starts with `.\gradlew runClient` on Windows, or `./gradlew runClient` on macOS and Linux.

Replay Mod is included only so videos can be recorded in that development client. It is not part of the mod jar, and players do not need to install it.

## Contributing

Pull requests are welcome. There is no separate contribution guide yet. A pull request should say what changes for the player.

GitHub builds every pull request with Java 25. That build needs to succeed before the change can be merged.

When you add or change a mechanic:

- Put chances, timers, damage, and similar numbers in named constants at the top of the class. Add a short English comment: what the number means, and the valid range. For a chance, `0.0–1.0`, with an example such as `0.40 = 40%`.
- Put new behavior in its own class. A mixin should only be the hook into Minecraft.
- Say how the mechanic sits on the threat scale:
  - **FIXED** — the chance stays as written and does not grow with days.
  - **FIXED from day N** — nothing until that day, then a fixed chance.
  - **GROWING** — the chance rises from day 0 toward day 100. The moon affects it.
  - **GROWING from day N** — nothing until that day, then it grows.

  If you are unsure which one fits, say so in the pull request. The author will choose.

## License

This mod is released under [CC0 1.0](LICENSE). You can use, change, and share it, including in other projects.
