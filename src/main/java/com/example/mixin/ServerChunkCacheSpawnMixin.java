package com.example.mixin;

import com.example.threat.HostileSpawnCap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While this world is deciding spawns, {@link HostileSpawnCap} needs to know which world it is.
 * The monster-limit question itself does not receive the world.
 */
@Mixin(ServerChunkCache.class)
public class ServerChunkCacheSpawnMixin {

	@Shadow
	private ServerLevel level;

	@Inject(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("HEAD"))
	private void evolvingthreats$beginSpawnCap(ProfilerFiller profiler, CallbackInfo ci) {
		HostileSpawnCap.begin(this.level);
	}

	@Inject(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("RETURN"))
	private void evolvingthreats$endSpawnCap(ProfilerFiller profiler, CallbackInfo ci) {
		HostileSpawnCap.end();
	}
}
