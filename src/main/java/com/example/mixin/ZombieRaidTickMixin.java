package com.example.mixin;

import com.example.entity.RaidParticipantEntity;
import com.example.raid.RaidBossBars;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Zombie.class)
public class ZombieRaidTickMixin {

	@Inject(method = "tick", at = @At("RETURN"))
	private void evolvingthreats$raidAnchorTick(CallbackInfo ci) {
		Zombie self = (Zombie) (Object) this;
		if (!(self instanceof RaidParticipantEntity raid) || !raid.evolvingthreats$isRaidAnchor()) {
			return;
		}
		if (self.level().isClientSide() || !(self.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		RaidBossBars.tick(self, serverLevel);
	}
}
