package com.example;

import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * Случайные чары на оружии, инструментах и броне мобов (зомби, скелеты, разбойники).
 * Оружие: мечи, топоры, кирки, лопаты, мотыги, лук и арбалет. Броня: шлем, нагрудник, поножи, ботинки
 * у зомби и скелетов (разбойники брони не носят).
 *
 * <p>Как это устроено: после того как моб получил оружие и броню, для каждого предмета
 * бросается шанс «зачаровать». Если выпало — выбирается от 1 до 5 чар, подходящих именно этому предмету,
 * причём несовместимые между собой (острота + небесная кара, защита + защита от огня и т.п.)
 * вместе не попадают, как в ванильном Майнкрафте.
 * Уровни у чар могут быть вплоть до максимума (острота V на деревянном мече, защита IV на кожаном шлеме),
 * но высокие уровни выпадают редко.
 *
 * <p>Что реально работает в бою у моба: острота, огненный заговор, отбрасывание (ближний бой),
 * сила / отбрасывание / пламя (лук), быстрая перезарядка / многозарядность / пронзающая стрела (арбалет),
 * защита всех видов (моба труднее убить), шипы (бьют игрока в ответ), невесомость.
 * Небесная кара и бич членистоногих урон по игроку НЕ усиливают (игрок не нежить и не паук).
 * Остальное (добыча, эффективность, прочность, починка, проклятия, подводное дыхание...) на самом мобе
 * ничего не меняет — они нужны, чтобы выпавший предмет был интересным лутом.
 */
public final class MobEnchantments {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — мобы не получают чар (оружие по-прежнему выдаётся как раньше). */
	public static final boolean ENABLED = true;

	/*
	 * ВАЖНО про шансы ниже: это значения на 100-й день (КОНЕЦ шкалы угрозы).
	 * В начале игры шанс намного меньше (ThreatTable.WEAPON_ENCHANT_CHANCE_START / ARMOR_ENCHANT_CHANCE_START)
	 * и плавно растёт вместе со шкалой и луной. Так же растут число чар и их уровни.
	 * Механики с меткой ФИКС (парящий инструмент, капитан, рейд, ритуалы) растут не должны: они зовут
	 * rollHeldWeapons / rollArmorPieces со своим шансом, и там остаются ваши прежние числа (COUNT_WEIGHTS и LEVEL_BIAS_FIXED).
	 */

	/**
	 * Шанс (0.0–1.0), что КАЖДЫЙ предмет в руках зомби будет зачарован на 100-й день. 0.70 = 70%.
	 * Это шанс уже для зомби, у которого оружие есть (оно у ~40% зомби, см. RandomWeapons).
	 * 0 = у зомби чар не будет.
	 */
	public static final float ZOMBIE_ENCHANT_CHANCE = 0.70f;

	/**
	 * Шанс (0.0–1.0) для КАЖДОГО предмета в руках скелета/странника (лук и второе оружие бросаются отдельно).
	 * 0.70 = 70% на 100-й день. 0 = у скелетов чар не будет.
	 */
	public static final float SKELETON_ENCHANT_CHANCE = 0.70f;

	/**
	 * Шанс (0.0–1.0) для оружия разбойника (арбалет) и громилы (топор).
	 * 0.70 = 70% на 100-й день. В рейде у разбойника тот же шанс, но бросок после арбалета самой игры.
	 * Если выпало — чары мода заменяют быструю перезарядку рейда. Если нет — она остаётся.
	 * Громилу в рейде это не затрагивает: игра меняет ему топор уже после нашего броска.
	 * 0 = чар не будет.
	 */
	public static final float ILLAGER_ENCHANT_CHANCE = 0.70f;

	/**
	 * Шанс (0.0–1.0), что КАЖДЫЙ надетый кусок брони зомби будет зачарован на 100-й день. 0.65 = 65%.
	 * Считается отдельно для шлема, нагрудника, поножей и ботинок (броня сама выпадает у зомби с шансом 50% на слот,
	 * см. RandomArmor). 0 = у зомби брони с чарами не будет.
	 */
	public static final float ZOMBIE_ARMOR_ENCHANT_CHANCE = 0.65f;

