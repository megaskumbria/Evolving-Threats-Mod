package com.example;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentTarget;

/**
 * Логика случайного оружия для мобов.
 * Миксины только вызывают методы отсюда — настройки и пояснения в одном месте.
 */
public final class RandomWeapons {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Шанс (0.0–1.0), что зомби появится с оружием в руке. 0.40 = 40%. */
	public static final float ZOMBIE_WEAPON_CHANCE = 0.40f;

	/**
	 * Шанс, что скелет появится с луком И вторым оружием (лук в основной руке, меч/топор/… во второй).
	 * 0.35 = 35%. На дальности стреляет, вплотную переключается на ближний бой.
	 */
	public static final float SKELETON_DUAL_WIELD_CHANCE = 0.35f;

	/**
	 * Шанс, что скелет появится ТОЛЬКО с ближним оружием (лук заменяется — как раньше).
	 * 0.20 = 20%. Не пересекается с dual wield: сначала бросок dual, потом этот.
	 */
	public static final float SKELETON_MELEE_ONLY_CHANCE = 0.20f;

	/**
	 * Шанс (0.0–1.0), что зомби появится с оружием в ОБЕИХ руках (меч и меч, топор и кирка и т.д.).
	 * 0.20 = 20%. Бросается раньше одного оружия, тотема и щита: вторая рука занята.
	 * 0 = двух оружий не будет.
	 */
	public static final float ZOMBIE_DUAL_MELEE_CHANCE = 0.20f;

	/**
	 * То же для скелета и странника. 0.20 = 20%.
	 * Лук при этом убирается: обе руки — ближний бой. Вредная стрела, тотем и щит не выдаются.
	 */
	public static final float SKELETON_DUAL_MELEE_CHANCE = 0.20f;

	/**
	 * Если игрок ближе этого расстояния (в блоках), скелет с луком+мечом НЕ стреляет (работает ближний бой).
	 * 4.0 — разумный порог; ближний удар всё равно только в радиусе удара (~2–3 блока, см. mixin).
	 */
	public static final float SKELETON_BOW_SUPPRESS_DISTANCE_BLOCKS = 4.0f;

	/**
	 * Чуть больше порога выше — лук снова включается, когда отбегаешь (гистерезис, меньше дёрганья).
	 * Должно быть >= SKELETON_BOW_SUPPRESS_DISTANCE_BLOCKS. 5.0 при suppress 4.0 — норм.
	 */
	public static final float SKELETON_BOW_RESUME_DISTANCE_BLOCKS = 5.0f;

	// ========================================================================

	/** Материалы оружия при спавне: только дерево…алмаз, без незерита. */
	private static final Item[][] MATERIALS = {
		{ Items.WOODEN_SWORD, Items.WOODEN_AXE, Items.WOODEN_SHOVEL, Items.WOODEN_PICKAXE, Items.WOODEN_HOE },
		{ Items.STONE_SWORD, Items.STONE_AXE, Items.STONE_SHOVEL, Items.STONE_PICKAXE, Items.STONE_HOE },
		{ Items.GOLDEN_SWORD, Items.GOLDEN_AXE, Items.GOLDEN_SHOVEL, Items.GOLDEN_PICKAXE, Items.GOLDEN_HOE },
		{ Items.COPPER_SWORD, Items.COPPER_AXE, Items.COPPER_SHOVEL, Items.COPPER_PICKAXE, Items.COPPER_HOE },
		{ Items.IRON_SWORD, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_PICKAXE, Items.IRON_HOE },
		{ Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_PICKAXE, Items.DIAMOND_HOE },
	};

	/** Веса материала оружия: дерево, камень, золото, медь, железо, алмаз (без незерита). */
	private static final int[] MATERIAL_WEIGHTS = {
		30, 25, 10, 10, 15, 10,
	};

	private RandomWeapons() {
	}

	/** Вызывается из SkeletonMixin при спавне. */
	public static void rollSkeletonLoadout(Mob mob) {
		RandomSource random = mob.getRandom();
		float roll = random.nextFloat();

		if (roll < SKELETON_DUAL_WIELD_CHANCE) {
			equipDualWieldArcher(mob);
			return;
		}

		if (roll < SKELETON_DUAL_WIELD_CHANCE + SKELETON_MELEE_ONLY_CHANCE) {
			equipMeleeOnly(mob);
		}
		// иначе — ванильный скелет только с луком
	}

