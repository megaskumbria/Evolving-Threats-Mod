package com.example.skeleton;

import com.example.MobNames;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;

import com.example.entity.BasiliskSkeletonEntity;
import com.example.entity.RaidParticipantEntity;
import com.example.mixin.RangedBowAttackGoalAccessor;
import com.example.siege.SiegeNight;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 A rare Basilisk skeleton. The game does not add it: it is an ordinary skeleton in Neserite.
 With a bow and a black shield. The bow takes the player very far and sets fire to it.
 Bow, shield and armor don't fall. He always gets his name tag dropped.
 */
public final class BasiliskSkeleton {

	// ===================== SETTINGS (change these numbers) =====================

	/*False - new Basiliskis do not appear, the team does not spawn either. The living remain as they are. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0-1.0) that a normal skeleton will become a Basilisk.
	 0.002 = 0.2%, less than the captain (0.6%). The team doesn't give up that chance.
	 Skeleton spawner also throws this chance. Called for help and /summon - No.
	 */
	public static final float SPAWN_CHANCE = 0.002f;

	/** Display name and text on the name tag. English, like a captain. */
	public static final String NAME = "Basilisk";

	/**
	 Health. In an ordinary skeleton of 20 (10 hearts), a random fashion throw (20-40) is not left to him:
	 This number is placed after that throw and is always the same.
	 * 100 = 50 hearts. Less than 1 game kills almost immediately.
	 */
	public static final float MAX_HEALTH = 100.0f;

	/**
	 How much experience falls at death. 100. A normal skeleton makes 5.
	 * 0 = no experience. This is a number instead of the usual, not the top.
	 */
	public static final int XP_REWARD = 100;

	/**
	 How many tags with the name Basilisk always fall. 1 = one.
	 * 0 = do not drop the name tag. This does not affect the bow and armor: they do not fall.
	 */
	public static final int NAME_TAG_COUNT = 1;

	/**
	 * Chance (0.0-1.0) to drop the skeleton skull.
	 0.025 = 2.5%, like the skull of an exhumerator skeleton. 0 = no skull. 1 = falls always.
	 */
	public static final float SKULL_DROP_CHANCE = 0.025f;

	/** Burning arrow. 1 = there. 0 = the arrow doesn't burn. */
	public static final int BOW_FLAME_LEVEL = 1;

	/*Strength on the bow. 5 = V, vanilla ceiling. */
	public static final int BOW_POWER_LEVEL = 5;

	/**
	 * Onion reclining. Vanilla bow has a ceiling II, Captain IV, and a rare bow has a mod VI.
	 * 20 = XX. The player takes a lot further than Retraction VI.
	 */
	public static final int BOW_PUNCH_LEVEL = 20;

	/**
	 How far the player notices and does not lose it, in blocks.
	 A normal skeleton has about 16. 64 = four times farther: sees through the whole field if there is no wall.
	 It doesn't break through the wall. Captain's looking through the walls at 128, only direct view.
	 */
	public static final double SIGHT_BLOCKS = 64.0;

	/**
	 At what distance he stops and shoots, in blocks.
	 A normal skeleton has 15. 48 = starts firing from afar, does not fit closely.
	 * Must be no more {@link #SIGHT BLOCKS} otherwise he can't see the point from which he could shoot.
	 */
	public static final float ATTACK_RANGE_BLOCKS = 48.0f;

	/**
	 How many hits to the shield it takes to fly completely.
	 4 = the fourth closed arrow of Basilisk breaks the shield.
	 "Durability" on the shield, this number does not stretch: every hit that the shield recaptured is considered.
	 *Missing and hitting the shield are not considered. 0 = shield doesn't break. 1 = from the first hit.
	 */
	public static final int SHIELD_HITS_TO_BREAK = 4;

	/**
	 Arrow speed. A normal skeleton has 1.6, a long-range fast bow has 2.4.
	 * 3.2 = so that the arrow reaches a distance rather than falling along the way.
	 The sight adjusts itself: faster than the arrow - less lifted up.
	 */
	public static final float ARROW_SPEED = 3.2f;

