package com.example.zombie;

import com.example.MobEnchantments;
import com.example.MobNames;
import com.example.MobShields;
import com.example.RandomArmor;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import com.example.entity.RaidParticipantEntity;
import com.example.entity.ZombieExcaliburEntity;
import com.example.entity.ZombieOverseerEntity;
import net.minecraft.util.Mth;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 A rare named zombie Excalibur.
 In the hand is always a wooden, stone, copper, iron or diamond sword with sharpness 20.
 Armor and shield - like a normal zombie. The sword doesn't fall out.
 Instead, the tag with this name and his experience fall.
 *
 <p>Sharpness 20 is a normal punch, only stronger. There is no separate "crushing" reception.
 The game adds 1 damage for the first level and another 0.5 for each next: 20 = 10.5 above the sword.
 A naked player on the usual difficulty is about 8-10 hearts (diamond almost finishes).
 On a complex, any such sword kills the naked with one stroke. Armor is weaker.
 A full diamond set does not take off with one blow.
 */
public final class ZombieExcalibur {

	// ===================== SETTINGS (change these numbers) =====================

	/** Enable this mechanic. false — no such zombies spawn; commands cannot spawn them either. */
	public static final boolean ENABLED = true;

	/**
	 Chance (0.0-1.0) that a common zombie will be Excalibur. 0.01 = 1%.
	 FIXED: From day 40 (ThreatTable.EXCALIBUR_DAY), the threat scale and the moon are not affected.
	 * There are none until this day. Excalibur is not going anywhere.
	 This is the starting number, it is easy to twist. 0.002 = 0.2%, like an overseer. 0 = commands only.
	 Drowned, husk, zombie villager, cub, overseer and raid skip this roll.
	 Commands ignore day and spawn chance.
	 */
	public static final float SPAWN_CHANCE = 0.01f;

	/**
	 Sword sharpness level. 20. At the enchantment table is the ceiling V (5), here deliberately higher.
	 Enchantment damage: 1 for I level and +0.5 for each next. 20 gives 10.5 damage over and above the sword.
	 * 5 = normal sharpness V (plus 3 damage). 1 = Sharpness I.
	 */
	public static final int SHARPNESS_LEVEL = 20;

	/**
	 Health. 50 = 25 hearts. The usual zombies in this mod accidentally 20-40.
	 This number is put after that throw and always the same.
	 Less than 1 game kills almost immediately. Excalibur already living maintains old health.
	 */
	public static final float MAX_HEALTH = 50.0f;

	/**
	 Experience for killing. 30. It only falls if a zombie is killed by a player: this is how the game works.
	 * 0 = no experience. This number instead of the usual, the total multiplier does not increase it.
	 */
	public static final int XP_REWARD = 30;

	/**
	 * How many name tags always drop. 1 = one tag named Excalibur.
	 * 0 = do not drop the name tag. The sword does not depend on this number; it never falls.
	 */
	public static final int NAME_TAG_COUNT = 1;

	/** Display name and text on the name tag. */
	public static final String DISPLAY_NAME = "Excalibur";

	// ========================================================================

	/** Wood, stone, copper, iron, diamond. The chances are equal, there is no gold and no netherite. */
	private static final Item[] SWORDS = {
			Items.WOODEN_SWORD,
			Items.STONE_SWORD,
			Items.COPPER_SWORD,
			Items.IRON_SWORD,
			Items.DIAMOND_SWORD
	};

	private ZombieExcalibur() {
	}

	public static boolean isExcalibur(Zombie zombie) {
		return ENABLED
				&& zombie instanceof ZombieExcaliburEntity data
				&& data.evolvingthreats$isExcalibur();
	}

