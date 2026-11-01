package com.example.entity.ai;

import com.example.RandomDispenserSkull;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.EnumSet;

/**
 AI goal: once every N seconds, the skeleton with a spreader on the head freezes / moves slightly and releases a volley of arrows.
 */
public class DispenserBarrageGoal extends Goal {

	private final AbstractSkeleton skeleton;

	private long nextBarrageGameTime;
	private boolean initializedCooldown;

	private int barrageTicksRemaining;
	private int arrowsRemaining;
	private int shootCooldownTicks;

	public DispenserBarrageGoal(AbstractSkeleton skeleton) {
		this.skeleton = skeleton;
		this.setFlags(EnumSet.of(Flag.LOOK, Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (!RandomDispenserSkull.isDispenserSkull(skeleton)) {
			return false;
		}
		if (!(skeleton.level() instanceof ServerLevel)) {
			return false;
		}

		LivingEntity target = skeleton.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}

		if (barrageTicksRemaining > 0) {
			return true;
		}

		long now = skeleton.level().getGameTime();
		if (!initializedCooldown) {
			nextBarrageGameTime = now + RandomDispenserSkull.randomCooldownTicks(skeleton.getRandom());
			initializedCooldown = true;
			return false;
		}

		if (now < nextBarrageGameTime) {
			return false;
		}

		return skeleton.getSensing().hasLineOfSight(target);
	}

	@Override
	public boolean canContinueToUse() {
		if (!RandomDispenserSkull.isDispenserSkull(skeleton)) {
			return false;
		}
		LivingEntity target = skeleton.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}
		return barrageTicksRemaining > 0;
	}

	@Override
	public void start() {
		barrageTicksRemaining = RandomDispenserSkull.BARRAGE_PHASE_TICKS;
		arrowsRemaining = RandomDispenserSkull.BARRAGE_ARROW_COUNT;
		shootCooldownTicks = 0;

		skeleton.level().playSound(
				null,
				skeleton.blockPosition(),
				SoundEvents.DISPENSER_DISPENSE,
				SoundSource.HOSTILE,
				1.0f,
				0.85f + skeleton.getRandom().nextFloat() * 0.2f
		);
	}

	@Override
	public void stop() {
		scheduleNextBarrage();
		barrageTicksRemaining = 0;
		arrowsRemaining = 0;
	}

	@Override
	public void tick() {
		LivingEntity target = skeleton.getTarget();
		if (target == null) {
			return;
		}

		skeleton.getLookControl().setLookAt(target, 45.0f, 45.0f);
		skeleton.getNavigation().stop();

		// A slight “twitch” in place so as not to stand like a statue.
		if (skeleton.getRandom().nextInt(6) == 0) {
			float strafe = (skeleton.getRandom().nextFloat() - 0.5f) * 0.35f;
			float forward = (skeleton.getRandom().nextFloat() - 0.5f) * 0.2f;
			skeleton.setDeltaMovement(
					skeleton.getDeltaMovement().add(
							strafe * Mth.cos(skeleton.getYRot() * Mth.DEG_TO_RAD),
							0.0,
							strafe * Mth.sin(skeleton.getYRot() * Mth.DEG_TO_RAD)
					).add(
							forward * -Mth.sin(skeleton.getYRot() * Mth.DEG_TO_RAD),
							0.0,
							forward * Mth.cos(skeleton.getYRot() * Mth.DEG_TO_RAD)
					)
			);
		}

		barrageTicksRemaining--;
		shootCooldownTicks--;

		if (shootCooldownTicks <= 0 && arrowsRemaining > 0) {
			fireArrow(target);
			arrowsRemaining--;
			shootCooldownTicks = RandomDispenserSkull.BARRAGE_SHOOT_INTERVAL_TICKS;
		}

		if (barrageTicksRemaining <= 0 || arrowsRemaining <= 0) {
			barrageTicksRemaining = 0;
			arrowsRemaining = 0;
		}
	}

	private void scheduleNextBarrage() {
		nextBarrageGameTime = skeleton.level().getGameTime() + RandomDispenserSkull.randomCooldownTicks(skeleton.getRandom());
		initializedCooldown = true;
	}

	private void fireArrow(LivingEntity target) {
		if (!(skeleton.level() instanceof ServerLevel level)) {
			return;
		}

		ItemStack weapon = skeleton.getMainHandItem();
		AbstractArrow arrow = ProjectileUtil.getMobArrow(
				skeleton,
				new ItemStack(Items.ARROW),
				RandomDispenserSkull.BARRAGE_ARROW_VELOCITY,
				weapon
		);

		double spawnY = skeleton.getY() + skeleton.getEyeHeight() + 0.35;
		arrow.setPos(skeleton.getX(), spawnY, skeleton.getZ());

		double dx = target.getX() - skeleton.getX();
		double dy = target.getEyeY() - spawnY;
		double dz = target.getZ() - skeleton.getZ();

		arrow.shoot(dx, dy, dz, RandomDispenserSkull.BARRAGE_ARROW_VELOCITY, RandomDispenserSkull.BARRAGE_INACCURACY);
		level.addFreshEntity(arrow);

		level.playSound(
				null,
				skeleton.blockPosition(),
				SoundEvents.SKELETON_SHOOT,
				SoundSource.HOSTILE,
				0.55f,
				0.9f + skeleton.getRandom().nextFloat() * 0.15f
		);
	}
}
