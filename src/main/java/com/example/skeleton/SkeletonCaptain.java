package com.example.skeleton;

import com.example.MobNames;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;

import com.example.MobEnchantments;
import com.example.entity.RaidParticipantEntity;
import com.example.entity.SkeletonSquadMember;
import com.example.siege.SiegeNight;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 Captain of skeletons and two assistants.
 Captain: Diamond armor, bow (force V, recline IV) and diamond sword
 (Sharpness V, Conspiracy of Fire II, Knockback II). Onions with recline IV in survival
 You can't get it, so it never falls off the captain.
 A complete iron set and an iron sword in each hand.
 Armor and swords of helpers are charmed by chance, piece by piece: there may be no charm,
 Maybe part, maybe all.
 The captain himself goes to the nearest player like a rotten overseer.
 The assistants do not move too far from the captain and do not fight their own.
 */
public final class SkeletonCaptain {

	// ===================== SETTINGS (change these numbers) =====================

	/** Turn on the mechanics. False - Captains do not appear, the team will not save the squad either. */
	public static final boolean ENABLED = true;

	/**
	 Chance (0.0-1.0) that a normal skeleton will appear as a captain and call two.
	 0.002 = 0.2% (was 0.6%, too often). The team doesn't give up that chance.
	 Skeleton spawner also throws this chance. Called backup and /summon team - No.
	 */
	public static final float SPAWN_CHANCE = 0.002f;

	/*How many assistants go out with the captain? 2 = two. */
	public static final int HELPER_COUNT = 2;

	/** Name overhead. It's a tag: as long as the name is there, the captain doesn't disappear like the mob. */
	public static final String CAPTAIN_NAME = "Captain of the Skeletons";

	/**
	 * Chance (0.0–1.0) to enchant EVERY piece of the captain’s armor.
	 * 0.45 = 45% for helmet, 45% for breastplate and so on.
	 Therefore, there is a bare diamond, there is one enchanted detail, there is the whole set.
	 * 0 = armor is always without charm. 1 = spell on each piece.
	 */
	public static final float ARMOR_ENCHANT_CHANCE = 0.45f;

	/**
	 * Chance (0.0-1.0) to enchant EVERY Iron Sword 0.55 = 55% per sword.
	 What charms and what level – like conventional weapons fashion (often low).
	 The sword and bow of the captain do not use this chance: their spells are set rigidly.
	 */
	public static final float HELPER_SWORD_ENCHANT_CHANCE = 0.55f;

	/** Lifting on the captain's bow. Vanilla onions have ceiling II. 4 = IV, purposely higher. */
	public static final int BOW_PUNCH_LEVEL = 4;

	/*Force on the captain's bow. 5 = V, this is the vanilla ceiling. */
	public static final int BOW_POWER_LEVEL = 5;

	/*The sharpness of the captain's sword. 5 = V. */
	public static final int SWORD_SHARPNESS_LEVEL = 5;

	/*A plot of fire on the captain's sword. 2 = II. */
	public static final int SWORD_FIRE_ASPECT_LEVEL = 2;

	/*Return on the captain's sword. 2 = II. */
	public static final int SWORD_KNOCKBACK_LEVEL = 2;

	/**
	 How far (blocks) an assistant can move away from the captain until there is a fight.
	 Next, catching up. 6 = held together.
	 */
	public static final double IDLE_FOLLOW_BLOCKS = 6.0;

	/**
	 Same in combat. 14 = the assistant has time to run to the player, but does not go after him
	 To the other end of the field while the captain stands and shoots.
	 */
	public static final double COMBAT_LEASH_BLOCKS = 14.0;

	/**
	 If one of the squad sees a target, the others pick it up until it's further away.
	 * this (blocks) from them. 32 = the whole squad enters, even if the player himself does not see.
	 */
	public static final double CALL_RANGE_BLOCKS = 32.0;

	/**
	 How far the captain himself goes after the player, blocks. 112 - like the chase radius of a rotten overseer.
	 The assistants have a FOLLOW RANGE: they do not look for the player themselves, they run for the captain.
	 */
	public static final double CAPTAIN_FOLLOW_RANGE = 112.0;

