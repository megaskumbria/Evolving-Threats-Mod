package com.example.mixin;

import com.example.entity.WeakeningPhantomEntity;
import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 The mark "this phantom hangs weakness" lives on the phantom itself.
 * Chance to appear and record in the world - {@link PhantomWeaknessMobMixin}
 */
@Mixin(Phantom.class)
public class PhantomWeaknessMixin implements WeakeningPhantomEntity {

	@Unique
	private boolean evolvingthreats$weakens;

	@Override
	public boolean evolvingthreats$weakensOnBite() {
		return evolvingthreats$weakens;
	}

	@Override
	public void evolvingthreats$setWeakensOnBite(boolean weakens) {
		this.evolvingthreats$weakens = weakens;
	}
}