	/*The speed of the arrow in a normal skeleton. It counts as a sight. */
	private static final float VANILLA_ARROW_SPEED = 1.6f;

	/*How much a normal skeleton lifts the arrow up. */
	private static final float VANILLA_ARC = 0.2f;

	// ========================================================================

	/*How many arrows has this shield already repulsed? It's on the shield, not the player. */
	private static final String SHIELD_HITS_KEY = "EvolvingThreatsBasiliskShieldHits";

	private static final EquipmentSlot[] ARMOR = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private BasiliskSkeleton() {
	}

	public static boolean isBasilisk(Mob mob) {
		return mob instanceof BasiliskSkeletonEntity data && data.evolvingthreats$isBasilisk();
	}

	/**
	 How hard to pick up an arrow. Counting from {@link #ARROW SPEED}.
	 1.6 is 0.2, like a normal skeleton.
	 */
	public static float arrowArc() {
		if (ARROW_SPEED <= 0.01f) {
			return VANILLA_ARC;
		}
		return (float) (VANILLA_ARC * Math.pow(VANILLA_ARROW_SPEED / ARROW_SPEED, 1.25));
	}

	/** true = it is Basilisk, the usual set of fashions he does not need. */
	public static boolean tryClaim(AbstractSkeleton skeleton, EntitySpawnReason reason) {
		if (!ENABLED || !(skeleton instanceof Skeleton) || !(skeleton instanceof BasiliskSkeletonEntity data)) {
			return false;
		}
		if (data.evolvingthreats$isBasilisk()) {
			equip(skeleton);
			return true;
		}
		if (reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.MOB_SUMMONED) {
			return false;
		}
		// On the night of the siege, mini-bosses don't show up.
		if (SiegeNight.isWindowOpen(skeleton.level())) {
			return false;
		}
		if (skeleton instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant()) {
			return false;
		}
		// From what day do Basiliskis even appear (ThreatTable.BASILISK DAY)? The chance lower from the day does not change.
		if (!ThreatScale.reached(skeleton.level(), ThreatTable.BASILISK_DAY)) {
			return false;
		}
		if (SPAWN_CHANCE <= 0.0f || skeleton.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		data.evolvingthreats$setBasilisk(true);
		equip(skeleton);
		return true;
	}

	/**
	 * Team. Appears dressed, there is no chance.
	 False - turned off or the skeleton could not be created.
	 */
	public static boolean spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return false;
		}
		Skeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
		if (skeleton == null) {
			return false;
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		skeleton.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (skeleton instanceof BasiliskSkeletonEntity data) {
			data.evolvingthreats$setBasilisk(true);
		}
		skeleton.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(skeleton.blockPosition()),
				EntitySpawnReason.COMMAND,
				(SpawnGroupData) null);
		if (lookAt != null) {
			skeleton.setTarget(lookAt);
		}
		level.addFreshEntity(skeleton);
		return true;
	}

	/** Range of sight and shot. Call after the game has re-chosen to shoot with a bow. */
	public static void applyBow(AbstractSkeleton skeleton, RangedBowAttackGoal<?> bowGoal) {
		if (!isBasilisk(skeleton)) {
			return;
		}
		setSight(skeleton);
		((RangedBowAttackGoalAccessor) bowGoal).evolvingthreats$setAttackRadiusSqr(ATTACK_RANGE_BLOCKS * ATTACK_RANGE_BLOCKS);
	}

	/*How much experience to give. -1 = not Basilisk, let the game decide. */
	public static int experienceReward(Mob mob) {
		if (!isBasilisk(mob)) {
			return -1;
		}
		return Math.max(0, XP_REWARD);
	}

	/*Name tag and rare skull. Onions and non-zerite don't come in here. */
	public static void dropBonus(Mob mob, ServerLevel level) {
		if (!isBasilisk(mob)) {
			return;
		}
		if (NAME_TAG_COUNT > 0) {
			ItemStack tag = new ItemStack(Items.NAME_TAG, NAME_TAG_COUNT);
			tag.set(DataComponents.CUSTOM_NAME, name());
			mob.spawnAtLocation(level, tag);
		}
		if (SKULL_DROP_CHANCE > 0.0f && mob.getRandom().nextFloat() < SKULL_DROP_CHANCE) {
			mob.spawnAtLocation(level, new ItemStack(Items.SKELETON_SKULL));
		}
	}

	/**
	 The shield has already repulsed that arrow. The fourth arrow destroys him completely.
	 The arrow of another skeleton does not fall here.
	 */
	public static void onShieldBlock(Player player, DamageSource source) {
		if (SHIELD_HITS_TO_BREAK <= 0 || !(player.level() instanceof ServerLevel)) {
			return;
		}
		if (!(source.getDirectEntity() instanceof AbstractArrow arrow)) {
			return;
		}
		if (!(arrow.getOwner() instanceof Mob owner) || !isBasilisk(owner)) {
			return;
		}
		InteractionHand hand = player.getUsedItemHand();
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(Items.SHIELD)) {
			return;
		}
		int hits = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr(SHIELD_HITS_KEY, 0) + 1;
		if (hits >= SHIELD_HITS_TO_BREAK) {
			ItemStack broken = stack.copy();
			player.setItemInHand(hand, ItemStack.EMPTY);
			player.onEquippedItemBroken(broken, hand.asEquipmentSlot());
			player.stopUsingItem();
			return;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(SHIELD_HITS_KEY, hits));
	}

	/** Before things fall out. Otherwise, prey can drop onions or nonzerite. */
	public static void preventDrop(Mob mob) {
		if (!isBasilisk(mob)) {
			return;
		}
		mob.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		mob.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
		for (EquipmentSlot slot : ARMOR) {
			mob.setDropChance(slot, 0.0f);
		}
	}

	private static void equip(AbstractSkeleton skeleton) {
		skeleton.setCustomName(name());
		MobNames.showOnlyWhenLookedAt(skeleton);
		skeleton.setCanPickUpLoot(false);
		skeleton.setPersistenceRequired();

		ItemStack bow = new ItemStack(Items.BOW);
		enchant(skeleton, bow, Enchantments.FLAME, BOW_FLAME_LEVEL);
		enchant(skeleton, bow, Enchantments.POWER, BOW_POWER_LEVEL);
		enchant(skeleton, bow, Enchantments.PUNCH, BOW_PUNCH_LEVEL);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow);
		// Black shield without flag pattern: base color only. Another color is DyeColor, like RED.
		// The blow on it with the chance of MobShields.BLOCK CHANCE (now 10%) is extinguished entirely, like a conventional fashion shield.
		// Shield does not fall: the chance of losing the second hand is lower than 0.
		ItemStack shield = new ItemStack(Items.SHIELD);
		shield.set(DataComponents.BASE_COLOR, DyeColor.BLACK);
		skeleton.setItemSlot(EquipmentSlot.OFFHAND, shield);

		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
		skeleton.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
		skeleton.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
		skeleton.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));

		skeleton.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		skeleton.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
		for (EquipmentSlot slot : ARMOR) {
			skeleton.setDropChance(slot, 0.0f);
		}
		var maxHealth = skeleton.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(MAX_HEALTH);
			skeleton.setHealth(MAX_HEALTH);
		}
		setSight(skeleton);
		skeleton.reassessWeaponGoal();
	}

	private static Component name() {
		return Component.literal(NAME).withStyle(Style.EMPTY.withItalic(false));
	}

	private static void setSight(AbstractSkeleton skeleton) {
		var follow = skeleton.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(SIGHT_BLOCKS);
		}
	}

	private static void enchant(Mob mob, ItemStack stack, ResourceKey<Enchantment> enchantment, int level) {
		if (level <= 0 || !(mob.level() instanceof ServerLevel)) {
			return;
		}
		var holder = mob.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment);
		EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(holder, level));
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
	}
}
