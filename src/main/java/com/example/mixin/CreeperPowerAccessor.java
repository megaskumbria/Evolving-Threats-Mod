package com.example.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Flag "charged" at the cryper closed. Lightning puts it itself, out of fashion - through this field. */
@Mixin(Creeper.class)
public interface CreeperPowerAccessor {

	@Accessor("DATA_IS_POWERED")
	static EntityDataAccessor<Boolean> evolvingthreats$poweredId() {
		throw new AssertionError();
	}
}
