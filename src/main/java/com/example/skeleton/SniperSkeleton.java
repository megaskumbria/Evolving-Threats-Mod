package com.example.skeleton;

import com.example.RandomWeapons;
import com.example.entity.SniperSkeletonEntity;
import com.example.mixin.RangedBowAttackGoalAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 Skeleton sniper. Shoots from a long distance, the bow is not removed from the hands.
 In the second hand is a common mob weapon. The armor and charms are like a normal skeleton.
 A fast bow can fall separately: then it stretches more often, but the range remains sniper.
 */
public final class SniperSkeleton {

	// ===================== SETTINGS (change these numbers) =====================

	/** Turn on the mechanics. False - snipers do not appear, the team does not spawn. */
	public static final boolean ENABLED = true;

	/**
	 Chance (0.0-1.0) that an ordinary skeleton or wanderer will be a sniper.
	 0.002 = 0.2%, one in five hundred.
	 FIXED: From day one, the threat scale and the moon do not rise.
	 * 0 = commands only.
	 */
	public static final float SPAWN_CHANCE = 0.002f;

	/**
	 From what distance it shoots, in blocks. A normal skeleton has 15, a distant bow has 30.
	 * 128 = eight chanks. If mobs fall asleep in the world, they won't shoot until you get there.
	 */
	public static final float ATTACK_RANGE_BLOCKS = 128.0f;

	/**
	 How far the player notices, in blocks. Must be longer than the onion range,
	 Otherwise, he does not see a target he could shoot at. 140 = just over 128.
	 */
	public static final double FOLLOW_RANGE = 140.0;

	/**
	 Arrow speed. Normal skeleton 1.6, far bow 2.4.
	 10 = reaches 128 blocks in about a second and does not fall into the ground along the way.
	 The sight adjusts itself. If you reduce too much, the arrow will not reach and fall into the ground.
	 */
	public static final float ARROW_SPEED = 10.0f;

	/**
	 Where to aim for body height. 0.0 = feet, 1.0 = crown, 0.5 = middle.
	 Counts again for each shot: the same number of bullies on 128 blocks
	 He raised an arrow overhead from about 48 to 112 blocks.
	 * Above 0.5 - closer to the head. Down to the feet.
	 */
	public static final double AIM_HEIGHT = 0.5;

	/**
	 * Arrow scatter. The skeleton at a complex complexity of about 2: on 128 blocks is a miss in several blocks.
	 * 0.15 = enters the body at a long distance. 0 = always point of sight.
	 2 = as complex complexity, at this range almost always past.
	 */
	public static final float INACCURACY = 0.15f;

	/**
	 How many hearts does it take to hit armor if there is no Force on the bow?
	 6 = six hearts. The speed of the flight does not increase:
	 A quick arrow is needed to fly, not to kill once.
	 1 = one heart. 0 = no arrow hurts. 10 = all strip of naked player.
	 The force on the bow adds damage from above, like any arrow.
	 */
	public static final double DAMAGE_HEARTS = 6.0;

	// ========================================================================

	private SniperSkeleton() {
	}

	/**
	 * Damage multiplier for the game. It counts the impact as the speed of flight × this number.
	 One heart is 2 damages. The multiplier is as much as the arrow is faster.
	 * to get straight {@link #DAMAGE HEARTS} hearts, not more because of speed.
	 */
	public static double baseDamage() {
		double damage = Math.max(0.0, DAMAGE_HEARTS) * 2.0;
		return damage / Math.max(0.01, ARROW_SPEED);
	}

	/*Air resistance at the arrow in the game itself. There is no need to change: the real arrow flies with this number. */
	private static final double ARROW_DRAG = 0.99;

	/*How much the arrow falls in one stroke of the game. The same number of the game itself, not the setting of the sight. */
	private static final double ARROW_GRAVITY = 0.05;

