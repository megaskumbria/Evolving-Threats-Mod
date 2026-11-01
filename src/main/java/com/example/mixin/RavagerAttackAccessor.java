package com.example.mixin;

import net.minecraft.world.entity.monster.Ravager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Head blow tick closed. Without it, the bite animation on the server does not run. */
@Mixin(Ravager.class)
public interface RavagerAttackAccessor {

	@Accessor("attackTick")
	void evolvingthreats$setAttackTick(int tick);
}
