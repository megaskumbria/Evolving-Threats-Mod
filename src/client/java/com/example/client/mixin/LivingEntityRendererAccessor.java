package com.example.client.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * The list of layers and the item drawer are in the general class of all living mobs.
 * The skeleton itself does not have these fields. If you look for them there, the game does not start rendering
 * and the screen remains black.
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {

	@Accessor("layers")
	List<RenderLayer<?, ?>> evolvingthreats$layers();

	@Accessor("itemModelResolver")
	ItemModelResolver evolvingthreats$itemModelResolver();
}
