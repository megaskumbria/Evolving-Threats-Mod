package com.example.client.mixin;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Faces of one cube. Clearing them hides the robe without hiding the torso on the same part. */
@Mixin(ModelPart.Cube.class)
public interface ModelPartCubeAccessor {

	@Accessor("polygons")
	ModelPart.Polygon[] evolvingthreats$getPolygons();

	@Mutable
	@Accessor("polygons")
	void evolvingthreats$setPolygons(ModelPart.Polygon[] polygons);
}