	/** То же для брони скелетов и странников. 0.65 = 65% на каждый кусок. 0 = чар на броне скелетов не будет. */
	public static final float SKELETON_ARMOR_ENCHANT_CHANCE = 0.65f;

	/**
	 * Сколько чар сразу на одном предмете-ОРУЖИИ. Индекс 0 = одна чара, 1 = две, 2 = три и т.д. Числа — «вес» (шанс относительно остальных).
	 * {55, 28, 12, 4, 1} = в сумме 100: одна чара у 55%, две у 28%, три у 12%, четыре у 4%, пять у 1%.
	 * Хочешь только по одной чаре — поставь {1}. Больше пяти не добавляй без нужды: предмет станет «прокачанным мечом» из скрина.
	 * Если подходящих чар для предмета не хватает, будет меньше (например у мотыги их мало).
	 */
	public static final int[] COUNT_WEIGHTS = { 55, 28, 12, 4, 1 };

	/**
	 * То же самое для БРОНИ. Брони на мобе до четырёх штук сразу, поэтому по умолчанию чар на кусок немного меньше:
	 * {60, 28, 10, 2} = одна чара у 60%, две у 28%, три у 10%, четыре у 2%.
	 * Хочешь «просто защиту IV и всё» — поставь {1}.
	 */
	public static final int[] ARMOR_COUNT_WEIGHTS = { 60, 28, 10, 2 };

	/**
	 * Насколько уровень чары тяготеет к низким значениям — на 100-й день (конец шкалы угрозы).
	 * Для остроты (максимум V): 0.0 = все уровни равновероятны (V = 20%), 0.5 = V ≈ 12%,
	 * 1.0 = V ≈ 7%, 2.0 = V ≈ 2% (I ≈ 45%), 3.0 = V почти не встречается. Меньше нуля — высокие чаще низких (сейчас -0.3: V ≈ 26%, I ≈ 14%).
	 * В начале игры уклон сильнее (ThreatTable.ENCHANT_LEVEL_BIAS_START).
	 */
	public static final double LEVEL_BIAS = -0.3;

	/**
	 * Тот же уклон для механик с меткой ФИКС (парящий инструмент, капитан, рейд, ритуальные группы):
	 * они от шкалы не зависят, поэтому остаются на прежних 2.0. См. описание LEVEL_BIAS.
	 */
	public static final double LEVEL_BIAS_FIXED = 2.0;