	/**
	 Next, the captain throws the fixed player, blocks. 128 is like an overseer.
	 Closer - goes to him, even if between them the wall and the captain has not yet seen him.
	 * Observer is not secured.
	 */
	public static final double HUNT_ABANDON_DISTANCE = 128.0;

	/**
	 True - the captain goes after the player in the creative. So the /etcaptain team shows the chase.
	 * False - like the overseer: creative does not fix. The observer doesn't touch it anyway.
	 */
	public static final boolean HUNT_CREATIVE = true;

	/*The speed at which the assistant catches up with the captain. 1.15 = slightly faster than the skeletal step. */
	public static final double GROUP_SPEED = 1.15;

	/*How far the assistant holds the target, blocks. A normal skeleton has about 16. 32 = does not lose a player next to the captain. */
	public static final double FOLLOW_RANGE = 32.0;

	// Captain's loot (helpers don't drop it)

	/**
	 Chance (0.0-1.0) that the captain will fall diamond sword with his charms.
	 * 0.15 = 15%. Onions don't come in here; they never fall.
	 */
	public static final float SWORD_DROP_CHANCE = 0.15f;

	/**
	 * Chance (0.0-1.0) on EVERY piece of Captain's Armor 0.085 ≈ 8.5%, like the rest of the armor.
	 The spell on the fallen piece is preserved.
	 */
	public static final float ARMOR_DROP_CHANCE = 0.085f;

	/**
	 * Chance (0.0-1.0) to drop 1 diamond. 0.20 = 20%.
	 It's beyond sword and armor.
	 */
	public static final float DIAMOND_DROP_CHANCE = 0.20f;

	/**
	 How many tags does the captain always drop? 1 = one tag.
	 * 0 = do not drop the name tag. The bow doesn't depend on that number.
	 */
	public static final int NAME_TAG_COUNT = 1;

	/** Chance of falling iron armor and swords of the assistant, on each item. 0.085 to 8.5%. */
	public static final float HELPER_DROP_CHANCE = 0.085f;

	// ========================================================================

	public static final int ROLE_NONE = 0;
	public static final int ROLE_CAPTAIN = 1;
	public static final int ROLE_HELPER = 2;

	private static final EquipmentSlot[] ARMOR = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private SkeletonCaptain() {
	}

	public static boolean isCaptain(Mob mob) {
		return ENABLED
				&& mob instanceof SkeletonSquadMember squad
				&& squad.evolvingthreats$getSquadRole() == ROLE_CAPTAIN;
	}

	/*Call at the end of the appearance of the skeleton. True = it's a captain or an assistant, they don't need the usual fashion set. */
	public static boolean tryClaim(AbstractSkeleton skeleton, ServerLevelAccessor level, EntitySpawnReason reason) {
		if (!ENABLED || !(skeleton instanceof Skeleton) || !(skeleton instanceof SkeletonSquadMember squad)) {
			return false;
		}
		if (squad.evolvingthreats$getSquadRole() == ROLE_CAPTAIN) {
			if (skeleton.getCustomName() == null) {
				equipCaptain(skeleton);
			}
			if (squad.evolvingthreats$getSquadMembers().isEmpty() && level instanceof ServerLevel server) {
				spawnHelpers(server, (Skeleton) skeleton);
			}
			return true;
		}
		if (squad.evolvingthreats$getSquadRole() == ROLE_HELPER) {
			if (!skeleton.getMainHandItem().is(Items.IRON_SWORD)) {
				equipHelper(skeleton);
			}
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
		// From what day do captains appear (ThreatTable.SKELETON CAPTAIN DAY)? The chance lower from the day does not change.
		if (!ThreatScale.reached(skeleton.level(), ThreatTable.SKELETON_CAPTAIN_DAY)) {
			return false;
		}
		if (skeleton.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		squad.evolvingthreats$setSquadRole(ROLE_CAPTAIN);
		squad.evolvingthreats$setSquadId(UUID.randomUUID());
		equipCaptain(skeleton);
		if (level instanceof ServerLevel server) {
			spawnHelpers(server, (Skeleton) skeleton);
			beginHunt(server, (Skeleton) skeleton, null);
		}
		return true;
	}

	/*Team: Captain and two assistants in front of the player. null - the mechanics are off. */
	public static @Nullable Skeleton spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return null;
		}
		Skeleton captain = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
		if (captain == null) {
			throw new IllegalStateException("Failed to create skeleton captain");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		captain.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (captain instanceof SkeletonSquadMember squad) {
			squad.evolvingthreats$setSquadRole(ROLE_CAPTAIN);
			squad.evolvingthreats$setSquadId(UUID.randomUUID());
		}
		captain.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(captain.blockPosition()),
				EntitySpawnReason.COMMAND,
				(SpawnGroupData) null);
		beginHunt(level, captain, lookAt);
		level.addFreshEntity(captain);
		return captain;
	}

