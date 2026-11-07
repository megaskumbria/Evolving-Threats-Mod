package com.example.client.mixin;

import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The robe is the second cube on the illager body. The list itself is not public. */
@Mixin(ModelPart.class)
public interface ModelPartCubesAccessor {

	@Accessor("cubes")
	List<ModelPart.Cube> evolvingthreats$cubes();
}
