package com.example.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Creeper fuse length is hidden. Vanilla creeper uses 30 ticks, 1.5 seconds. */
@Mixin(Creeper.class)
public interface CreeperFuseAccessor {

	@Accessor("maxSwell")
	int evolvingthreats$getMaxSwell();

	@Accessor("maxSwell")
	void evolvingthreats$setMaxSwell(int ticks);

	/** How many ticks the fuse has been running. 0 — creeper is not hissing yet. */
	@Accessor("swell")
	int evolvingthreats$getSwell();

	@Accessor("swell")
	void evolvingthreats$setSwell(int ticks);
}
