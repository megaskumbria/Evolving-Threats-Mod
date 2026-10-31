package com.example.mixin;

import com.example.entity.SniperSkeletonEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Пометка снайпера живёт на скелете и страннике. */
@Mixin(AbstractSkeleton.class)
public class SniperSkeletonDataMixin implements SniperSkeletonEntity {

	@Unique
	private boolean evolvingthreats$sniper;

	@Override
	public boolean evolvingthreats$isSniper() {
		return evolvingthreats$sniper;
	}

	@Override
	public void evolvingthreats$setSniper(boolean sniper) {
		this.evolvingthreats$sniper = sniper;
	}
}
