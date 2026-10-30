package com.example.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Длина шипения крипера закрыта. У обычного крипера 30 тиков, это 1,5 секунды. */
@Mixin(Creeper.class)
public interface CreeperFuseAccessor {

	@Accessor("maxSwell")
	int evolvingthreats$getMaxSwell();

	@Accessor("maxSwell")
	void evolvingthreats$setMaxSwell(int ticks);

	/** Сколько тиков шипение уже идёт. 0 — крипер ещё не шипит. */
	@Accessor("swell")
	int evolvingthreats$getSwell();

	@Accessor("swell")
	void evolvingthreats$setSwell(int ticks);
}
