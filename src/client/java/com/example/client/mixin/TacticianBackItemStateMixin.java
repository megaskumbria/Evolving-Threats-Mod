package com.example.client.mixin;

import com.example.client.skeleton.TacticianBackItemState;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** An item on the back is added to the skeleton drawing state. */
@Mixin(SkeletonRenderState.class)
public class TacticianBackItemStateMixin implements TacticianBackItemState {

	@Unique
	private final ItemStackRenderState evolvingthreats$backItem = new ItemStackRenderState();

	@Override
	public ItemStackRenderState evolvingthreats$getBackItem() {
		return evolvingthreats$backItem;
	}
}
