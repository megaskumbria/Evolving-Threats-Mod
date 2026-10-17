package com.example.item;

import com.example.overseer.ZombieOverseer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Предмет для тестов: ПКМ по блоку — спавн Rotten Overseer.
 * Не ванильное яйцо, но работает так же удобно.
 */
public class RottenOverseerSpawnEggItem extends Item {

	public RottenOverseerSpawnEggItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(@NotNull UseOnContext context) {
		Level level = context.getLevel();
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		ServerLevel serverLevel = (ServerLevel) level;
		Vec3 spawnPos = Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace()));
		Player user = context.getPlayer();
		ServerPlayer hunter = user instanceof ServerPlayer sp ? sp : null;

		ZombieOverseer.spawnOverseer(serverLevel, spawnPos, hunter);

		if (user != null && !user.getAbilities().instabuild) {
			context.getItemInHand().shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.PASS;
		}

		Vec3 spawnPos = player.position().add(player.getLookAngle().scale(2.0));
		ZombieOverseer.spawnOverseer(serverLevel, spawnPos, sp);

		if (!player.getAbilities().instabuild) {
			player.getItemInHand(hand).shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
