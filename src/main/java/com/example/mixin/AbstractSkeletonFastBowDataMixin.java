package com.example.mixin;

import com.example.entity.FastBowSkeletonEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/*The mark "long-range fast bow" lives on the skeleton and the wanderer. */
@Mixin(AbstractSkeleton.class)
public class AbstractSkeletonFastBowDataMixin implements FastBowSkeletonEntity {

	@Unique
	private boolean evolvingthreats$fastBow;

	@Override
	public boolean evolvingthreats$isFastBow() {
		return evolvingthreats$fastBow;
	}

	@Override
	public void evolvingthreats$setFastBow(boolean fastBow) {
		this.evolvingthreats$fastBow = fastBow;
	}
}
