package com.example.mixin;

import com.example.entity.ZombieOverseerEntity;
import com.example.overseer.ZombieOverseer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Zombie.class)
public class ZombieOverseerTickMixin {

	@Inject(method = "tick", at = @At("RETURN"))
	private void evolvingthreats$overseerTick(CallbackInfo ci) {
		Zombie self = (Zombie) (Object) this;
		if (!(self instanceof ZombieOverseerEntity overseer) || !overseer.evolvingthreats$isOverseer()) {
			return;
		}
		if (self.level().isClientSide() || !(self.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		ZombieOverseer.refreshServantCount(self, serverLevel);
		tickSummonCooldown(overseer);
		tickHuntTimeout(self, overseer, serverLevel);
	}

	private static void tickSummonCooldown(ZombieOverseerEntity overseer) {
		int cd = overseer.evolvingthreats$getSummonCooldownTicks();
		if (cd > 0) {
			overseer.evolvingthreats$setSummonCooldownTicks(cd - 1);
		}
	}

	private static void tickHuntTimeout(Zombie zombie, ZombieOverseerEntity overseer, ServerLevel level) {
		var huntId = overseer.evolvingthreats$getHuntTargetId();
		if (huntId == null) {
			return;
		}

		ServerPlayer player = level.getServer().getPlayerList().getPlayer(huntId);
		if (player == null || player.level() != level) {
			return;
		}

		double abandonSqr = ZombieOverseer.HUNT_ABANDON_DISTANCE * ZombieOverseer.HUNT_ABANDON_DISTANCE;
		if (zombie.distanceToSqr(player) <= abandonSqr) {
			overseer.evolvingthreats$setHuntStartedGameTime(level.getGameTime());
			return;
		}

		long started = overseer.evolvingthreats$getHuntStartedGameTime();
		if (started == 0L) {
			overseer.evolvingthreats$setHuntStartedGameTime(level.getGameTime());
			return;
		}

		if (level.getGameTime() - started >= ZombieOverseer.HUNT_TIMEOUT_TICKS) {
			overseer.evolvingthreats$setHuntTargetId(null);
			zombie.setTarget(null);
			overseer.evolvingthreats$setHuntStartedGameTime(0L);
		}
	}
}
