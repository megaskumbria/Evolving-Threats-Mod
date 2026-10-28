package com.example.mixin;

import com.example.spawn.NaturalMonsterSwap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Ночной спавн враждебных мобов и спавн монстров в аду. Игра уже выбрала моба
 * и собирается его создать. Здесь вместо него иногда ставится парящий инструмент
 * или, в обычном мире, повреждённый голем. Команда и спавнер этот метод не вызывают.
 */
@Mixin(NaturalSpawner.class)
public class NaturalSpawnerFloatingToolMixin {

	/** Ноги моба, которого игра ставит прямо сейчас. Нужны, чтобы проверить рост голема. */
	@Unique
	private static BlockPos.MutableBlockPos evolvingthreats$feet;

	@Redirect(
			method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;set(III)Lnet/minecraft/core/BlockPos$MutableBlockPos;"
			)
	)
	private static BlockPos.MutableBlockPos evolvingthreats$rememberFeet(BlockPos.MutableBlockPos pos, int x, int y, int z) {
		evolvingthreats$feet = pos.set(x, y, z);
		return evolvingthreats$feet;
	}

	@Redirect(
			method = "getMobForSpawn",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/EntityType;create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;)Lnet/minecraft/world/entity/Entity;"
			)
	)
	private static Entity evolvingthreats$maybeSwap(EntityType<?> type, Level level, EntitySpawnReason reason) {
		EntityType<?> picked = NaturalMonsterSwap.pick(level, type, evolvingthreats$feet);
		return picked.create(level, reason);
	}
}
