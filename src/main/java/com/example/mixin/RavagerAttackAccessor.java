package com.example.mixin;

import net.minecraft.world.entity.monster.Ravager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Тик удара головой закрыт. Без него анимация укуса на сервере не запускается. */
@Mixin(Ravager.class)
public interface RavagerAttackAccessor {

	@Accessor("attackTick")
	void evolvingthreats$setAttackTick(int tick);
}
