package com.example.mixin;

import com.example.advancement.AdvancementMoments;
import com.example.advancement.ModAdvancements;
import com.example.creeper.FastCreeper;
import com.example.entity.BornChargedCreeperEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remembers a creeper that spawned already charged, and grants the blast cards
 * when the explosion actually starts.
 * Lightning charges a creeper too, but it does not set this flag, so that blast
 * does not count as "already charged".
 * A player outside the blast radius does not get either card.
 */
@Mixin(Creeper.class)
public class CreeperBornChargedMixin implements BornChargedCreeperEntity {

	@Shadow
	private int explosionRadius;

	@Unique
	private boolean evolvingthreats$bornCharged;

	@Override
	public boolean evolvingthreats$wasBornCharged() {
		return evolvingthreats$bornCharged;
	}

	@Override
	public void evolvingthreats$setBornCharged(boolean bornCharged) {
		this.evolvingthreats$bornCharged = bornCharged;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveBornCharged(ValueOutput output, CallbackInfo ci) {
		if (evolvingthreats$bornCharged) {
			output.putBoolean("EvolvingThreatsBornCharged", true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadBornCharged(ValueInput input, CallbackInfo ci) {
		evolvingthreats$bornCharged = input.getBooleanOr("EvolvingThreatsBornCharged", false);
	}

	/** The fuse just finished. Players farther than the blast are skipped. */
	@Inject(method = "explodeCreeper", at = @At("HEAD"))
	private void evolvingthreats$blastCards(CallbackInfo ci) {
		Creeper creeper = (Creeper) (Object) this;
		if (!(creeper.level() instanceof ServerLevel level)) {
			return;
		}
		boolean fast = FastCreeper.isFast(creeper);
		boolean born = evolvingthreats$bornCharged && creeper.isPowered();
		if (!fast && !born) {
			return;
		}
		double radius = explosionRadius * (creeper.isPowered() ? 2.0 : 1.0);
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			if (!AdvancementMoments.blastReaches(player.distanceToSqr(creeper), radius)) {
				continue;
			}
			if (fast) {
				ModAdvancements.onShortStep(player);
			}
			if (born) {
				ModAdvancements.onStrongerBlast(player);
			}
		}
	}
}
