package com.example.threat;

/**
 * Единая таблица шкалы угрозы: с какого дня что включается и с каких значений всё начинается.
 * Правь здесь, не открывая классы мобов. Метки ФИКС/РАСТЁТ взяты из mobs.txt.
 *
 * <p>Как читать:
 * <ul>
 *   <li><b>«С N-го дня»</b> — до этого дня механики нет совсем, с него шанс ровно такой, как в классе моба
 *       (там он подписан «ФИКС»). Луна на это не влияет.</li>
 *   <li><b>«Стартовое значение»</b> — шанс в самом начале мира (день 0). К 100-му дню он растёт до числа
 *       в классе моба (там оно теперь называется «конец шкалы»). По дороге шанс меняет луна
 *       (см. {@link ThreatScale}).</li>
 * </ul>
 */
public final class ThreatTable {

	// ===================== С КАКОГО ДНЯ ВКЛЮЧАЕТСЯ (ступеньки) =====================

	/** Первая осада. Через сколько дней дальше — в {@code SiegeNight}. */
	public static final int SIEGE_FIRST_DAY = 25;

	/**
	 * С этого дня осада может быть «иллагерской» (разорители, разбойники, вызыватели, поборники).
	 * До него все осады из нежити и тварей. Шанс иллагерской осады — в {@code SiegeComposition}.
	 */
	public static final int SIEGE_ILLAGER_DAY = 40;

	/** Скелеты с луком Откидывание V–VI («чары сильнее ванильных»). До этого дня таких нет. */
	public static final int STRONG_BOW_DAY = 20;

	/** Зомби бегут на свет и ломают его. */
	public static final int LIGHT_SEEK_DAY = 40;

	/** Зомби ломают блоки к игроку. Скорость разрушения от дня не зависит. Осадные зомби тоже с этого дня. */
	public static final int BLOCK_BREAK_DAY = 40;

	/** Зомби-чародей. */
	public static final int SORCERER_DAY = 40;

	/** Зомби Excalibur: меч с Остротой 20. Шанс в {@code ZombieExcalibur}, от дня не растёт. */
	public static final int EXCALIBUR_DAY = 40;

	/**
	 * Spiky: обычный зомби или скелет, один кусок брони колет на 7–8 сердец сквозь броню.
	 * Шанс в {@code Spiky}, от дня не растёт.
	 */
	public static final int SPIKY_DAY = 40;

	/** Гнилой надсмотрщик. */
	public static final int OVERSEER_DAY = 60;

	/** Капитан скелетов с охраной. */
	public static final int SKELETON_CAPTAIN_DAY = 60;

	/** Бастион: обычный зомби или скелет с большим запасом здоровья. Шанс в {@code Bastion}, от дня не растёт. */
	public static final int BASTION_DAY = 60;

	/** Рейдовый якорь: с этого дня он может появиться сам (шанс и пауза — в RaidAnchorNatural). */
	public static final int RAID_ANCHOR_DAY = 80;

	/** Скелет Василиск. */
	public static final int BASILISK_DAY = 80;

	// ===================== СТАРТОВЫЕ ЗНАЧЕНИЯ (день 0) =====================

	/**
	 * «Опасные способности» (спавнер на голове, тактик, быстрый лук, динамит, тыква, раздатчик, вредные стрелы,
	 * лук Откидывание): в начале шанс = максимум × это число. 0.10 = в 10 раз меньше максимума.
	 * 0 = в начале игры способности не появляются совсем.
	 */
	public static final float DANGEROUS_START_RATIO = 0.10f;

	/** Любое оружие в руках: в начале шанс = максимум × это число. 0.25 = в четыре раза реже максимума. */
	public static final float WEAPON_START_RATIO = 0.25f;

	/** Щит: в начале шанс = максимум × это число. 0.10 = в десять раз реже. */
	public static final float SHIELD_START_RATIO = 0.10f;

	/** Тотем бессмертия: в начале шанс = максимум × это число. 0.05 = в двадцать раз реже. */
	public static final float TOTEM_START_RATIO = 0.05f;

