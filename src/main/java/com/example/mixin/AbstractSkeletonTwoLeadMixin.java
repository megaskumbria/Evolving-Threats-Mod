package com.example.mixin;

import com.example.entity.TwoLeadHandsSkeleton;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mark for a skeleton who keeps a sword (or axe, pickaxe…) in the right hand and a bow in the left.
 * Captain, sniper, and tactician are never marked, so their own weapon switch stays.
 */
@Mixin(AbstractSkeleton.class)
public class AbstractSkeletonTwoLeadMixin implements TwoLeadHandsSkeleton {

	@Unique
	private boolean evolvingthreats$twoLeadHands;

	@Override
	public boolean evolvingthreats$isTwoLeadHands() {
		return evolvingthreats$twoLeadHands;
	}

	@Override
	public void evolvingthreats$setTwoLeadHands(boolean twoLeadHands) {
		this.evolvingthreats$twoLeadHands = twoLeadHands;
	}
}
