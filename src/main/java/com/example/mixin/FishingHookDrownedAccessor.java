package com.example.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/*The hook is able to remember who it caught, but the method is closed. */
@Mixin(FishingHook.class)
public interface FishingHookDrownedAccessor {

	@Invoker("setHookedEntity")
	void evolvingthreats$setHookedEntity(Entity hooked);
}
