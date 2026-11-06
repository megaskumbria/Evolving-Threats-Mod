package com.example.mixin;

import com.example.advancement.ModAdvancements;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs after a raised block already absorbed a hit.
 * The card counts only when that hit also shut the block off for a few seconds.
 * A wooden axe returns zero seconds, so it does not count. A miss does not reach here.
 */
@Mixin(Player.class)
public class PlayerShieldSilenceMixin {

	@Inject(method = "blockUsingItem", at = @At("TAIL"))
	private void evolvingthreats$shieldSilenced(
			ServerLevel level,
			LivingEntity attacker,
			DamageSource source,
			float damage,
			boolean fullyBlocked,
			CallbackInfo ci) {
		if (!((Object) this instanceof ServerPlayer player)) {
			return;
		}
		ItemStack blocking = player.getItemBlockingWith();
		if (blocking == null || blocking.get(DataComponents.BLOCKS_ATTACKS) == null) {
			return;
		}
		if (attacker.getSecondsToDisableBlocking() <= 0.0f) {
			return;
		}
		ModAdvancements.onShieldSilenced(player);
	}
}
