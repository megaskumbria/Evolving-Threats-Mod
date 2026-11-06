package com.example.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

/**
 * Lets the dev command mark mod advancements done and tell the client,
 * without going through award(), which would pay every reward and announce each one.
 */
@Mixin(PlayerAdvancements.class)
public interface PlayerAdvancementsDirtyAccessor {

	@Accessor("progressChanged")
	Set<AdvancementHolder> evolvingthreats$getProgressChanged();

	@Invoker("markForVisibilityUpdate")
	void evolvingthreats$markForVisibilityUpdate(AdvancementHolder advancement);
}
