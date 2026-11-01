Generate Minecraft sources for browsing (slow the first time):
.\gradlew genSources

Run Minecraft with the mod:
.\gradlew runClient

================================================
To test dual-wield more often, temporarily set SKELETON_DUAL_WIELD_CHANCE = 1.0f.

About 1 in 390,625. Zombies and skeletons (and strays) share the same chance: 0.000256%.

Each of the four armor slots rolls separately. For a full diamond set, all four must pass two checks:

- the slot gets armor at all — 50%;
- diamond is chosen among materials — 8 of 100 (weights: leather 28, gold 12, chain 18, copper 12, iron 22, diamond 8).

One diamond slot: 50% × 8% = 4%. All four: 4%^4 = 1 / 390,625.

================================================
Tactician skeleton:
/ettactician

Patrol near home:
/etpatrol

Blaze alchemist:
/etalchemist

Nullifier witch:
/etwitch

Wither skeleton with spawner:
/etwitherspawner

Skeleton captain:
/etcaptain

Skeleton horseman:
/ethorseman

TNT zombie:
/ettnt

Sorcerer zombie:
/etsorcerer

Ritual band (random head, or player / creeper / piglin / wither):
/etritual
/etritual wither
/etritual creeper
/etritual piglin

Floating tool:
/ettool

Damaged golem:
/etgolem

Feint creeper (speed burst when close):
/etfeint
