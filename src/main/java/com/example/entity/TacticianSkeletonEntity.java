package com.example.entity;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Скелет «700 IQ»: лук и меч меняются местами, второй предмет висит на спине. */
public interface TacticianSkeletonEntity {

	boolean evolvingthreats$isTactician();

	void evolvingthreats$setTactician(boolean tactician);

	ItemStack evolvingthreats$getBackItem();

	void evolvingthreats$setBackItem(ItemStack stack);

	@Nullable UUID evolvingthreats$getMarkedPlayer();

	void evolvingthreats$setMarkedPlayer(@Nullable UUID playerId);

	/** До какого игрового времени он ещё может быть с мечом. 0 — сейчас с луком. */
	long evolvingthreats$getMeleeUntil();

	void evolvingthreats$setMeleeUntil(long gameTime);
}
