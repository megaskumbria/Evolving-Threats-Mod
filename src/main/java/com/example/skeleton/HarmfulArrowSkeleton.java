package com.example.skeleton;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.List;
import java.util.Optional;

/**
 * Скелет с одной вредной стрелой во второй руке. Лук в основной руке её и выпускает:
 * обычная стрела из воздуха ему больше не выдаётся, пока эта лежит во второй руке.
 * Ближний бой и щит такому скелету не выдаются — вторая рука занята стрелой.
 */
public final class HarmfulArrowSkeleton {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — такие скелеты не появляются. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный скелет или странник появится с вредной стрелой. 0.20 = 20%.
	 * Бросается раньше случайного меча и щита: если выпало, вторая рука занята стрелой.
	 */
	public static final float SPAWN_CHANCE = 0.20f;

	/**
	 * Во сколько раз дольше обычной стрелы с зельем длится эффект.
	 * У ванильной стрелы множитель 0.125: яд II держится около 2.7 сек.
	 * 0.25 = в два раза дольше, яд II около 5.4 сек. Мгновенный урон от длительности не зависит.
	 */
	public static final float EFFECT_DURATION_SCALE = 0.25f;

	/**
	 * Шанс, что стрела выпадет при смерти. 0.085 ≈ 8.5%, как броня.
	 * 1.0 = падает всегда.
	 * Слепота, иссушение, голод, тошнота и левитация этим шансом не пользуются: они не выпадают никогда.
	 */
	public static final float DROP_CHANCE = 0.085f;

	/**
	 * Сколько секунд слепоты чувствует игрок после попадания.
	 * В игре нет такой стрелы, длительность выбрана отдельно. 8 = 8 секунд почти без обзора.
	 */
	public static final int BLINDNESS_SECONDS = 8;

	/**
	 * Сколько секунд иссушения после попадания. 7 = чуть дольше сильного яда (около 5 сек).
	 * Уровень I: чёрные сердца тикают, но не так часто, как у визер-скелета на II.
	 */
	public static final int WITHER_SECONDS = 7;

	/**
	 * Голод после попадания, в минутах. Каждый скелет бросает своё число от минимума до максимума.
	 * 1 и 10 = от одной минуты до десяти. Уровень I: полоска еды уходит заметно, но не за секунды.
	 */
	public static final int HUNGER_MIN_MINUTES = 1;
	public static final int HUNGER_MAX_MINUTES = 10;

	/**
	 * Тошнота после попадания, в минутах. Тот же разброс, что у голода: экран качает от 1 до 10 минут.
	 */
	public static final int NAUSEA_MIN_MINUTES = 1;
	public static final int NAUSEA_MAX_MINUTES = 10;

	/**
	 * Сколько секунд левитации. 8 = игрока поднимает примерно на 8 блоков.
	 * Когда эффект кончится, падение уже бьёт, но в небо не уносит.
	 * Уровень I, как у шилкера, только короче: у шилкера около 10 секунд.
	 */
	public static final int LEVITATION_SECONDS = 8;

	// ========================================================================

	/**
	 * Вредные зелья. Обычная, долгая и сильная — разные стрелы.
	 * Полезные (сила, лечение, невидимость и т.д.) сюда не входят.
	 * Черепашья мощь тоже нет: вместе с замедлением она даёт защиту, игроку от неё легче.
	 * Спектральная стрела и эффекты без своего зелья добавляются отдельно в {@link #randomArrow}.
	 * Слепота красится цветом слабости, иссушение — цветом плетения.
	 * Голод, тошнота и левитация красятся своим цветом: оливковый, фиолетовый и бледно-голубой.
	 */
	private static final List<Holder<Potion>> HARMFUL_POTIONS = List.of(
			Potions.SLOWNESS,
			Potions.LONG_SLOWNESS,
			Potions.STRONG_SLOWNESS,
			Potions.HARMING,
			Potions.STRONG_HARMING,
			Potions.POISON,
			Potions.LONG_POISON,
			Potions.STRONG_POISON,
			Potions.WEAKNESS,
			Potions.LONG_WEAKNESS,
			Potions.WIND_CHARGED,
			Potions.WEAVING,
			Potions.OOZING,
			Potions.INFESTED
	);