	/**
	 * Лук остаётся в основной руке (ваниль уже выдала), случайное оружие — во второй.
	 */
	public static void equipDualWieldArcher(Mob mob) {
		Item weapon = pickRandomWeapon(mob.getRandom());
		mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(weapon));
	}

	/** Заменяет лук в основной руке на случайное оружие. */
	public static void equipMeleeOnly(Mob mob) {
		Item weapon = pickRandomWeapon(mob.getRandom());
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
	}

	/**
	 * Скелет с луком и вторым оружием (лук может быть в любой руке во время боя — мы меняем руки местами).
	 */
	public static boolean isDualWieldArcher(AbstractSkeleton skeleton) {
		return skeleton.isHolding(Items.BOW) && hasModMeleeWeapon(skeleton);
	}

	/** Есть ли у моба наше «инструментальное» оружие (не лук) в одной из рук. */
	public static boolean hasModMeleeWeapon(LivingEntity entity) {
		return isModMeleeWeapon(entity.getMainHandItem()) || isModMeleeWeapon(entity.getOffhandItem());
	}

	public static boolean isModMeleeWeapon(ItemStack stack) {
		if (stack.isEmpty() || stack.is(Items.BOW)) {
			return false;
		}
		Item item = stack.getItem();
		for (Item[] row : MATERIALS) {
			for (Item tool : row) {
				if (item == tool) {
					return true;
				}
			}
		}
		return false;
	}

	public static float bowSuppressDistanceSqr() {
		float d = SKELETON_BOW_SUPPRESS_DISTANCE_BLOCKS;
		return d * d;
	}

	public static float bowResumeDistanceSqr() {
		float d = SKELETON_BOW_RESUME_DISTANCE_BLOCKS;
		return d * d;
	}

	/**
	 * Лук «выключен» на близкой дистанции; с гистерезисом — снова включается, когда отбежал дальше resume.
	 */
	public static boolean shouldSuppressBow(AbstractSkeleton skeleton, LivingEntity target, boolean bowGoalWasActive) {
		double distSqr = skeleton.distanceToSqr(target);
		if (bowGoalWasActive) {
			return distSqr <= bowSuppressDistanceSqr();
		}
		return distSqr < bowResumeDistanceSqr();
	}

	/**
	 * Перед ударом в ближнем бою: меч в основную руку, лук во вторую (иначе урон шёл бы «луком»).
	 */
	public static void swapToMeleeHands(AbstractSkeleton skeleton) {
		if (!isDualWieldArcher(skeleton)) {
			return;
		}
		if (skeleton.getMainHandItem().is(Items.BOW)) {
			swapHands(skeleton);
		}
	}

	/** После ближнего боя: снова лук в основной руке. */
	public static void swapToRangedHands(AbstractSkeleton skeleton) {
		if (!skeleton.isHolding(Items.BOW)) {
			return;
		}
		if (skeleton.getOffhandItem().is(Items.BOW)) {
			swapHands(skeleton);
		}
	}

	private static void swapHands(LivingEntity entity) {
		ItemStack main = entity.getMainHandItem();
		ItemStack off = entity.getOffhandItem();
		entity.setItemSlot(EquipmentSlot.MAINHAND, off);
		entity.setItemSlot(EquipmentSlot.OFFHAND, main);
	}

	/**
	 * Два случайных оружия ближнего боя, каждое выбирается отдельно.
	 * Может совпасть (меч и меч) или разойтись (топор и кирка).
	 *
	 * @return true — обе руки заняты, щит, тотем и стрелу ставить не нужно
	 */
	public static boolean tryEquipDualMelee(Mob mob, float chance) {
		if (chance <= 0.0f) {
			return false;
		}
		RandomSource random = mob.getRandom();
		if (random.nextFloat() >= chance) {
			return false;
		}
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(pickRandomWeapon(random)));
		mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(pickRandomWeapon(random)));
		return true;
	}

	/**
	 * Сколько урона добавить с оружия во второй руке.
	 * Сила моба и оружие в основной руке сюда не входят: их игра уже посчитала.
	 * Острота со второй руки входит. 0, если во второй руке не наше оружие.
	 */
	public static float offhandMeleeDamage(ServerLevel level, Mob mob, Entity target) {
		ItemStack offhand = mob.getOffhandItem();
		if (!isModMeleeWeapon(offhand)) {
			return 0.0f;
		}
		float damage = weaponAttackDamage(offhand);
		var source = offhand.getDamageSource(mob);
		damage = EnchantmentHelper.modifyDamage(level, offhand, target, source, damage);
		damage += offhand.getItem().getAttackDamageBonus(target, damage, source);
		return Math.max(0.0f, damage);
	}

	/**
	 * После удара: огненный аспект со второй руки и износ этого оружия.
	 * Вызывать только если удар прошёл.
	 */
	public static void applyOffhandOnHit(ServerLevel level, Mob mob, Entity target) {
		ItemStack offhand = mob.getOffhandItem();
		if (!isModMeleeWeapon(offhand)) {
			return;
		}
		var source = offhand.getDamageSource(mob);
		EnchantmentHelper.runIterationOnItem(offhand, EquipmentSlot.MAINHAND, mob, (enchantment, enchantLevel, item) ->
				enchantment.value().doPostAttack(level, enchantLevel, item, EnchantmentTarget.ATTACKER, target, source));
		Weapon weapon = offhand.get(DataComponents.WEAPON);
		int wear = weapon != null ? weapon.itemDamagePerAttack() : 1;
		offhand.hurtAndBreak(wear, mob, EquipmentSlot.OFFHAND);
	}

	/** Урон, написанный на предмете. Он прописан для основной руки, во второй игра его сама не прибавляет. */
	private static float weaponAttackDamage(ItemStack weapon) {
		ItemAttributeModifiers modifiers = weapon.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		float[] damage = { 0.0f };
		modifiers.forEach(EquipmentSlotGroup.MAINHAND, (attribute, modifier) -> {
			if (attribute.equals(Attributes.ATTACK_DAMAGE) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
				damage[0] += (float) modifier.amount();
			}
		});
		return damage[0];
	}

	public static boolean tryEquip(Mob mob, float chance) {
		RandomSource random = mob.getRandom();
		if (random.nextFloat() >= chance) {
			return false;
		}
		equipMeleeOnly(mob);
		return true;
	}

	private static Item pickRandomWeapon(RandomSource random) {
		int totalWeight = 0;
		for (int weight : MATERIAL_WEIGHTS) {
			totalWeight += weight;
		}

		int roll = random.nextInt(totalWeight);
		int materialIndex = 0;
		for (int i = 0; i < MATERIAL_WEIGHTS.length; i++) {
			roll -= MATERIAL_WEIGHTS[i];
			if (roll < 0) {
				materialIndex = i;
				break;
			}
		}

		Item[] tools = MATERIALS[materialIndex];
		return tools[random.nextInt(tools.length)];
	}
}
