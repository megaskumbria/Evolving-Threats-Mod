package com.example.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Флаг «заряжен» у крипера закрыт. Молния ставит его сама, из мода — через это поле. */
@Mixin(Creeper.class)
public interface CreeperPowerAccessor {

	@Accessor("DATA_IS_POWERED")
	static EntityDataAccessor<Boolean> evolvingthreats$poweredId() {
		throw new AssertionError();
	}
}
