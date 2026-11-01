package com.example.drowned;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 Drowning: turtle helmet, treatment in the water and charms on the trident, which has already given the game.
 Helmet and spells are solved once upon appearance. The treatment works for everyone while they are in the water.
 The new trident fashion does not create and does not take the rod. If the game has managed to hang its charms, the mod replaces them.
 */
public final class DrownedThreats {

	// ===================== SETTINGS (change these numbers) =====================

	/*False - drowned as in a normal game. */
	public static final boolean ENABLED = true;

	/**
	 Chance (0.0-1.0) to appear in a turtle helmet.
	 * 0.10 = 10%, approximately one in ten. 0 = no helmet. 1 = everyone's helmet.
	 If something is already on the head, the helmet is not worn.
	 */
	public static final float HELMET_CHANCE = 0.10f;

	/**
	 The level of charm "Protection" on the helmet. That's what damage resistance is.
	 * 2 = Protection II. 0 = helmet without enchantment, only its own armor remains.
	 4 = maximum game, Defense IV.
	 */
	public static final int HELMET_PROTECTION = 2;

	/**
	 * Chance (0.0-1.0) that the trident, which has already given the game, will be "Thunderer".
	 * Only count if the trident is already in hand. The new trident doesn't make fashion.
	 * 0.30 = 30%. The remaining 70% receive one other charm: Piercing, Loyalty, Tragon or Strength.
	 * 0 = "Thunderbolt" no, always another charm. 1 = "Thunderbolt" on each such trident.
	 */
	public static final float CHANNELING_CHANCE = 0.30f;

	/**
	 * Chance (0.0–1.0) that a turtle helmet will fall out on death.
	 0.085 = 8.5%, like a regular mob armor. 0 = not falling out. 1 = always falls out.
	 The trident is not affected: it was issued by the game, and he falls according to its rules.
	 */
	public static final float DROP_CHANCE = 0.085f;

	/**
	 How much health returns treatment in water. 2 = one heart.
	 * 0 = not treated in water.
	 */
	public static final float REGEN_HP = 2.0f;

	/**
	 How often treatment, in seconds. 3 = once every three seconds until drowned in water and not full.
	 On land, the timer stands and starts again when it goes back into the water.
	 */
	public static final int REGEN_SECONDS = 3;

	// ========================================================================

	/** 20 ticks in one second. */
	private static final int TICKS_PER_SECOND = 20;

	private DrownedThreats() {
	}

	/*Turtle helmet and charm on the already issued trident. It's not a rod. */
	public static void onSpawn(Drowned drowned) {
		if (!ENABLED || drowned.level().isClientSide()) {
			return;
		}
		if (!(drowned.level() instanceof ServerLevel level)) {
			return;
		}
		if (drowned.getRandom().nextFloat() < HELMET_CHANCE && drowned.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
			equipHelmet(level, drowned);
		}
		if (drowned.getMainHandItem().is(Items.TRIDENT)) {
			enchantTrident(level, drowned);
		}
	}

	/**
	 One tick. Returns how many tics to wait before treatment.
	 On land, the meter is reset, treatment does not go.
	 */
	public static int tickRegen(Drowned drowned, int ticksLeft) {
		int interval = Math.max(1, REGEN_SECONDS) * TICKS_PER_SECOND;
		if (!ENABLED || REGEN_HP <= 0.0f || REGEN_SECONDS <= 0 || drowned.level().isClientSide()) {
			return interval;
		}
		if (!drowned.isInWater() || !drowned.isAlive() || drowned.getHealth() >= drowned.getMaxHealth()) {
			return interval;
		}
		if (ticksLeft > 1) {
			return ticksLeft - 1;
		}
		drowned.heal(REGEN_HP);
		return interval;
	}

	/**
	 Abandoned trident has already hit the target and removed health.
	 Lightning hits the player only if the trident "Thunderbolt" - it hangs a mod, about every third such trident.
	 Then lightning strikes even without rain and under the roof.
	 In a thunderstorm under the open sky lightning causes the game itself, the second time do not hit.
	 */
	public static void onTridentHit(ThrownTrident trident, Entity target) {
		if (!ENABLED || !(target instanceof Player player) || !(trident.getOwner() instanceof Drowned)) {
			return;
		}
		if (!(trident.level() instanceof ServerLevel level) || !hasChanneling(level, trident.getWeaponItem())) {
			return;
		}
		boolean vanillaBolt = level.isThundering() && level.canSeeSky(player.blockPosition());
		if (vanillaBolt) {
			return;
		}
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt == null) {
			return;
		}
		bolt.snapTo(player.getX(), player.getY(), player.getZ());
		level.addFreshEntity(bolt);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 5.0f, 1.0f);
	}

	private static void equipHelmet(ServerLevel level, Drowned drowned) {
		ItemStack helmet = new ItemStack(Items.TURTLE_HELMET);
		int protection = Math.min(4, HELMET_PROTECTION);
		if (protection > 0) {
			Holder<Enchantment> enchantment = enchantment(level, Enchantments.PROTECTION);
			EnchantmentHelper.updateEnchantments(helmet, mutable -> mutable.set(enchantment, protection));
		}
		drowned.setItemSlot(EquipmentSlot.HEAD, helmet);
		drowned.setDropChance(EquipmentSlot.HEAD, Math.max(0.0f, DROP_CHANCE));
	}

	/**
	 One spell for a trident game. Their magic games, if they already were, are erased.
	 The level of the other charm is from I to its maximum, each level is equally likely.
	 "Thunderbolt" is only I.
	 */
	private static void enchantTrident(ServerLevel level, Drowned drowned) {
		ItemStack trident = drowned.getMainHandItem();
		if (!trident.is(Items.TRIDENT)) {
			return;
		}
		RandomSource random = drowned.getRandom();
		ResourceKey<Enchantment> key = random.nextFloat() < CHANNELING_CHANCE
				? Enchantments.CHANNELING
				: otherTridentEnchant(random.nextInt(4));
		Holder<Enchantment> holder = enchantment(level, key);
		int maxLevel = Math.max(1, holder.value().getMaxLevel());
		int levelValue = 1 + random.nextInt(maxLevel);
		EnchantmentHelper.updateEnchantments(trident, mutable -> {
			mutable.removeIf(ignored -> true);
			mutable.set(holder, levelValue);
		});
		drowned.setItemSlot(EquipmentSlot.MAINHAND, trident);
	}

	/*Piercing, Loyalty, Tiagun, Strength - equally. The Thunderbolt is not among them. */
	private static ResourceKey<Enchantment> otherTridentEnchant(int roll) {
		return switch (roll) {
			case 0 -> Enchantments.IMPALING;
			case 1 -> Enchantments.LOYALTY;
			case 2 -> Enchantments.RIPTIDE;
			default -> Enchantments.UNBREAKING;
		};
	}

	private static boolean hasChanneling(ServerLevel level, ItemStack stack) {
		if (!stack.is(Items.TRIDENT)) {
			return false;
		}
		return EnchantmentHelper.getItemEnchantmentLevel(enchantment(level, Enchantments.CHANNELING), stack) > 0;
	}

	private static Holder<Enchantment> enchantment(ServerLevel level, ResourceKey<Enchantment> key) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}
}