	/** Шанс надеть кусок брони (на каждый слот) в начале игры. 0.08 = 8%. Конец шкалы — в {@code RandomArmor}. */
	public static final float ARMOR_PIECE_CHANCE_START = 0.08f;

	/**
	 * Шанс полного комплекта брони в начале игры. 0 = в первый день такого моба нет.
	 * Конец шкалы — {@code RandomArmor.FULL_SET_CHANCE}. Растёт вместе со шкалой и луной.
	 */
	public static final float FULL_ARMOR_SET_CHANCE_START = 0.0f;

	/** Шанс чар на оружии в руках в начале игры. 0.01 = 1%. Конец шкалы — в {@code MobEnchantments}. */
	public static final float WEAPON_ENCHANT_CHANCE_START = 0.01f;

	/** Шанс чар на куске брони в начале игры. 0.01 = 1%. */
	public static final float ARMOR_ENCHANT_CHANCE_START = 0.01f;

	/**
	 * Насколько уровень чары тяготеет к низким значениям в начале игры. 3.0 = высокие уровни почти не выпадают.
	 * 0.0 = все уровни равновероятны, меньше нуля — высокие даже чаще. Конец шкалы — в {@code MobEnchantments}.
	 */
	public static final double ENCHANT_LEVEL_BIAS_START = 3.0;

	/** Сколько чар на оружии в начале игры (вес: одна, две, три, четыре, пять). Конец — {@code COUNT_WEIGHTS}. */
	public static final double[] ENCHANT_COUNT_WEIGHTS_START = { 85, 11, 3, 1, 0 };

	/** Сколько чар на одном куске брони в начале игры (вес: одна, две, три, четыре). */
	public static final double[] ARMOR_ENCHANT_COUNT_WEIGHTS_START = { 90, 8, 2, 0 };

	/**
	 * Сколько зелий-бафов сразу в начале игры (вес: ни одного, одно, два, три, четыре, пять).
	 * Конец шкалы — {@code MobPotionBuffs.COUNT_WEIGHTS}.
	 */
	public static final double[] POTION_COUNT_WEIGHTS_START = { 96, 3, 1, 0, 0, 0 };

	/**
	 * Веса материала брони в начале игры: кожа, золото, кольчуга, медь, железо, алмаз.
	 * Алмаз 0.3 из 100 ≈ как «алмазный зомби в ванильной игре». Конец шкалы — {@code RandomArmor.MATERIAL_WEIGHTS}.
	 */
	public static final double[] ARMOR_MATERIAL_WEIGHTS_START = { 60, 20, 9, 6, 4.7, 0.3 };

	/**
	 * Веса материала оружия в начале игры: дерево, камень, золото, медь, железо, алмаз.
	 * Дерево и камень в руках зомби не такая уж редкость. Конец шкалы — {@code RandomWeapons.MATERIAL_WEIGHTS}.
	 */
	public static final double[] WEAPON_MATERIAL_WEIGHTS_START = { 50, 30, 10, 6, 3.7, 0.3 };

	/**
	 * Максимум здоровья зомби и скелета в начале игры. Минимум всегда 20 (как в ванили),
	 * максимум плавно растёт до {@code MobHealth.ZOMBIE_SKELETON_MAX} к 100-му дню.
	 */
	public static final int ZOMBIE_SKELETON_HEALTH_MAX_START = 20;

	// ===================== ИКОНКА НА ШКАЛЕ =====================

	/**
	 * С какого дня показывается каждый череп справа от шкалы (4 числа, по возрастанию):
	 * 0 — спящий зомби, затем зомби в шлеме, череп с красными глазами, череп в короне.
	 * По умолчанию пороги взяты из важных вех: первая осада, капитан и надсмотрщик, Василиск и якорь.
	 */
	public static final int[] STAGE_ICON_FROM_DAY = { 0, SIEGE_FIRST_DAY, SKELETON_CAPTAIN_DAY, BASILISK_DAY };

	private ThreatTable() {
	}
}