	/**
	 * Какие чары вообще могут выпасть и как часто (вес; 0 = выключена).
	 * Из этого списка берутся только те, что подходят предмету (острота — мечам и топорам, сила — луку и т.д.),
	 * ванильная несовместимость (острота + кара, шёлковое касание + удача, ...) учитывается сама.
	 * Веса взяты из стола зачарований Майнкрафта, можно менять как угодно.
	 */
	private static final Candidate[] CANDIDATES = {
		// ---- мечи и топоры ----
		new Candidate(Enchantments.SHARPNESS, 10),           // Острота (до V): +урон
		new Candidate(Enchantments.SMITE, 5),                // Небесная кара (до V): урон по нежити, игрока не бьёт сильнее
		new Candidate(Enchantments.BANE_OF_ARTHROPODS, 5),   // Бич членистоногих (до V): урон по паукам, игрока не бьёт сильнее
		new Candidate(Enchantments.KNOCKBACK, 5),            // Отбрасывание (до II): откидывает игрока
		new Candidate(Enchantments.FIRE_ASPECT, 5),          // Заговор огня (до II): поджигает игрока
		new Candidate(Enchantments.LOOTING, 5),              // Добыча (до III): только для лута
		new Candidate(Enchantments.SWEEPING_EDGE, 5),        // Разящий клинок (до III): только для лута
		// ---- кирки, лопаты, мотыги, топоры ----
		new Candidate(Enchantments.EFFICIENCY, 10),          // Эффективность (до V): только для лута
		new Candidate(Enchantments.SILK_TOUCH, 1),           // Шёлковое касание: только для лута
		new Candidate(Enchantments.FORTUNE, 5),              // Удача (до III): только для лута
		// ---- лук ----
		new Candidate(Enchantments.POWER, 10),               // Сила (до V): +урон стрелы
		new Candidate(Enchantments.PUNCH, 2),                // Отбрасывание стрелы (до II)
		new Candidate(Enchantments.FLAME, 2),                // Горящая стрела
		new Candidate(Enchantments.INFINITY, 1),             // Бесконечность: только для лута
		// ---- арбалет ----
		new Candidate(Enchantments.QUICK_CHARGE, 5),         // Быстрая перезарядка (до III)
		new Candidate(Enchantments.MULTISHOT, 2),            // Многозарядный: три стрелы
		new Candidate(Enchantments.PIERCING, 10),            // Пронзающая стрела (до IV)
		// ---- броня ----
		new Candidate(Enchantments.PROTECTION, 10),          // Защита (до IV): моба труднее убить
		new Candidate(Enchantments.FIRE_PROTECTION, 5),      // Защита от огня (до IV): горит и от лавы почти не страдает
		new Candidate(Enchantments.BLAST_PROTECTION, 2),     // Защита от взрыва (до IV): крипер почти не бьёт
		new Candidate(Enchantments.PROJECTILE_PROTECTION, 5),// Защита от снарядов (до IV): стрелы почти не бьют
		new Candidate(Enchantments.THORNS, 3),               // Шипы (до III): бьют игрока в ответ на каждый удар
		new Candidate(Enchantments.FEATHER_FALLING, 5),      // Невесомость (до IV, ботинки): не получает урон от падения
		new Candidate(Enchantments.RESPIRATION, 2),          // Подводное дыхание (до III, шлем): только для лута
		new Candidate(Enchantments.AQUA_AFFINITY, 2),        // Подводник (шлем): только для лута
		new Candidate(Enchantments.DEPTH_STRIDER, 2),        // Подводная ходьба (до III, ботинки): быстрее в воде
		new Candidate(Enchantments.FROST_WALKER, 0),         // Ледоход (ботинки): ВЫКЛЮЧЕНА, моб замораживал бы воду под собой
		new Candidate(Enchantments.SOUL_SPEED, 1),           // Скорость души (до III, ботинки): быстрее по песку душ
		new Candidate(Enchantments.SWIFT_SNEAK, 1),          // Проворство (до III, поножи): только для лута
		new Candidate(Enchantments.BINDING_CURSE, 1),        // Проклятие несъёмности: надетую броню с выпавшего предмета не снять
		// ---- для всех ----
		new Candidate(Enchantments.UNBREAKING, 10),          // Прочность (до III): только для лута
		new Candidate(Enchantments.MENDING, 2),              // Починка: только для лута
		new Candidate(Enchantments.VANISHING_CURSE, 2),      // Проклятие утраты: только для лута
	};

	// ========================================================================

	private static final EquipmentSlot[] HAND_SLOTS = { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND };

