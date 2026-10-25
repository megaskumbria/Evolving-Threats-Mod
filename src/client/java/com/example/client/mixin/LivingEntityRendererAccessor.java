package com.example.client.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Список слоёв и рисовальщик предметов лежат в общем классе всех живых мобов.
 * У самого скелета этих полей нет. Если искать их там, игра не запускает отрисовку
 * и остаётся чёрный экран.
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {

	@Accessor("layers")
	List<RenderLayer<?, ?>> evolvingthreats$layers();

	@Accessor("itemModelResolver")
	ItemModelResolver evolvingthreats$itemModelResolver();
}
