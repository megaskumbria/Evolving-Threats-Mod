package com.example;

import com.example.entity.TwoLeadHandsSkeleton;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
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
 * Random weapon logic for mobs.
 * Mixins only call methods from here — settings and explanations live in one place.
 */
public final class RandomWeapons {

	// ===================== SETTINGS (change these numbers) =====================

	/** Chance (0.0–1.0) that a zombie spawns with a weapon in hand. 0.40 = 40%. */
	public static final float ZOMBIE_WEAPON_CHANCE = 0.40f;

	/**
	 * Chance that a skeleton spawns with a bow AND a melee weapon.
	 * 0.35 = 35%. Right hand keeps the sword, axe, pickaxe, shovel, or hoe. Left hand keeps the bow.
	 * Up close he swings the right hand at once. Far away he shoots the bow in the left hand.
	 * He does not swap the two items, so he does not pause to decide.
	 * Captain and sniper do not use this chance: their bow stays in the right hand and they still swap.
	 * Tactician is separate too: his spare weapon hangs on his back.
	 */
	public static final float SKELETON_DUAL_WIELD_CHANCE = 0.35f;

	/**
	 * Chance that a skeleton spawns with melee ONLY (bow is replaced — as before).
	 * 0.20 = 20%. Does not overlap dual wield: dual roll first, then this one.
	 */
	public static final float SKELETON_MELEE_ONLY_CHANCE = 0.20f;

	/**
	 * Chance (0.0–1.0) that a zombie spawns with weapons in BOTH hands (sword and sword, axe and pickaxe, etc.).
	 * 0.20 = 20%. Rolled before single weapon, totem, and shield: offhand is taken.
	 * 0 = no dual weapons.
	 */
	public static final float ZOMBIE_DUAL_MELEE_CHANCE = 0.20f;

	/**
	 * Same for skeleton and stray. 0.20 = 20%.
	 * Bow is removed: both hands are melee. Harmful arrow, totem, and shield are not given.
	 */
	public static final float SKELETON_DUAL_MELEE_CHANCE = 0.20f;

	/**
	 * If the player is closer than this distance (blocks), a bow+sword skeleton does NOT shoot (melee takes over).
	 * 6.0 = he closes in from 6 blocks. The hit itself is still only in strike range (~2–3 blocks).
	 */
	public static final float SKELETON_BOW_SUPPRESS_DISTANCE_BLOCKS = 6.0f;

	/**
	 * Slightly above the threshold above — bow turns back on when you run away (hysteresis, less flicker).
	 * Must be >= SKELETON_BOW_SUPPRESS_DISTANCE_BLOCKS. 7.0 with suppress 6.0 is fine.
	 */
	public static final float SKELETON_BOW_RESUME_DISTANCE_BLOCKS = 7.0f;

	// ========================================================================

	/** Weapon materials on spawn: wood through diamond, no netherite. Floating tool picks items from here too. */
	private static final Item[][] MATERIALS = {
		{ Items.WOODEN_SWORD, Items.WOODEN_AXE, Items.WOODEN_SHOVEL, Items.WOODEN_PICKAXE, Items.WOODEN_HOE },
		{ Items.STONE_SWORD, Items.STONE_AXE, Items.STONE_SHOVEL, Items.STONE_PICKAXE, Items.STONE_HOE },
		{ Items.GOLDEN_SWORD, Items.GOLDEN_AXE, Items.GOLDEN_SHOVEL, Items.GOLDEN_PICKAXE, Items.GOLDEN_HOE },
		{ Items.COPPER_SWORD, Items.COPPER_AXE, Items.COPPER_SHOVEL, Items.COPPER_PICKAXE, Items.COPPER_HOE },
		{ Items.IRON_SWORD, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_PICKAXE, Items.IRON_HOE },
		{ Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_PICKAXE, Items.DIAMOND_HOE },
	};

	/**
	 * Material weights for weapons on day 100 (end of scale): wood, stone, gold, copper, iron, diamond (no netherite).
	 * At game start weights differ: ThreatTable.WEAPON_MATERIAL_WEIGHTS_START.
	 */
	private static final int[] MATERIAL_WEIGHTS = {
		30, 25, 10, 10, 15, 10,
	};

	private RandomWeapons() {
	}

	/** Called from SkeletonMixin on spawn. */
	public static void rollSkeletonLoadout(Mob mob) {
		RandomSource random = mob.getRandom();
		float roll = random.nextFloat();
		// GROWING: both chances above are for day 100; at game start they are lower (ThreatTable.WEAPON_START_RATIO).
		float dualWield = growing(mob, SKELETON_DUAL_WIELD_CHANCE);
		float meleeOnly = growing(mob, SKELETON_MELEE_ONLY_CHANCE);

		if (roll < dualWield) {
			equipTwoLeadHands(mob);
			return;
		}

		if (roll < dualWield + meleeOnly) {
			equipMeleeOnly(mob);
		}
		// otherwise — vanilla skeleton with bow only
	}

