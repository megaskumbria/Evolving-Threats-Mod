package com.example.mixin;

import com.example.entity.TacticianSkeletonEntity;
import com.example.skeleton.TacticianSkeleton;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

/** 700 IQ mark and object on the back. The object is synchronized to be seen. */
@Mixin(AbstractSkeleton.class)
public class TacticianSkeletonDataMixin implements TacticianSkeletonEntity {

	static {
		// The slot number on the back should be taken as soon as the skeleton is loaded.
		// before the usual skeleton and wanderer. If later, the number will match them.
		// The world will fall as soon as the skeleton tries to appear.
		if (TacticianSkeleton.BACK_ITEM == null) {
			throw new IllegalStateException("back item");
		}
	}

	@Unique
	private boolean evolvingthreats$tactician;

	@Unique
	@Nullable
	private UUID evolvingthreats$markedPlayer;

	@Unique
	private long evolvingthreats$meleeUntil;

	@Override
	public boolean evolvingthreats$isTactician() {
		return evolvingthreats$tactician;
	}

	@Override
	public void evolvingthreats$setTactician(boolean tactician) {
		this.evolvingthreats$tactician = tactician;
	}

	@Override
	public ItemStack evolvingthreats$getBackItem() {
		return ((Entity) (Object) this).getEntityData().get(TacticianSkeleton.BACK_ITEM);
	}

	@Override
	public void evolvingthreats$setBackItem(ItemStack stack) {
		ItemStack stored = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
		((Entity) (Object) this).getEntityData().set(TacticianSkeleton.BACK_ITEM, stored);
	}

	@Override
	public @Nullable UUID evolvingthreats$getMarkedPlayer() {
		return evolvingthreats$markedPlayer;
	}

	@Override
	public void evolvingthreats$setMarkedPlayer(@Nullable UUID playerId) {
		this.evolvingthreats$markedPlayer = playerId;
	}

	@Override
	public long evolvingthreats$getMeleeUntil() {
		return evolvingthreats$meleeUntil;
	}

	@Override
	public void evolvingthreats$setMeleeUntil(long gameTime) {
		this.evolvingthreats$meleeUntil = gameTime;
	}
}
