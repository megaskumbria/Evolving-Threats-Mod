package com.example.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/*Where the shalker might stick. The methods are closed, they cannot be called outside. */
@Mixin(Shulker.class)
public interface ShulkerTeleportAccessor {

	@Invoker("findAttachableSurface")
	Direction evolvingthreats$findAttachableSurface(BlockPos target);

	@Invoker("setAttachFace")
	void evolvingthreats$setAttachFace(Direction face);
}
