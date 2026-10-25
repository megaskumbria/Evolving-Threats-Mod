package com.example.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Куда шалкер может прилипнуть. Методы закрыты, снаружи их не вызвать. */
@Mixin(Shulker.class)
public interface ShulkerTeleportAccessor {

	@Invoker("findAttachableSurface")
	Direction evolvingthreats$findAttachableSurface(BlockPos target);

	@Invoker("setAttachFace")
	void evolvingthreats$setAttachFace(Direction face);
}