	private static final EquipmentSlot[] ARMOR_SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
	};

	/** Чара из списка настроек выше: ключ в реестре игры и вес. */
	private record Candidate(ResourceKey<Enchantment> key, int weight) {
	}

	/** Чара, уже подтянутая из реестра игры и подходящая предмету. */
	private record Option(Holder<Enchantment> holder, int weight) {
	}

	private MobEnchantments() {
	}

	/** Вызывается из ZombieMixin после того, как зомби получил оружие. */
	public static void rollForZombie(Mob mob) {
		rollForHeldItems(mob, ZOMBIE_ENCHANT_CHANCE, true);
	}

	/** Вызывается из SkeletonMixin после того, как скелет получил лук/оружие. */
	public static void rollForSkeleton(Mob mob) {
		rollForHeldItems(mob, SKELETON_ENCHANT_CHANCE, true);
	}

	/** Вызывается из IllagerWeaponEnchantMixin для разбойников и громил. */
	public static void rollForIllager(Mob mob) {
		rollForHeldItems(mob, ILLAGER_ENCHANT_CHANCE, true);
	}

	/** Вызывается из ZombieMixin ПОСЛЕ того, как зомби получил броню (RandomArmor). */
	public static void rollArmorForZombie(Mob mob) {
		rollForArmor(mob, ZOMBIE_ARMOR_ENCHANT_CHANCE, true);
	}

	/** Вызывается из SkeletonMixin ПОСЛЕ того, как скелет получил броню (RandomArmor). */
	public static void rollArmorForSkeleton(Mob mob) {
		rollForArmor(mob, SKELETON_ARMOR_ENCHANT_CHANCE, true);
	}

	/**
	 * Шанс отдельно на каждый кусок брони. 0.45 = 45% на шлем, отдельно 45% на нагрудник и так далее.
	 * 0 = броня без чар. 1 = зачарован каждый кусок.
	 * Для механик с меткой ФИКС (капитан, рейд, ритуал): шанс и уровни от шкалы угрозы не зависят.
	 */
	public static void rollArmorPieces(Mob mob, float chancePerPiece) {
		rollForArmor(mob, chancePerPiece, false);
	}

	/** Шанс отдельно на каждый предмет в руках. 0 = оружие без чар. От шкалы угрозы не зависит (метка ФИКС). */
	public static void rollHeldWeapons(Mob mob, float chancePerItem) {
		rollForHeldItems(mob, chancePerItem, false);
	}

	/**
	 * Бросает шанс отдельно для каждого куска брони. Особые «головы» (спавнер, тыква, раздатчик) не трогает.
	 *
	 * @param growing true — {@code chance} это значение на 100-й день, в начале игры оно меньше (РАСТЁТ)
	 */
	private static void rollForArmor(Mob mob, float chance, boolean growing) {
		if (!ENABLED || chance <= 0.0f) {
			return;
		}
		float actual = growing
				? ThreatScale.lerp(mob.level(), ThreatTable.ARMOR_ENCHANT_CHANCE_START, chance)
				: chance;
		RandomSource random = mob.getRandom();
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack stack = mob.getItemBySlot(slot);
			if (!RandomArmor.isModArmor(stack) || random.nextFloat() >= actual) {
				continue;
			}
			if (enchant(mob, stack, random, true, growing)) {
				mob.setItemSlot(slot, stack);
			}
		}
	}

	/**
	 * Чары на меч в основной руке. Каменный и незеритовый тоже:
	 * обычный список оружия мода незерит не включает.
	 * {@code endChance} — шанс на 100-й день (0.70 = 70%).
	 * В начале он равен {@link ThreatTable#WEAPON_ENCHANT_CHANCE_START} (1%).
	 * Число чар и их уровни растут так же, как у меча зомби. Луна тоже влияет.
	 * Если чары выпали, они заменяют те, что игра уже повесила на этот меч.
	 */
	public static void rollMainHandGrowing(Mob mob, float endChance) {
		if (!ENABLED || endChance <= 0.0f) {
			return;
		}
		float actual = ThreatScale.lerp(mob.level(), ThreatTable.WEAPON_ENCHANT_CHANCE_START, endChance);
		ItemStack stack = mob.getMainHandItem();
		if (stack.isEmpty() || mob.getRandom().nextFloat() >= actual) {
			return;
		}
		if (enchant(mob, stack, mob.getRandom(), false, true)) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, stack);
		}
	}

	/** Бросает шанс отдельно для каждого предмета в основной и второй руке. {@code growing} — как у брони. */
	private static void rollForHeldItems(Mob mob, float chance, boolean growing) {
		if (!ENABLED || chance <= 0.0f) {
			return;
		}
		float actual = growing
				? ThreatScale.lerp(mob.level(), ThreatTable.WEAPON_ENCHANT_CHANCE_START, chance)
				: chance;
		RandomSource random = mob.getRandom();
		for (EquipmentSlot slot : HAND_SLOTS) {
			ItemStack stack = mob.getItemBySlot(slot);
			if (!isEnchantableWeapon(stack) || random.nextFloat() >= actual) {
				continue;
			}
			if (enchant(mob, stack, random, false, growing)) {
				// Кладём обратно на случай, если игра отдала копию, а не сам предмет.
				mob.setItemSlot(slot, stack);
			}
		}
	}

	/** Только наше оружие/инструменты (дерево…алмаз, железный топор громилы) и луки/арбалеты. Щиты, броню, трезубец не трогаем. */
	private static boolean isEnchantableWeapon(ItemStack stack) {
		return !stack.isEmpty()
				&& (RandomWeapons.isModMeleeWeapon(stack) || stack.is(Items.BOW) || stack.is(Items.CROSSBOW));
	}

	/**
	 * Заменяет чары предмета на случайный набор. Ванильные чары (если игра их уже выдала) стираются —
	 * иначе старая и новая чара могли бы быть несовместимы.
	 *
	 * @return true, если на предмет что-то повешено
	 */
	private static boolean enchant(Mob mob, ItemStack stack, RandomSource random, boolean armor, boolean growing) {
		List<Option> pool = buildPool(mob, stack);
		if (pool.isEmpty()) {
			return false;
		}

		// РАСТЁТ: и число чар, и их уровни зависят от шкалы угрозы. ФИКС: прежние значения.
		int[] endCounts = armor ? ARMOR_COUNT_WEIGHTS : COUNT_WEIGHTS;
		int wanted;
		double levelBias;
		if (growing) {
			double[] startCounts = armor
					? ThreatTable.ARMOR_ENCHANT_COUNT_WEIGHTS_START
					: ThreatTable.ENCHANT_COUNT_WEIGHTS_START;
			wanted = ThreatScale.pickWeighted(mob.level(), random, startCounts, endCounts) + 1;
			levelBias = ThreatScale.lerp(mob.level(), ThreatTable.ENCHANT_LEVEL_BIAS_START, LEVEL_BIAS);
		} else {
			wanted = pickCount(random, endCounts);
			levelBias = LEVEL_BIAS_FIXED;
		}
		List<Option> chosen = new ArrayList<>();
		while (chosen.size() < wanted && !pool.isEmpty()) {
			Option pick = pickWeighted(random, pool);
			chosen.add(pick);
			pool.remove(pick);
			// Выкидываем из пула всё, что несовместимо с выбранной чарой (острота ↔ кара ↔ бич и т.д.).
			pool.removeIf(other -> !Enchantment.areCompatible(pick.holder(), other.holder()));
		}

		EnchantmentHelper.updateEnchantments(stack, mutable -> {
			mutable.removeIf(holder -> true);
			for (Option option : chosen) {
				mutable.set(option.holder(), pickLevel(random, option.holder().value().getMaxLevel(), levelBias));
			}
		});
		return true;
	}

	/** Подходящие предмету чары из списка настроек. */
	private static List<Option> buildPool(Mob mob, ItemStack stack) {
		var registry = mob.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		List<Option> pool = new ArrayList<>();
		for (Candidate candidate : CANDIDATES) {
			if (candidate.weight() <= 0) {
				continue;
			}
			Holder<Enchantment> holder = registry.getOrThrow(candidate.key());
			if (holder.value().isSupportedItem(stack)) {
				pool.add(new Option(holder, candidate.weight()));
			}
		}
		return pool;
	}

	private static int pickCount(RandomSource random, int[] countWeights) {
		int total = 0;
		for (int weight : countWeights) {
			total += weight;
		}
		if (total <= 0) {
			return 1;
		}
		int roll = random.nextInt(total);
		for (int i = 0; i < countWeights.length; i++) {
			roll -= countWeights[i];
			if (roll < 0) {
				return i + 1;
			}
		}
		return 1;
	}

	private static Option pickWeighted(RandomSource random, List<Option> pool) {
		int total = 0;
		for (Option option : pool) {
			total += option.weight();
		}
		int roll = random.nextInt(total);
		for (Option option : pool) {
			roll -= option.weight();
			if (roll < 0) {
				return option;
			}
		}
		return pool.get(0);
	}

	/** Уровень от 1 до maxLevel; чем больше {@code bias}, тем реже высокие уровни (см. LEVEL_BIAS). */
	private static int pickLevel(RandomSource random, int maxLevel, double bias) {
		if (maxLevel <= 1) {
			return 1;
		}
		double[] weights = new double[maxLevel];
		double total = 0.0;
		for (int level = 1; level <= maxLevel; level++) {
			weights[level - 1] = Math.pow(maxLevel - level + 1, bias);
			total += weights[level - 1];
		}
		double roll = random.nextDouble() * total;
		for (int level = 1; level <= maxLevel; level++) {
			roll -= weights[level - 1];
			if (roll < 0.0) {
				return level;
			}
		}
		return 1;
	}
}
