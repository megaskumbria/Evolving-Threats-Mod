package com.example.illager;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

import java.util.ArrayList;
import java.util.List;

/**
 * Разбойник иногда заряжает в арбалет ракету.
 * Ракета кладётся в арбалет в момент, когда он начинает тянуть тетиву,
 * поэтому на арбалете сразу виден фейерверк.
 * Стрела в этот залп не заряжается.
 * Ракета со звездой взрывается при попадании и снимает здоровье.
 */
public final class PillagerFirework {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — разбойник всегда стреляет стрелой. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что очередная зарядка будет ракетой.
	 * 0.20 = 20%, примерно каждый пятый залп. Остальные залпы — обычная стрела.
	 * Бросок в начале зарядки. 0 = ракет нет. 1 = каждый выстрел ракета.
	 */
	public static final float SHOT_CHANCE = 0.20f;

	/**
	 * Сколько звёзд в ракете. От этого урон, без брони, на нормальной сложности.
	 * 1 = 7 здоровья (3,5 сердца). 2 = 9. 3 = 11. Каждая звезда после первой добавляет 2.
	 * 7 = предел, который можно собрать в игре, 19 здоровья.
	 * 0 = ракету не заряжать, остаётся стрела.
	 * На лёгкой удар слабее, на сложной сильнее. Броня уменьшает.
	 * Взрыв задевает всех рядом, около пяти блоков, в том числе самого разбойника.
	 */
	public static final int STAR_COUNT = 1;

	// ========================================================================

	/** Больше семи звёзд в обычной ракете не бывает. */
	private static final int MAX_STARS = 7;

	private PillagerFirework() {
	}

	/**
	 * Разбойник только начал тянуть арбалет.
	 * Ракета уже лежит в нём: модель арбалета показывает фейерверк всю зарядку.
	 * Игрока и пиглина не трогает.
	 */
	public static void onChargeStart(LivingEntity shooter, InteractionHand hand) {
		if (!(shooter instanceof Pillager) || shooter.level().isClientSide() || shooter.isUsingItem()) {
			return;
		}
		ItemStack crossbow = shooter.getItemInHand(hand);
		if (!crossbow.is(Items.CROSSBOW)) {
			return;
		}
		if (!ENABLED || SHOT_CHANCE <= 0.0f || STAR_COUNT <= 0) {
			clearRocket(crossbow);
			return;
		}
		if (shooter.getRandom().nextFloat() < SHOT_CHANCE) {
			crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(rocket())));
		} else {
			clearRocket(crossbow);
		}
	}

	/** Убирает ракету, оставшуюся с прошлого залпа, чтобы этот залп зарядил стрелу. */
	private static void clearRocket(ItemStack crossbow) {
		ChargedProjectiles charged = crossbow.get(DataComponents.CHARGED_PROJECTILES);
		if (charged != null && charged.contains(Items.FIREWORK_ROCKET)) {
			crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
		}
	}

	private static ItemStack rocket() {
		int stars = Math.clamp(STAR_COUNT, 1, MAX_STARS);
		List<FireworkExplosion> explosions = new ArrayList<>(stars);
		IntList color = IntList.of(DyeColor.RED.getFireworkColor());
		FireworkExplosion star = new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL, color, IntList.of(), false, false);
		for (int i = 0; i < stars; i++) {
			explosions.add(star);
		}
		ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
		rocket.set(DataComponents.FIREWORKS, new Fireworks(1, explosions));
		return rocket;
	}
}