	/** Who's holding the squad. A living captain, and if he's gone, he's the oldest living assistant. */
	public static @Nullable Skeleton anchor(Skeleton self, SkeletonSquadMember squad) {
		if (!(self.level() instanceof ServerLevel level)) {
			return null;
		}
		Skeleton captain = null;
		Skeleton eldest = null;
		for (UUID id : squad.evolvingthreats$getSquadMembers()) {
			if (!(level.getEntity(id) instanceof Skeleton member) || !member.isAlive()) {
				continue;
			}
			if (member instanceof SkeletonSquadMember data && data.evolvingthreats$getSquadRole() == ROLE_CAPTAIN) {
				captain = member;
			}
			if (eldest == null || member.getUUID().compareTo(eldest.getUUID()) < 0) {
				eldest = member;
			}
		}
		if (captain != null) {
			return captain;
		}
		return eldest;
	}

	/*The captain and his assistants. An alien squad and an ordinary skeleton are not their own. */
	public static boolean isSquadAlly(LivingEntity self, LivingEntity other) {
		if (!(self instanceof SkeletonSquadMember a) || !(other instanceof SkeletonSquadMember b)) {
			return false;
		}
		if (a.evolvingthreats$getSquadRole() == ROLE_NONE || b.evolvingthreats$getSquadRole() == ROLE_NONE) {
			return false;
		}
		UUID squadId = a.evolvingthreats$getSquadId();
		return squadId != null && squadId.equals(b.evolvingthreats$getSquadId());
	}

	/*End of server tick: the target of one skeleton becomes the target of the entire squad. We're not targeting our own. */
	public static void shareTarget(ServerLevel level, Skeleton self) {
		if (!(self instanceof SkeletonSquadMember squad) || squad.evolvingthreats$getSquadRole() == ROLE_NONE) {
			return;
		}
		if (self.getTarget() != null && isSquadAlly(self, self.getTarget())) {
			self.setTarget(null);
		}
		LivingEntity shared = null;
		for (UUID id : squad.evolvingthreats$getSquadMembers()) {
			if (!(level.getEntity(id) instanceof Skeleton member) || !member.isAlive()) {
				continue;
			}
			LivingEntity target = member.getTarget();
			if (target != null && target.isAlive() && !isSquadAlly(self, target)) {
				shared = target;
				break;
			}
		}
		if (shared == null || !self.canAttack(shared)) {
			return;
		}
		if (self.distanceToSqr(shared) > CALL_RANGE_BLOCKS * CALL_RANGE_BLOCKS) {
			return;
		}
		if (self.getTarget() != shared) {
			self.setTarget(shared);
		}
	}

	/** Captain's extra loot. The bow doesn't drop even here. */
	public static void dropBonus(ServerLevel level, Mob mob) {
		if (!isCaptain(mob)) {
			return;
		}
		ItemStack sword = diamondSword(mob);
		if (!sword.isEmpty() && mob.getRandom().nextFloat() < SWORD_DROP_CHANCE) {
			mob.spawnAtLocation(level, sword.copy());
		}
		if (NAME_TAG_COUNT > 0) {
			mob.spawnAtLocation(level, new ItemStack(Items.NAME_TAG, NAME_TAG_COUNT));
		}
		if (DIAMOND_DROP_CHANCE > 0.0f && mob.getRandom().nextFloat() < DIAMOND_DROP_CHANCE) {
			mob.spawnAtLocation(level, new ItemStack(Items.DIAMOND));
		}
	}

