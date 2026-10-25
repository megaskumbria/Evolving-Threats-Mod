package com.example.drowned;

import com.example.mixin.FishingHookDrownedAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Утопленник с удочкой закидывает крючок в игрока и подтягивает его.
 * Когда игрок уже близко, крючок отпускает и утопленник бьёт руками, как обычно.
 */
public final class DrownedFishing {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — удочка снова просто лежит в руке, крючок не закидывается. */
	public static final boolean ENABLED = true;

	/**
	 * Ближе этого (блоки) крючок отпускает, и утопленник атакует в упор.
	 * 3 = около трёх блоков, как длина его удара. Меньше — бьёт ещё издалека.
	 */
	public static final double MELEE_BLOCKS = 3.0;

	/**
	 * Дальше этого (блоки) крючок не закидывает, просто идёт к игроку.
	 * 16 = заброс примерно на 16 блоков.
	 */
	public static final double CAST_BLOCKS = 16.0;

	/**
	 * Насколько сильно тянет зацепленного игрока за тик (20 тиков = 1 секунда).
	 * 0.18 = заметно тащит к себе. Больше — рывок резче. 0 = висит на крючке, но не едет.
	 */
	public static final double PULL_PER_TICK = 0.18;

	/**
	 * Пауза после промаха, секунды. 3 = новый заброс через три секунды.
	 * Пока игрок на крючке, пауза не идёт.
	 */
	public static final int MISS_COOLDOWN_SECONDS = 3;

	/** Сколько тиков крючок летит, если никого не задел. 30 = полторы секунды. */
	public static final int FLIGHT_TICKS = 30;

	// ========================================================================

	private DrownedFishing() {
	}

	public static boolean holdsRod(Drowned drowned) {
		return drowned.getMainHandItem().is(Items.FISHING_ROD);
	}

	/** Удочка в руке, игрок не вплотную и не слишком далеко. */
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
		double distance = drowned.distanceTo(player);
		Vec3 aim = new Vec3(player.getX(), player.getY() + player.getBbHeight() * 0.6 + distance * 0.06, player.getZ());
		Vec3 direction = aim.subtract(from);
		if (direction.lengthSqr() < 1.0E-4) {
			return null;
		}
		direction = direction.normalize();
		Vec3 spawn = from.add(direction.scale(0.6));

		FishingHook hook = new FishingHook(EntityTypes.FISHING_BOBBER, level);
		hook.setOwner(drowned);
		hook.snapTo(spawn.x, spawn.y, spawn.z, drowned.getYRot(), drowned.getXRot());
		hook.setDeltaMovement(direction.scale(1.5));
		level.addFreshEntity(hook);
		drowned.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, drowned.getX(), drowned.getY(), drowned.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.HOSTILE, 0.8f, 0.8f);
		return hook;
	}

	/**
	 * Полёт крючка, пока владелец — утопленник.
	 * Обычная удочка живёт только у игрока, поэтому этот тик вместо неё.
	 *
	 * @return false — крючок пора убрать (промах, утопленник умер, удочки больше нет)
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
			if (caught instanceof Player player && !player.isSpectator()) {
				pull(drowned, player);
			}
			return true;
		}
		hook.tickCount++;
		if (hook.tickCount > FLIGHT_TICKS) {
			return false;
		}
		hook.setDeltaMovement(hook.getDeltaMovement().add(0.0, -0.03, 0.0));
		HitResult hit = ProjectileUtil.getHitResultOnMoveVector(hook, entity -> entity.isAlive() && entity != drowned);
		if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Player player) {
			((FishingHookDrownedAccessor) hook).evolvingthreats$setHookedEntity(player);
			hook.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.HOSTILE, 0.6f, 1.0f);
			pull(drowned, player);
			return true;
		}
		if (hit.getType() == HitResult.Type.BLOCK) {
			return false;
		}
		hook.move(MoverType.SELF, hook.getDeltaMovement());
		hook.setDeltaMovement(hook.getDeltaMovement().scale(0.92));
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