	/**
	 Called at end of zombie spawn. True = Excalibur: sword required, armor and shield as usual.
	 Accidental weapons, totem, pumpkin and spawner on the head are not issued.
	 Potions at the appearance could already hang themselves.
	 */
	public static boolean tryClaim(Zombie zombie, EntitySpawnReason reason) {
		if (!canBeExcalibur(zombie)) {
			return false;
		}
		ZombieExcaliburEntity data = (ZombieExcaliburEntity) zombie;
		if (data.evolvingthreats$isExcalibur()) {
			equip(zombie);
			return true;
		}
		if (reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.MOB_SUMMONED) {
			return false;
		}
		// Excalibur (ThreatTable.EXCALIBUR_DAY) The chance lower from the day does not change.
		if (!ThreatScale.reached(zombie.level(), ThreatTable.EXCALIBUR_DAY)) {
			return false;
		}
		if (zombie.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		data.evolvingthreats$setExcalibur(true);
		equip(zombie);
		return true;
	}

	/*How much experience to give. -1 is not Excalibur, let the game decide. */
	public static int experienceReward(Zombie zombie) {
		if (!isExcalibur(zombie)) {
			return -1;
		}
		return Math.max(0, XP_REWARD);
	}

	/*A name tag. The sword doesn't come in here. */
	public static void dropBonus(Zombie zombie, ServerLevel level) {
		if (!isExcalibur(zombie) || NAME_TAG_COUNT <= 0) {
			return;
		}
		ItemStack tag = new ItemStack(Items.NAME_TAG, NAME_TAG_COUNT);
		tag.set(DataComponents.CUSTOM_NAME, name());
		zombie.spawnAtLocation(level, tag);
	}

	/*Team: Excalibur in front of the player. null - the mechanics are off. */
	public static @Nullable Zombie spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return null;
		}
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		if (zombie == null) {
			throw new IllegalStateException("Failed to create Excalibur");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		zombie.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (zombie instanceof ZombieExcaliburEntity data) {
			data.evolvingthreats$setExcalibur(true);
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

	private static boolean canBeExcalibur(Zombie zombie) {
		if (!ENABLED || !(zombie instanceof ZombieExcaliburEntity)) {
			return false;
		}
		if (zombie.getType() != EntityTypes.ZOMBIE || zombie.isBaby()) {
			return false;
		}
		if (zombie instanceof ZombieOverseerEntity overseer && overseer.evolvingthreats$isOverseer()) {
			return false;
		}
		return !(zombie instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant());
	}

	private static void equip(Zombie zombie) {
		zombie.setCustomName(name());
		MobNames.showOnlyWhenLookedAt(zombie);
		// It doesn’t pick up a foreign thing; otherwise, the sharp 20 sword could change.
		zombie.setCanPickUpLoot(false);
		zombie.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);

		// Same armor as a normal zombie: pieces, a rare complete set, charms on armor.
		// The chance increases with the threat scale, like everyone else. That throw doesn't take up your hands.
		// We don't throw spells on the sword itself; they would erase sharp 20.
		RandomArmor.rollForZombie(zombie);
		MobEnchantments.rollArmorForZombie(zombie);
		// Shield in the second hand, with the same chance as a normal zombie. If it doesn't fall out, it's empty.
		MobShields.tryEquip(zombie);

		// The sword is at the very end, so that the armor and shield will not replace it.
		zombie.setItemSlot(EquipmentSlot.MAINHAND, sword(zombie));
		// 0 = sword does not fall, even with booty. Armor and shield fall at their usual chance.
		zombie.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		applyHealth(zombie);
	}

	private static void applyHealth(Zombie zombie) {
		if (MAX_HEALTH < 1.0f) {
			return;
		}
		var maxHealth = zombie.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null) {
			return;
		}
		maxHealth.setBaseValue(MAX_HEALTH);
		zombie.setHealth(MAX_HEALTH);
	}

	private static ItemStack sword(Zombie zombie) {
		Item item = SWORDS[zombie.getRandom().nextInt(SWORDS.length)];
		ItemStack stack = new ItemStack(item);
		if (!(zombie.level() instanceof ServerLevel level) || SHARPNESS_LEVEL <= 0) {
			return stack;
		}
		Holder<Enchantment> sharp = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.SHARPNESS);
		EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(sharp, SHARPNESS_LEVEL));
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
