package com.example.entity;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/*700 IQ skeleton: the bow and sword are reversed, the second object hangs on the back. */
public interface TacticianSkeletonEntity {

	boolean evolvingthreats$isTactician();

	void evolvingthreats$setTactician(boolean tactician);

	ItemStack evolvingthreats$getBackItem();

	void evolvingthreats$setBackItem(ItemStack stack);

	@Nullable UUID evolvingthreats$getMarkedPlayer();

	void evolvingthreats$setMarkedPlayer(@Nullable UUID playerId);

	/*How long can he still play with a sword? 0 - now with onions. */
	long evolvingthreats$getMeleeUntil();

	void evolvingthreats$setMeleeUntil(long gameTime);
}
