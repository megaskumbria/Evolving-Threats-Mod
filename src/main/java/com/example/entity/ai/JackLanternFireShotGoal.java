package com.example.entity.ai;

import com.example.beacon.JackLanternBeacon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;

/**
 Skeleton with jack o'lantern: once in the cooldown one fire arrow on the target (in addition to the bow).
 */
public class JackLanternFireShotGoal extends Goal {

	private final AbstractSkeleton skeleton;

	private long nextShotGameTime;
	private boolean initializedCooldown;

	public JackLanternFireShotGoal(AbstractSkeleton skeleton) {
		this.skeleton = skeleton;
		this.setFlags(EnumSet.of(Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!JackLanternBeacon.ENABLED || !JackLanternBeacon.hasJackOnHead(skeleton)) {
			return false;
		}
		if (!(skeleton.level() instanceof ServerLevel)) {
			return false;
		}
		LivingEntity target = skeleton.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}
		double dist = skeleton.distanceTo(target);
		if (dist < JackLanternBeacon.FIRE_SHOT_MIN_DISTANCE || dist > JackLanternBeacon.FIRE_SHOT_MAX_DISTANCE) {
			return false;
		}
		if (!skeleton.getSensing().hasLineOfSight(target)) {
			return false;
		}

		long now = skeleton.level().getGameTime();
		if (!initializedCooldown) {
			nextShotGameTime = now + JackLanternBeacon.randomFireShotCooldownTicks(skeleton.getRandom());
			initializedCooldown = true;
			return false;
		}
		return now >= nextShotGameTime;
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	@Override
	public void start() {
		LivingEntity target = skeleton.getTarget();
		if (target != null) {
			fireFlamingArrow(target);
		}
		nextShotGameTime = skeleton.level().getGameTime()
				+ JackLanternBeacon.randomFireShotCooldownTicks(skeleton.getRandom());
		initializedCooldown = true;
	}

	private void fireFlamingArrow(LivingEntity target) {
		if (!(skeleton.level() instanceof ServerLevel level)) {
			return;
		}

		ItemStack weapon = skeleton.getMainHandItem();
		AbstractArrow arrow = ProjectileUtil.getMobArrow(
				skeleton,
				new ItemStack(Items.ARROW),
				JackLanternBeacon.FIRE_ARROW_VELOCITY,
				weapon);
		arrow.setRemainingFireTicks(JackLanternBeacon.FIRE_ARROW_FIRE_SECONDS * 20);

		double spawnY = skeleton.getY() + skeleton.getEyeHeight() + 0.35;
		arrow.setPos(skeleton.getX(), spawnY, skeleton.getZ());

		double dx = target.getX() - skeleton.getX();
		double dy = target.getEyeY() - spawnY;
		double dz = target.getZ() - skeleton.getZ();

		arrow.shoot(dx, dy, dz, JackLanternBeacon.FIRE_ARROW_VELOCITY, JackLanternBeacon.FIRE_ARROW_INACCURACY);
		arrow.addTag(JackLanternBeacon.FIRE_ARROW_TAG);
		level.addFreshEntity(arrow);

		level.playSound(
				null,
				skeleton.blockPosition(),
				SoundEvents.SKELETON_SHOOT,
				SoundSource.HOSTILE,
				0.65f,
				0.75f + skeleton.getRandom().nextFloat() * 0.1f);
	}
}