	/**
	 * Bow stays in the right hand (vanilla already gave it), random weapon in the left.
	 * Sniper uses this. He still swaps hands in a fight. Ordinary skeletons use {@link #equipTwoLeadHands}.
	 */
	public static void equipDualWieldArcher(Mob mob) {
		Item weapon = pickRandomWeapon(mob);
		mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(weapon));
	}

	/**
	 * Right hand: random melee weapon. Left hand: the bow he already held.
	 * Marked so combat does not swap the items. Shield is not added later: the left hand is full.
	 * If the right hand is not a bow, nothing changes (another set already took it).
	 */
	public static void equipTwoLeadHands(Mob mob) {
		if (!mob.getMainHandItem().is(Items.BOW)) {
			return;
		}
		// Mark first. Moving the bow re-checks his goals, and the mark tells them not to swap.
		if (mob instanceof TwoLeadHandsSkeleton data) {
			data.evolvingthreats$setTwoLeadHands(true);
		}
		// Same drop chance the bow already had. The sword keeps that chance on the right hand.
		float bowDrop = mob.getDropChances().byEquipment(EquipmentSlot.MAINHAND);
		ItemStack bow = mob.getMainHandItem().copy();
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(pickRandomWeapon(mob)));
		mob.setItemSlot(EquipmentSlot.OFFHAND, bow);
		mob.setDropChance(EquipmentSlot.OFFHAND, bowDrop);
	}

	/** Replaces the bow in main hand with a random weapon. */
	public static void equipMeleeOnly(Mob mob) {
		Item weapon = pickRandomWeapon(mob);
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
	}

	/**
	 * Skeleton with a bow and a melee weapon. Bow may sit in either hand.
	 * Two lead hands do not swap. Captain and sniper do.
	 */
	public static boolean isDualWieldArcher(AbstractSkeleton skeleton) {
		return skeleton.isHolding(Items.BOW) && hasModMeleeWeapon(skeleton);
	}

	/**
	 * True — melee stays in the right hand, bow stays in the left, combat does not swap them.
	 * False for captain, sniper, tactician, and skeletons saved before this mark existed.
	 */
	public static boolean isTwoLeadHands(AbstractSkeleton skeleton) {
		return skeleton instanceof TwoLeadHandsSkeleton data && data.evolvingthreats$isTwoLeadHands();
	}

	/**
	 * Bow is in the left hand and a sword, axe, pickaxe, shovel, or hoe is in the right.
	 * Used for the draw pose: the game only raises a bow held in the right hand by itself.
	 */
	public static boolean holdsBowInLeftHand(AbstractSkeleton skeleton) {
		return skeleton.getOffhandItem().is(Items.BOW) && isModMeleeWeapon(skeleton.getMainHandItem());
	}

	/** Whether the mob holds our tool-style weapon (not a bow) in either hand. */
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
	 * Bow is suppressed at close range; with hysteresis it turns back on when you move beyond resume distance.
	 */
	public static boolean shouldSuppressBow(AbstractSkeleton skeleton, LivingEntity target, boolean bowGoalWasActive) {
		double distSqr = skeleton.distanceToSqr(target);
		if (bowGoalWasActive) {
			return distSqr <= bowSuppressDistanceSqr();
		}
		return distSqr < bowResumeDistanceSqr();
	}

	/**
	 * Captain and sniper only: before a melee hit, sword moves to the right hand.
	 * Two lead hands already hold the sword there, so they skip this.
	 */
	public static void swapToMeleeHands(AbstractSkeleton skeleton) {
		if (isTwoLeadHands(skeleton) || !isDualWieldArcher(skeleton)) {
			return;
		}
		if (skeleton.getMainHandItem().is(Items.BOW)) {
			swapHands(skeleton);
		}
	}

	/**
	 * Captain and sniper only: after melee, bow returns to the right hand.
	 * Two lead hands keep the bow in the left hand and shoot from there.
	 */
	public static void swapToRangedHands(AbstractSkeleton skeleton) {
		if (isTwoLeadHands(skeleton) || !skeleton.isHolding(Items.BOW)) {
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
	 * Two random melee weapons, each picked separately.
	 * May match (sword and sword) or differ (axe and pickaxe).
	 *
	 * @return true — both hands taken; no shield, totem, or arrow needed
	 */
	public static boolean tryEquipDualMelee(Mob mob, float chance) {
		if (chance <= 0.0f) {
			return false;
		}
		RandomSource random = mob.getRandom();
		// chance — value on day 100; at game start the roll is lower.
		if (random.nextFloat() >= growing(mob, chance)) {
			return false;
		}
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(pickRandomWeapon(mob)));
		mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(pickRandomWeapon(mob)));
		return true;
	}

	/** GROWING: chance from {@code endChance × WEAPON_START_RATIO} (day 0) to {@code endChance} (day 100). */
	private static float growing(Mob mob, float endChance) {
		return ThreatScale.growingChance(mob.level(), endChance, ThreatTable.WEAPON_START_RATIO);
	}

	/**
	 * Extra damage from the weapon in offhand.
	 * Mob strength and main-hand weapon are not included: the game already counted those.
	 * Sharpness from offhand counts. 0 if offhand is not our weapon.
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
	 * After a hit: fire aspect from offhand and durability loss on that weapon.
	 * Call only if the hit landed.
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

	/** Damage shown on the item. It is defined for main hand; the game does not add it in offhand by itself. */
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
		// chance — value on day 100; at game start the roll is lower.
		if (random.nextFloat() >= growing(mob, chance)) {
			return false;
		}
		equipMeleeOnly(mob);
		return true;
	}

	/** Material by weights that smoothly shift from early threat scale to late. Weapon type (sword, axe…) — equal. */
	private static Item pickRandomWeapon(Mob mob) {
		RandomSource random = mob.getRandom();
		int materialIndex = ThreatScale.pickWeighted(
				mob.level(), random, ThreatTable.WEAPON_MATERIAL_WEIGHTS_START, MATERIAL_WEIGHTS);

		Item[] tools = MATERIALS[materialIndex];
		return tools[random.nextInt(tools.length)];
	}
}