	/**
	 Direction of shot vertically. The game then aligns it and multiplies it by speed.
	 * Selected so that the arrow comes to {@link #AIM HEIGHT} target body.
	 */
	public static double launchDirectionY(AbstractSkeleton skeleton, LivingEntity target) {
		double dx = target.getX() - skeleton.getX();
		double dz = target.getZ() - skeleton.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		double startY = skeleton.getEyeY() - 0.1;
		double wantY = target.getY(Mth.clamp(AIM_HEIGHT, 0.0, 1.0));
		if (distance < 0.5) {
			return wantY - startY;
		}
		double low = -distance * 2.0;
		double high = distance * 2.0;
		for (int i = 0; i < 24; i++) {
			double mid = (low + high) * 0.5;
			if (arrowHeightAt(distance, mid, startY) > wantY) {
				high = mid;
			} else {
				low = mid;
			}
		}
		return (low + high) * 0.5;
	}

	/*The height of the arrow at the moment when it flies exactly above a point at this distance. */
	private static double arrowHeightAt(double distance, double directionY, double startY) {
		double speed = Math.max(0.01f, ARROW_SPEED);
		double length = Math.sqrt(distance * distance + directionY * directionY);
		double horizontal = distance / length * speed;
		double vertical = directionY / length * speed;
		double traveled = 0.0;
		double height = startY;
		for (int tick = 0; tick < 400; tick++) {
			if (horizontal <= 0.0001) {
				return height;
			}
			if (traveled + horizontal >= distance) {
				double fraction = (distance - traveled) / horizontal;
				return height + vertical * fraction;
			}
			traveled += horizontal;
			height += vertical;
			horizontal *= ARROW_DRAG;
			vertical = vertical * ARROW_DRAG - ARROW_GRAVITY;
		}
		return height;
	}

	public static boolean isSniper(AbstractSkeleton skeleton) {
		return ENABLED
				&& skeleton instanceof SniperSkeletonEntity data
				&& data.evolvingthreats$isSniper();
	}

	/**
	 * True is that sniper skeleton. We do not give him two swords, a totem and a harmful arrow.
	 The bow must remain, the second hand occupied by conventional weapons.
	 */
	public static boolean tryRoll(AbstractSkeleton skeleton) {
		if (!ENABLED || !(skeleton instanceof SniperSkeletonEntity data)) {
			return false;
		}
		if (!(skeleton.level() instanceof ServerLevel)) {
			return false;
		}
		// The team puts the mark before it appears. Then we don't give up.
		if (data.evolvingthreats$isSniper()) {
			return true;
		}
		if (SPAWN_CHANCE <= 0.0f) {
			return false;
		}
		if (skeleton.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		data.evolvingthreats$setSniper(true);
		return true;
	}

	/*Bow in the main hand, random weapon in the second. Armor and charm hang the common code of the skeleton. */
	public static void equip(AbstractSkeleton skeleton) {
		if (!isSniper(skeleton)) {
			return;
		}
		if (!skeleton.getMainHandItem().is(Items.BOW)) {
			skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		}
		RandomWeapons.equipDualWieldArcher(skeleton);
	}

	/**
	 * Range of sight and shot. Call after the fast bow: he puts his 30 blocks,
	 Here they become snipers again. We don't touch the quick onion pause.
	 */
	public static void apply(AbstractSkeleton skeleton, RangedBowAttackGoal<?> bowGoal) {
		if (!isSniper(skeleton)) {
			return;
		}
		var follow = skeleton.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(FOLLOW_RANGE);
		}
		float range = Math.max(1.0f, ATTACK_RANGE_BLOCKS);
		((RangedBowAttackGoalAccessor) bowGoal).evolvingthreats$setAttackRadiusSqr(range * range);
	}

	/**
	 Team: Sniper in front of the player, with no chance.
	 Armor, charms and weapons in the second hand are hung by the same code as in a regular spawn.
	 */
	public static void spawn(ServerLevel level, Vec3 pos, LivingEntity lookAt) {
		if (!ENABLED) {
			return;
		}
		AbstractSkeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
		if (skeleton == null) {
			return;
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		skeleton.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (skeleton instanceof SniperSkeletonEntity data) {
			data.evolvingthreats$setSniper(true);
		}
		skeleton.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(skeleton.blockPosition()),
				EntitySpawnReason.COMMAND,
				null);
		if (lookAt != null) {
			skeleton.setTarget(lookAt);
		}
		level.addFreshEntity(skeleton);
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