	private static void equipCaptain(AbstractSkeleton skeleton) {
		skeleton.setCustomName(Component.literal(CAPTAIN_NAME));
		MobNames.showOnlyWhenLookedAt(skeleton);
		skeleton.setCanPickUpLoot(false);
		skeleton.setPersistenceRequired();

		ItemStack bow = new ItemStack(Items.BOW);
		enchant(skeleton, bow, Enchantments.POWER, BOW_POWER_LEVEL);
		enchant(skeleton, bow, Enchantments.PUNCH, BOW_PUNCH_LEVEL);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow);

		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		enchant(skeleton, sword, Enchantments.SHARPNESS, SWORD_SHARPNESS_LEVEL);
		enchant(skeleton, sword, Enchantments.FIRE_ASPECT, SWORD_FIRE_ASPECT_LEVEL);
		enchant(skeleton, sword, Enchantments.KNOCKBACK, SWORD_KNOCKBACK_LEVEL);
		skeleton.setItemSlot(EquipmentSlot.OFFHAND, sword);

		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		skeleton.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		skeleton.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
		skeleton.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
		MobEnchantments.rollArmorPieces(skeleton, ARMOR_ENCHANT_CHANCE);

		skeleton.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		skeleton.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
		for (EquipmentSlot slot : ARMOR) {
			skeleton.setDropChance(slot, ARMOR_DROP_CHANCE);
		}
		setFollowRange(skeleton, CAPTAIN_FOLLOW_RANGE);
		skeleton.reassessWeaponGoal();
	}

	private static void equipHelper(AbstractSkeleton skeleton) {
		skeleton.setCanPickUpLoot(false);
		skeleton.setPersistenceRequired();
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		skeleton.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.IRON_SWORD));
		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		skeleton.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		skeleton.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		skeleton.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		MobEnchantments.rollHeldWeapons(skeleton, HELPER_SWORD_ENCHANT_CHANCE);
		MobEnchantments.rollArmorPieces(skeleton, ARMOR_ENCHANT_CHANCE);

		skeleton.setDropChance(EquipmentSlot.MAINHAND, HELPER_DROP_CHANCE);
		skeleton.setDropChance(EquipmentSlot.OFFHAND, HELPER_DROP_CHANCE);
		for (EquipmentSlot slot : ARMOR) {
			skeleton.setDropChance(slot, HELPER_DROP_CHANCE);
		}
		setFollowRange(skeleton, FOLLOW_RANGE);
		skeleton.reassessWeaponGoal();
	}

	private static void spawnHelpers(ServerLevel level, Skeleton captain) {
		if (!(captain instanceof SkeletonSquadMember squad)) {
			return;
		}
		List<Skeleton> helpers = new ArrayList<>();
		Vec3 side = sideVector(captain);
		int count = Math.max(0, HELPER_COUNT);
		for (int i = 0; i < count; i++) {
			double offset = (i - (count - 1) / 2.0) * 1.8;
			Vec3 pos = findStand(level, captain, captain.position().add(side.scale(offset)));
			Skeleton helper = EntityTypes.SKELETON.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (helper == null) {
				continue;
			}
			helper.snapTo(pos.x, pos.y, pos.z, captain.getYRot(), 0.0f);
			if (helper instanceof SkeletonSquadMember data) {
				data.evolvingthreats$setSquadRole(ROLE_HELPER);
				data.evolvingthreats$setSquadId(squad.evolvingthreats$getSquadId());
			}
			helper.finalizeSpawn(
					level,
					level.getCurrentDifficultyAt(helper.blockPosition()),
					EntitySpawnReason.MOB_SUMMONED,
					(SpawnGroupData) null);
			level.addFreshEntity(helper);
			helpers.add(helper);
		}
		List<UUID> ids = new ArrayList<>();
		ids.add(captain.getUUID());
		for (Skeleton helper : helpers) {
			ids.add(helper.getUUID());
		}
		remember(captain, ids);
		for (Skeleton helper : helpers) {
			remember(helper, ids);
		}
	}

	private static void remember(Skeleton skeleton, List<UUID> ids) {
		if (skeleton instanceof SkeletonSquadMember squad) {
			squad.evolvingthreats$setSquadMembers(new ArrayList<>(ids));
		}
	}

	private static Vec3 findStand(ServerLevel level, Skeleton captain, Vec3 wanted) {
		AABB box = captain.getDimensions(captain.getPose()).makeBoundingBox(wanted);
		if (level.noCollision(box)) {
			return wanted;
		}
		return captain.position();
	}

	private static Vec3 sideVector(Skeleton captain) {
		Vec3 look = captain.getLookAngle();
		Vec3 side = new Vec3(-look.z, 0.0, look.x);
		if (side.lengthSqr() < 1.0E-4) {
			return new Vec3(1.0, 0.0, 0.0);
		}
		return side.normalize();
	}

	private static ItemStack diamondSword(Mob mob) {
		if (mob.getMainHandItem().is(Items.DIAMOND_SWORD)) {
			return mob.getMainHandItem();
		}
		if (mob.getOffhandItem().is(Items.DIAMOND_SWORD)) {
			return mob.getOffhandItem();
		}
		return ItemStack.EMPTY;
	}

	private static void enchant(Mob mob, ItemStack stack, ResourceKey<Enchantment> enchantment, int level) {
		if (level <= 0 || !(mob.level() instanceof ServerLevel)) {
			return;
		}
		var holder = mob.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment);
		EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(holder, level));
	}

	private static void setFollowRange(AbstractSkeleton skeleton, double blocks) {
		var follow = skeleton.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(blocks);
		}
	}

	/**
	 * Assign the captain to the player. Prefer who the team has indicated.
	 Otherwise, the nearest live player in the range of the hunt, not in the creative and not in the observer.
	 */
	private static void beginHunt(ServerLevel level, Skeleton captain, @Nullable LivingEntity prefer) {
		if (!(captain instanceof SkeletonSquadMember squad)) {
			return;
		}
		Player player = null;
		if (prefer instanceof Player chosen && chosen.isAlive() && !chosen.isSpectator()) {
			player = chosen;
		}
		if (player == null) {
			player = findNearestPlayer(level, captain, HUNT_ABANDON_DISTANCE);
		}
		if (player == null) {
			return;
		}
		squad.evolvingthreats$setHuntTargetId(player.getUUID());
		captain.setTarget(player);
		for (UUID id : squad.evolvingthreats$getSquadMembers()) {
			if (level.getEntity(id) instanceof Skeleton member && member != captain) {
				member.setTarget(player);
			}
		}
	}

	/** Nearest player in radius. Observer passed. null - there's no one. */
	public static @Nullable Player tryLockNearestPlayer(Skeleton captain, SkeletonSquadMember squad) {
		if (!(captain.level() instanceof ServerLevel level)) {
			return null;
		}
		Player player = findNearestPlayer(level, captain, HUNT_ABANDON_DISTANCE);
		if (player == null) {
			return null;
		}
		squad.evolvingthreats$setHuntTargetId(player.getUUID());
		return player;
	}

	private static @Nullable Player findNearestPlayer(ServerLevel level, Skeleton skeleton, double maxDistance) {
		double maxDistSqr = maxDistance * maxDistance;
		AABB box = skeleton.getBoundingBox().inflate(maxDistance);
		List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, box,
				player -> player.isAlive()
						&& !player.isSpectator()
						&& (HUNT_CREATIVE || !player.isCreative()));
		return players.stream()
				.filter(player -> skeleton.distanceToSqr(player) <= maxDistSqr)
				.min(Comparator.comparingDouble(skeleton::distanceToSqr))
				.orElse(null);
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
