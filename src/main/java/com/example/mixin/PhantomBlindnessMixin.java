package com.example.mixin;

import com.example.entity.BlindingPhantomEntity;
import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Пометка «этот фантом вешает слепоту» живёт на самом фантоме.
 * Шанс при появлении и запись в мир — {@link PhantomBlindnessMobMixin}.
 */
@Mixin(Phantom.class)
public class PhantomBlindnessMixin implements BlindingPhantomEntity {

	@Unique
	private boolean evolvingthreats$blinds;

	@Override
	public boolean evolvingthreats$blindsOnBite() {
		return evolvingthreats$blinds;
	}

	@Override
	public void evolvingthreats$setBlindsOnBite(boolean blinds) {
		this.evolvingthreats$blinds = blinds;
	}
}
