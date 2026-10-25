package com.example.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Крючок умеет запоминать, кого зацепил, но метод закрыт. */
@Mixin(FishingHook.class)
public interface FishingHookDrownedAccessor {

	@Invoker("setHookedEntity")
	void evolvingthreats$setHookedEntity(Entity hooked);
}
