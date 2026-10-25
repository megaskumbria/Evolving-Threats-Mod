package com.example.mixin;

import com.example.shulker.ShulkerShellDurability;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Удар по шалкеру. Если панцирь закрыт, стрела урон не наносит, но удар всё равно был.
 * Порча оружия — {@link ShulkerShellDurability}.
 */
@Mixin(Shulker.class)
public abstract class ShulkerShellMixin {

	@Invoker("isClosed")
	protected abstract boolean evolvingthreats$isClosed();

	@Inject(method = "hurtServer", at = @At("HEAD"))
	private void evolvingthreats$closedShell(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		if (this.evolvingthreats$isClosed()) {
			ShulkerShellDurability.onClosedHit(source);
		}
	}
}