	private HarmfulArrowSkeleton() {
	}

	/**
	 * Вызвать из спавна скелета до меча и щита.
	 *
	 * @return true — скелет получил стрелу, другое оружие во вторую руку ставить не нужно
	 */
	public static boolean tryEquip(Mob skeleton) {
		if (!ENABLED || SPAWN_CHANCE <= 0.0f) {
			return false;
		}
		RandomSource random = skeleton.getRandom();
		if (random.nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		if (!skeleton.getMainHandItem().is(Items.BOW)) {
			skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		}
		ItemStack arrow = randomArrow(random);
		skeleton.setItemSlot(EquipmentSlot.OFFHAND, arrow);
		skeleton.setDropChance(EquipmentSlot.OFFHAND, neverDrops(arrow) ? 0.0f : DROP_CHANCE);
		return true;
	}

	/** Случайная вредная стрела. Все варианты равновероятны, включая голод, тошноту и левитацию. */
	private static ItemStack randomArrow(RandomSource random) {
		int extra = 6;
		int roll = random.nextInt(HARMFUL_POTIONS.size() + extra);
		int special = roll - HARMFUL_POTIONS.size();
		if (special == 0) {
			return new ItemStack(Items.SPECTRAL_ARROW);
		}
		if (special == 1) {
			return effectArrow(MobEffects.BLINDNESS, BLINDNESS_SECONDS, MobEffects.WEAKNESS.value().getColor(), "blindness");
		}
		if (special == 2) {
			return effectArrow(MobEffects.WITHER, WITHER_SECONDS, MobEffects.WEAVING.value().getColor(), "wither");
		}
		if (special == 3) {
			int minutes = randomMinutes(random, HUNGER_MIN_MINUTES, HUNGER_MAX_MINUTES);
			return effectArrow(MobEffects.HUNGER, minutes * 60, MobEffects.HUNGER.value().getColor(), "hunger");
		}
		if (special == 4) {
			int minutes = randomMinutes(random, NAUSEA_MIN_MINUTES, NAUSEA_MAX_MINUTES);
			return effectArrow(MobEffects.NAUSEA, minutes * 60, MobEffects.NAUSEA.value().getColor(), "nausea");
		}
		if (special == 5) {
			return effectArrow(MobEffects.LEVITATION, LEVITATION_SECONDS, MobEffects.LEVITATION.value().getColor(), "levitation");
		}
		ItemStack arrow = PotionContents.createItemStack(Items.TIPPED_ARROW, HARMFUL_POTIONS.get(roll));
		arrow.set(DataComponents.POTION_DURATION_SCALE, EFFECT_DURATION_SCALE);
		return arrow;
	}

	/** Целые минуты от минимума до максимума, оба края входят. */
	private static int randomMinutes(RandomSource random, int minMinutes, int maxMinutes) {
		int min = Math.min(minMinutes, maxMinutes);
		int max = Math.max(minMinutes, maxMinutes);
		return min + random.nextInt(max - min + 1);
	}

	/**
	 * Стрела с эффектом, которого нет среди ванильных зелий.
	 * Цвет берётся у другой стрелы, секунды — уже те, что чувствует игрок.
	 */
	private static ItemStack effectArrow(Holder<MobEffect> effect, int seconds, int color, String name) {
		int storedTicks = Math.max(1, Math.round(seconds * 20.0f / EFFECT_DURATION_SCALE));
		ItemStack arrow = new ItemStack(Items.TIPPED_ARROW);
		arrow.set(DataComponents.POTION_CONTENTS, new PotionContents(
				Optional.empty(),
				Optional.of(color),
				List.of(new MobEffectInstance(effect, storedTicks)),
				Optional.of(name)));
		arrow.set(DataComponents.POTION_DURATION_SCALE, EFFECT_DURATION_SCALE);
		return arrow;
	}

	/**
	 * Стрелы без ванильного зелья со скелета не падают:
	 * слепота, иссушение, голод, тошнота, левитация.
	 */
	private static boolean neverDrops(ItemStack arrow) {
		PotionContents contents = arrow.get(DataComponents.POTION_CONTENTS);
		return contents != null && !contents.customEffects().isEmpty();
	}
}
