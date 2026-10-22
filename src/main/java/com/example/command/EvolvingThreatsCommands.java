package com.example.command;

import com.example.creeper.DynamiteCreeper;
import com.example.creeper.SapperCreeper;
import com.example.creeper.ShieldBreakCreeper;
import com.example.item.ModItems;
import com.example.overseer.ZombieOverseer;
import com.example.raid.RaidAnchor;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public final class EvolvingThreatsCommands {

	private EvolvingThreatsCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register(EvolvingThreatsCommands::registerCommands);
	}

	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher,
			net.minecraft.commands.CommandBuildContext registryAccess,
			Commands.CommandSelection environment) {

		var root = Commands.literal("evolvingthreats")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));

		dispatcher.register(root.then(Commands.literal("spawn")
				.then(Commands.literal("overseer")
						.executes(ctx -> spawnOverseer(ctx.getSource(), null))
						.then(Commands.argument("target", EntityArgument.player())
								.executes(ctx -> spawnOverseer(
										ctx.getSource(),
										EntityArgument.getPlayer(ctx, "target")
								))))
				.then(Commands.literal("dynamite")
						.executes(ctx -> spawnDynamite(ctx.getSource())))
				.then(Commands.literal("sapper")
						.executes(ctx -> spawnSapper(ctx.getSource())))
				.then(Commands.literal("shield")
						.executes(ctx -> spawnShieldBreak(ctx.getSource())))
				.then(Commands.literal("raid")
						.executes(ctx -> spawnRaid(ctx.getSource(), null))
						.then(Commands.argument("target", EntityArgument.player())
								.executes(ctx -> spawnRaid(
										ctx.getSource(),
										EntityArgument.getPlayer(ctx, "target")
								))))));

		dispatcher.register(root.then(Commands.literal("give")
				.then(Commands.literal("overseer_egg")
						.executes(ctx -> giveEgg(ctx.getSource(), 1))
						.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
								.executes(ctx -> giveEgg(
										ctx.getSource(),
										IntegerArgumentType.getInteger(ctx, "count")
								))))));

		dispatcher.register(Commands.literal("etoverseer")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnOverseer(ctx.getSource(), null)));

		dispatcher.register(Commands.literal("etoverseer_egg")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> giveEgg(ctx.getSource(), 1)));

		dispatcher.register(Commands.literal("etsapper")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnSapper(ctx.getSource())));

		dispatcher.register(Commands.literal("etdynamite")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnDynamite(ctx.getSource())));

		dispatcher.register(Commands.literal("etshield")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnShieldBreak(ctx.getSource())));

		dispatcher.register(Commands.literal("etraid")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnRaid(ctx.getSource(), null)));
	}

	private static int spawnSapper(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		ServerPlayer lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(8.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		SapperCreeper.spawn(level, pos, lookAt);
		source.sendSuccess(() -> Component.literal("Spawned sapper creeper"), true);
		return 1;
	}

	private static int spawnShieldBreak(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		ServerPlayer lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(8.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		ShieldBreakCreeper.spawn(level, pos, lookAt);
		source.sendSuccess(() -> Component.literal("Spawned shield-break creeper"), true);
		return 1;
	}

	private static int spawnDynamite(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		ServerPlayer lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(10.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		DynamiteCreeper.spawn(level, pos, lookAt);
		source.sendSuccess(() -> Component.literal("Spawned dynamite creeper"), true);
		return 1;
	}

	private static int spawnRaid(CommandSourceStack source, ServerPlayer explicitTarget) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}

		ServerPlayer hunter = explicitTarget;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(8.0));
			if (hunter == null) {
				hunter = self;
			}
		} else {
			pos = source.getPosition();
		}

		RaidAnchor.spawnRaid(level, pos, hunter);
		source.sendSuccess(() -> Component.literal("Spawned raid anchor and elite minions"), true);
		return 1;
	}

	private static int spawnOverseer(CommandSourceStack source, ServerPlayer explicitTarget) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}

		ServerPlayer hunter = explicitTarget;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(2.0));
			if (hunter == null) {
				hunter = self;
			}
		} else {
			pos = source.getPosition();
		}

		ZombieOverseer.spawnOverseer(level, pos, hunter);
		source.sendSuccess(() -> Component.literal("Spawned Rotten Overseer"), true);
		return 1;
	}

	private static int giveEgg(CommandSourceStack source, int count) {
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only players can receive the spawn item"));
			return 0;
		}
		ItemStack stack = new ItemStack(Objects.requireNonNull(ModItems.ROTTEN_OVERSEER_SPAWN_EGG), count);
		player.getInventory().add(stack);
		source.sendSuccess(() -> Component.literal("Added Rotten Overseer spawn egg"), true);
		return 1;
	}
}
