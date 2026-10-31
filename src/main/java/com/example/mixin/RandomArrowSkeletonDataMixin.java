package com.example.mixin;

import com.example.entity.RandomArrowSkeletonEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Пометка и очередь эффектов. Очередь нужна только серверу, на экране её нет. */
@Mixin(AbstractSkeleton.class)
public class RandomArrowSkeletonDataMixin implements RandomArrowSkeletonEntity {

	@Unique
	private boolean evolvingthreats$randomArrow;

	@Unique
	private String evolvingthreats$arrowQueue = "";

	@Unique
	private String evolvingthreats$lastArrow = "";

	@Override
	public boolean evolvingthreats$isRandomArrow() {
		return evolvingthreats$randomArrow;
	}

	@Override
	public void evolvingthreats$setRandomArrow(boolean randomArrow) {
		this.evolvingthreats$randomArrow = randomArrow;
	}

	@Override
	public String evolvingthreats$getArrowQueue() {
		return evolvingthreats$arrowQueue == null ? "" : evolvingthreats$arrowQueue;
	}

	@Override
	public void evolvingthreats$setArrowQueue(String queue) {
		this.evolvingthreats$arrowQueue = queue == null ? "" : queue;
	}

	@Override
	public String evolvingthreats$getLastArrow() {
		return evolvingthreats$lastArrow == null ? "" : evolvingthreats$lastArrow;
	}

	@Override
	public void evolvingthreats$setLastArrow(String effect) {
		this.evolvingthreats$lastArrow = effect == null ? "" : effect;
	}
}
