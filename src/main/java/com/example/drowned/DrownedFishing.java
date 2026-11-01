package com.example.drowned;

import com.example.mixin.FishingHookDrownedAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 The drowned man with the fishing rod just holds it in his hand. The hook does not throw and does not drag anyone.
 The rod is given by the game itself, the mod does not take it. He beats his hands like a regular drowned man.
 * true y {@link #ENABLED} again includes throwing and pulling.
 */
public final class DrownedFishing {

	// ===================== SETTINGS (change these numbers) =====================

	/**
	 False - only the object in the hand, the hook does not throw.
	 True - again throws the hook at the player and pulls him up.
	 */
	public static final boolean ENABLED = false;

	/**
	 Closer to this (blocks) the hook releases, and the drowned attack at point-blank range.
	 3 = about three blocks as the length of its impact. Less hits from afar.
	 */
	public static final double MELEE_BLOCKS = 3.0;

	/**
	 Next to this (blocks) the hook does not throw, just goes to the player.
	 * 16 = throw of approximately 16 blocks.
	 */
	public static final double CAST_BLOCKS = 16.0;

	/**
	 How much does the player pull by the tick (20 ticks = 1 second).
	 * 0.18 = noticeably pulling towards himself. More is a sharper push. 0 = hanging on the hook, but not going.
	 */
	public static final double PULL_PER_TICK = 0.18;

	/**
	 Pause after miss, second. 1 = new throw in a second.
	 While the player is on the hook, there is no pause.
	 */
	public static final int MISS_COOLDOWN_SECONDS = 1;

	/*How many ticks does the hook fly if you don't hit anyone? 30 = a second and a half, then it's a miss. */
	public static final int FLIGHT_TICKS = 30;

	/**
	 Hook speed, blocks per tick. 1.6 = Flying at a normal distance.
	 Less - the arc lower and more often beats the ground.
	 */
	public static final double CAST_SPEED = 1.6;

	// ========================================================================

	private DrownedFishing() {
	}

	public static boolean holdsRod(Drowned drowned) {
		return drowned.getMainHandItem().is(Items.FISHING_ROD);
	}

	/*rod in hand, the player is not close and not too far away. */
	public static boolean wantsToCast(Drowned drowned) {
		if (!ENABLED || CAST_BLOCKS <= MELEE_BLOCKS || !holdsRod(drowned)) {
			return false;
		}
		Player player = targetPlayer(drowned);
		if (player == null) {
			return false;
		}
		double distance = drowned.distanceTo(player);
		return distance > MELEE_BLOCKS && distance <= CAST_BLOCKS;
	}

	public static FishingHook cast(Drowned drowned, Player player) {
		if (!(drowned.level() instanceof ServerLevel level)) {
			return null;
		}
		Vec3 from = new Vec3(drowned.getX(), drowned.getEyeY() - 0.1, drowned.getZ());
		Vec3 aim = new Vec3(player.getX(), player.getY(0.6), player.getZ());
		Vec3 direction = aim.subtract(from);
		if (direction.lengthSqr() < 1.0E-4) {
			return null;
		}
		direction = direction.normalize();
		// Next to the body, otherwise the hook immediately rests on the drowned person.
		Vec3 spawn = from.add(direction.scale(1.4));

		FishingHook hook = new FishingHook(EntityTypes.FISHING_BOBBER, level);
		hook.setOwner(drowned);
		hook.snapTo(spawn.x, spawn.y, spawn.z, drowned.getYRot(), drowned.getXRot());
		hook.setDeltaMovement(direction.scale(CAST_SPEED));
		hook.needsSync = true;
		level.addFreshEntity(hook);
		drowned.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
		level.playSound(null, drowned.getX(), drowned.getY(), drowned.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.HOSTILE, 0.8f, 0.8f);
		return hook;
	}

	/**
	 Fly the hook while the owner is drowned.
	 The usual fishing rod lives only in the player, so this tick instead of it.
	 *
	 * @return false - it's time to remove the hook (miss, drowned man died, fishing rod no more)
	 */
	public static boolean tickBobber(FishingHook hook, Drowned drowned) {
		if (!ENABLED || !drowned.isAlive() || !holdsRod(drowned)) {
			return false;
		}
		if (hook.distanceToSqr(drowned) > CAST_BLOCKS * CAST_BLOCKS * 4.0) {
			return false;
		}
		Entity caught = hook.getHookedIn();
		if (caught != null) {
			if (!caught.isAlive()) {
				return false;
			}
			hook.setPos(caught.getX(), caught.getY(0.8), caught.getZ());
			hook.setDeltaMovement(Vec3.ZERO);
			hook.needsSync = true;
			if (caught instanceof Player player && !player.isSpectator()) {
				pull(drowned, player);
			}
			return true;
		}
		if (hook.tickCount > FLIGHT_TICKS) {
			return false;
		}
		Vec3 motion = hook.getDeltaMovement();
		Player player = targetPlayer(drowned);
		if (player != null) {
			Vec3 toPlayer = new Vec3(player.getX(), player.getY(0.6), player.getZ()).subtract(hook.position());
			if (toPlayer.lengthSqr() > 1.0E-4) {
				double speed = Math.max(CAST_SPEED * 0.75, motion.length());
				Vec3 desired = toPlayer.normalize().scale(speed);
				motion = motion.scale(0.55).add(desired.scale(0.45));
			}
		}
		motion = motion.add(0.0, -0.02, 0.0);
		hook.setDeltaMovement(motion);
		HitResult hit = ProjectileUtil.getHitResultOnMoveVector(hook, entity -> entity.isAlive() && entity != drowned && entity instanceof Player);
		if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Player hooked) {
			((FishingHookDrownedAccessor) hook).evolvingthreats$setHookedEntity(hooked);
			hook.setPos(hooked.getX(), hooked.getY(0.8), hooked.getZ());
			hook.setDeltaMovement(Vec3.ZERO);
			pull(drowned, hooked);
			hook.needsSync = true;
			return true;
		}
		// In the body itself, the hook can touch the floor. The wall is a mistake, and you can throw again.
		if (hit.getType() == HitResult.Type.BLOCK && hook.tickCount > 2 && hit.getLocation().distanceToSqr(drowned.position()) > 2.25) {
			return false;
		}
		Vec3 next = hook.position().add(motion);
		hook.setPos(next.x, next.y, next.z);
		hook.setDeltaMovement(motion.scale(0.98));
		hook.needsSync = true;
		return true;
	}

	private static void pull(Drowned drowned, Player player) {
		if (PULL_PER_TICK <= 0.0 || drowned.distanceTo(player) <= MELEE_BLOCKS) {
			return;
		}
		Vec3 toDrowned = drowned.position().subtract(player.position());
		double length = toDrowned.length();
		if (length < 1.0E-4) {
			return;
		}
		Vec3 step = toDrowned.scale(PULL_PER_TICK / length);
		Vec3 movement = player.getDeltaMovement().scale(0.5);
		player.setDeltaMovement(movement.x + step.x, Math.max(movement.y, step.y * 0.4), movement.z + step.z);
		player.needsSync = true;
		player.resetFallDistance();
	}

	public static Player targetPlayer(Drowned drowned) {
		if (drowned.getTarget() instanceof Player player && player.isAlive() && !player.isSpectator()) {
			return player;
		}
		return null;
	}
}
