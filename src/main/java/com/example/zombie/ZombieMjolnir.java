package com.example.zombie;

import com.example.MobEnchantments;
import com.example.MobNames;
import com.example.RandomArmor;
import com.example.entity.RaidParticipantEntity;
import com.example.entity.ZombieExcaliburEntity;
import com.example.entity.ZombieMjolnirEntity;
import com.example.entity.ZombieOverseerEntity;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Rare named zombie Mjölnir.
 * Holds a mace with Knockback XX. The enchantment table caps Knockback at II.
 * The mace almost never drops. A name tag always drops; kill XP is higher than a normal zombie.
 *
 * <p>Knockback 20 is a normal hit with much stronger knockback. Each level adds 1 to knockback strength.
 * Knockback II is 2; Knockback 20 is 20 — about ten times stronger: the player can be launched far away.
 */
public final class ZombieMjolnir {

	// ===================== SETTINGS (change these numbers) =====================

	/** Enable the mechanic. false — no Mjölnir zombies spawn; command spawn is disabled too. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) that a normal zombie becomes Mjölnir. 0.002 = 0.2%, one in five hundred.
	 * FIXED: from day one; does not scale with threat or moon.
	 * Excalibur uses 0.01 = 1%; this one is intentionally rarer. 0 = command only.
	 * Drowned, husk, zombie villager, baby, overseer, raid, and Excalibur never roll this.
	 * Command spawn ignores the chance.
	 */
	public static final float SPAWN_CHANCE = 0.002f;

	/**
	 * Knockback level on the mace. 20. Enchantment table cap is II (2).
	 * Each level adds 1 to knockback strength. 2 = normal Knockback II. 20 = ten times stronger.
	 * 1 = Knockback I. 0 = no knockback, only the mace's base hit.
	 */
	public static final int KNOCKBACK_LEVEL = 20;

	/**
	 * Chance (0.0–1.0) that the mace drops on death with its enchantments. 0.005 = 0.5%, one in two hundred.
	 * Intentionally very rare: skeleton captain's sword drops at 15% here.
	 * 0 = mace never drops. 1 = always drops. Looting does not affect this roll.
	 */
	public static final float HAMMER_DROP_CHANCE = 0.005f;

	/**
	 * Kill XP. 20 — four times a normal zombie (5), same idea as Excalibur.
	 * Only if the player killed the zombie (vanilla rule).
	 * 0 = no XP. This value replaces normal mob XP; it is not added on top.
	 */
	public static final int XP_REWARD = 20;

	/**
	 * Name tags that always drop. 1 = one tag reading Mjölnir.
	 * 0 = no tag. Does not control whether the mace drops.
	 */
	public static final int NAME_TAG_COUNT = 1;

	/** Name above the head and text on the name tag. */
	public static final String DISPLAY_NAME = "Mjölnir";

	// ========================================================================

	private ZombieMjolnir() {
	}

	public static boolean isMjolnir(Zombie zombie) {
		return ENABLED
				&& zombie instanceof ZombieMjolnirEntity data
				&& data.evolvingthreats$isMjolnir();
	}

	/**
	 * End of zombie spawn. true = Mjölnir: no random weapon, shield, totem, or head gear.
	 * Armor rolls like a normal zombie. Spawn potions may already be applied.
	 */
	public static boolean tryClaim(Zombie zombie, EntitySpawnReason reason) {
		if (!canBeMjolnir(zombie)) {
			return false;
		}
		ZombieMjolnirEntity data = (ZombieMjolnirEntity) zombie;
		if (data.evolvingthreats$isMjolnir()) {
			equip(zombie);
			return true;
		}
		if (reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.MOB_SUMMONED) {
			return false;
		}
		if (zombie.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		data.evolvingthreats$setMjolnir(true);
		equip(zombie);
		return true;
	}

	/** XP to award. -1 = not Mjölnir; let vanilla decide. */
	public static int experienceReward(Zombie zombie) {
		if (!isMjolnir(zombie)) {
			return -1;
		}
		return Math.max(0, XP_REWARD);
	}

	/** Name tag always. Mace — separate rare roll in dropBonus, same enchantments. */
	public static void dropBonus(Zombie zombie, ServerLevel level) {
		if (!isMjolnir(zombie)) {
			return;
		}
		if (NAME_TAG_COUNT > 0) {
			ItemStack tag = new ItemStack(Items.NAME_TAG, NAME_TAG_COUNT);
			tag.set(DataComponents.CUSTOM_NAME, name());
			zombie.spawnAtLocation(level, tag);
		}
		if (HAMMER_DROP_CHANCE <= 0.0f || zombie.getRandom().nextFloat() >= HAMMER_DROP_CHANCE) {
			return;
		}
		ItemStack hammer = zombie.getMainHandItem();
		if (!hammer.isEmpty()) {
			zombie.spawnAtLocation(level, hammer.copy());
		}
	}

	/** Command spawn: Mjölnir in front of the player. null if {@link #ENABLED} is false. */
	public static @Nullable Zombie spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return null;
		}
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		if (zombie == null) {
			throw new IllegalStateException("Failed to create Mjölnir");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		zombie.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (zombie instanceof ZombieMjolnirEntity data) {
			data.evolvingthreats$setMjolnir(true);
		}
		zombie.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(zombie.blockPosition()),
				EntitySpawnReason.COMMAND,
				(SpawnGroupData) null);
		if (lookAt != null) {
			zombie.setTarget(lookAt);
		}
		level.addFreshEntity(zombie);
		return zombie;
	}

	private static boolean canBeMjolnir(Zombie zombie) {
		if (!ENABLED || !(zombie instanceof ZombieMjolnirEntity)) {
			return false;
		}
		if (zombie.getType() != EntityTypes.ZOMBIE || zombie.isBaby()) {
			return false;
		}
		if (zombie instanceof ZombieOverseerEntity overseer && overseer.evolvingthreats$isOverseer()) {
			return false;
		}
		if (zombie instanceof ZombieExcaliburEntity excalibur && excalibur.evolvingthreats$isExcalibur()) {
			return false;
		}
		return !(zombie instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant());
	}

	private static void equip(Zombie zombie) {
		zombie.setCustomName(name());
		MobNames.showOnlyWhenLookedAt(zombie);
		// Named mobs do not despawn. No pickup — otherwise the mace could be replaced.
		zombie.setCanPickUpLoot(false);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, hammer(zombie));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		// 0 = vanilla never drops the mace, even with Looting. dropBonus handles the rare drop.
		zombie.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		zombie.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
		// Same armor as a normal zombie. Hands are not touched by armor rolls.
		RandomArmor.rollForZombie(zombie);
		MobEnchantments.rollArmorForZombie(zombie);
	}

	private static ItemStack hammer(Zombie zombie) {
		ItemStack stack = new ItemStack(Items.MACE);
		if (!(zombie.level() instanceof ServerLevel level) || KNOCKBACK_LEVEL <= 0) {
			return stack;
		}
		Holder<Enchantment> knockback = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.KNOCKBACK);
		EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(knockback, KNOCKBACK_LEVEL));
		return stack;
	}

	private static Component name() {
		return Component.literal(DISPLAY_NAME).withStyle(Style.EMPTY.withItalic(false));
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
