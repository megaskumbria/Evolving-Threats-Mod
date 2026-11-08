package com.example.command;

import com.example.advancement.ModAdvancements;
import com.example.bastion.Bastion;
import com.example.mirror.Mirror;
import com.example.spiky.Spiky;
import com.example.blaze.BlazeAlchemist;
import com.example.witch.WitchNullifier;
import com.example.creeper.DynamiteCreeper;
import com.example.creeper.FastCreeper;
import com.example.creeper.FeintCreeper;
import com.example.creeper.MagnetCreeper;
import com.example.illager.IllagerPatrol;
import com.example.creeper.SapperCreeper;
import com.example.creeper.ShieldBreakCreeper;
import com.example.item.ModItems;
import com.example.kit.SurvivalKit;
import com.example.overseer.ZombieOverseer;
import com.example.raid.RaidAnchor;
import com.example.siege.SiegeComposition;
import com.example.siege.SiegeNight;
import com.example.skeleton.BasiliskSkeleton;
import com.example.skeleton.SkeletonCaptain;
import com.example.skeleton.SkeletonPunchBow;
import com.example.skeleton.SkeletonHorseman;
import com.example.zombie.TntZombie;
import com.example.zombie.ZombieExcalibur;
import com.example.zombie.ZombieMjolnir;
import com.example.zombie.ZombieSorcerer;
import com.example.ritual.RitualBand;
import com.example.skeleton.RandomArrowSkeleton;
import com.example.skeleton.SniperSkeleton;
import com.example.skeleton.TacticianSkeleton;
import com.example.golem.DamagedGolem;
import com.example.spawnerhead.SpawnerHeadMob;
import com.example.sleep.SleepShelter;
import com.example.threat.ThreatEvents;
import com.example.threat.ThreatScale;
import com.example.tool.FloatingTool;
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
import net.minecraft.world.entity.LivingEntity;
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
				.then(Commands.literal("fast_creeper")
						.executes(ctx -> spawnFastCreeper(ctx.getSource())))
				.then(Commands.literal("feint_creeper")
						.executes(ctx -> spawnFeintCreeper(ctx.getSource())))
				.then(Commands.literal("magnet")
						.executes(ctx -> spawnMagnetCreeper(ctx.getSource())))
				.then(Commands.literal("raid")
						.executes(ctx -> spawnRaid(ctx.getSource(), null))
						.then(Commands.argument("target", EntityArgument.player())
								.executes(ctx -> spawnRaid(
										ctx.getSource(),
										EntityArgument.getPlayer(ctx, "target")
								))))
				.then(Commands.literal("tactician")
						.executes(ctx -> spawnTactician(ctx.getSource())))
				.then(Commands.literal("random_arrow")
						.executes(ctx -> spawnRandomArrow(ctx.getSource())))
				.then(Commands.literal("sniper")
						.executes(ctx -> spawnSniper(ctx.getSource())))
				.then(Commands.literal("patrol")
						.executes(ctx -> spawnPatrol(ctx.getSource())))
				.then(Commands.literal("alchemist")
						.executes(ctx -> spawnAlchemist(ctx.getSource())))
				.then(Commands.literal("witch")
						.executes(ctx -> spawnNullifierWitch(ctx.getSource())))
				.then(Commands.literal("wither_spawner")
						.executes(ctx -> spawnWitherSpawner(ctx.getSource())))
				.then(Commands.literal("captain")
						.executes(ctx -> spawnCaptain(ctx.getSource())))
				.then(Commands.literal("basilisk")
						.executes(ctx -> spawnBasilisk(ctx.getSource())))
				.then(Commands.literal("bastion")
						.executes(ctx -> spawnBastion(ctx.getSource(), false))
						.then(Commands.literal("skeleton")
								.executes(ctx -> spawnBastion(ctx.getSource(), true))))
				.then(Commands.literal("spiky")
						.executes(ctx -> spawnSpiky(ctx.getSource(), false))
						.then(Commands.literal("skeleton")
								.executes(ctx -> spawnSpiky(ctx.getSource(), true))))
				.then(mirrorSpawn())
				.then(Commands.literal("horseman")
						.executes(ctx -> spawnHorseman(ctx.getSource())))
				.then(Commands.literal("tnt_zombie")
						.executes(ctx -> spawnTntZombie(ctx.getSource())))
				.then(Commands.literal("sorcerer")
						.executes(ctx -> spawnSorcerer(ctx.getSource())))
				.then(Commands.literal("excalibur")
						.executes(ctx -> spawnExcalibur(ctx.getSource())))
				.then(Commands.literal("mjolnir")
						.executes(ctx -> spawnMjolnir(ctx.getSource())))
				.then(Commands.literal("ritual")
						.executes(ctx -> spawnRitual(ctx.getSource(), 0))
						.then(Commands.literal("player")
								.executes(ctx -> spawnRitual(ctx.getSource(), RitualBand.HEAD_PLAYER)))
						.then(Commands.literal("creeper")
								.executes(ctx -> spawnRitual(ctx.getSource(), RitualBand.HEAD_CREEPER)))
						.then(Commands.literal("piglin")
								.executes(ctx -> spawnRitual(ctx.getSource(), RitualBand.HEAD_PIGLIN)))
						.then(Commands.literal("wither")
								.executes(ctx -> spawnRitual(ctx.getSource(), RitualBand.HEAD_WITHER))))
				.then(Commands.literal("tool")
						.executes(ctx -> spawnFloatingTool(ctx.getSource())))
				.then(Commands.literal("golem")
						.executes(ctx -> spawnDamagedGolem(ctx.getSource())))
				.then(Commands.literal("punch")
						.executes(ctx -> spawnPunchBow(ctx.getSource(), SkeletonPunchBow.PUNCH_LEVEL_VI))
						.then(Commands.literal("v")
								.executes(ctx -> spawnPunchBow(ctx.getSource(), SkeletonPunchBow.PUNCH_LEVEL_V))))));

		dispatcher.register(root.then(Commands.literal("give")
				.then(Commands.literal("overseer_egg")
						.executes(ctx -> giveEgg(ctx.getSource(), 1))
						.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
								.executes(ctx -> giveEgg(
										ctx.getSource(),
										IntegerArgumentType.getInteger(ctx, "count")
								))))
				// A set of netherite to test the fights. Charm numbers are in kit/SurvivalKit.java.
				.then(Commands.literal("kit")
						.executes(ctx -> SurvivalKit.give(ctx.getSource())))));

		// Check the siege without waiting 10-15 days.
		//   Status: When is the next stage and at what stage?
		//   Warning now, the night of siege will begin in 2 minutes.
		//   Start - night of siege at once (in the day mobs burn in the sun, this is normal).
		//   Warn/start undead is the same, but it is necessary to undead and creatures (zombies, crypers, spiders...).
		//   Warn/start illagers – the same, but necessarily illagers (robbers, ruiners...), the day is not checked.
		//   Warning/start is a 100% siege from one species: zombie, skeleton, wither skeleton,
		//                         creeper, spider, witch, phantom,
		//                         pillager, vindicator, evoker, ravager.
		//                         wither skeleton does not verify if a player has been to Hell.
		//   Stop the siege.
		//   in <days> - to appoint a siege after so many game days (0 = night today).
		var siegeWarn = Commands.literal("warn")
				.executes(ctx -> siegeWarn(ctx.getSource(), null))
				.then(Commands.literal("undead")
						.executes(ctx -> siegeWarn(ctx.getSource(), SiegeComposition.Faction.UNDEAD)))
				.then(Commands.literal("illagers")
						.executes(ctx -> siegeWarn(ctx.getSource(), SiegeComposition.Faction.ILLAGER)));
		var siegeStart = Commands.literal("start")
				.executes(ctx -> siegeStart(ctx.getSource(), null))
				.then(Commands.literal("undead")
						.executes(ctx -> siegeStart(ctx.getSource(), SiegeComposition.Faction.UNDEAD)))
				.then(Commands.literal("illagers")
						.executes(ctx -> siegeStart(ctx.getSource(), SiegeComposition.Faction.ILLAGER)));
		for (SiegeComposition.Kind kind : SiegeComposition.Kind.values()) {
			String name = kind.name().toLowerCase(java.util.Locale.ROOT);
			siegeWarn.then(Commands.literal(name)
					.executes(ctx -> siegeWarnKind(ctx.getSource(), kind)));
			siegeStart.then(Commands.literal(name)
					.executes(ctx -> siegeStartKind(ctx.getSource(), kind)));
		}
		dispatcher.register(root.then(Commands.literal("siege")
				.then(Commands.literal("status")
						.executes(ctx -> siegeStatus(ctx.getSource())))
				.then(siegeWarn)
				.then(siegeStart)
				.then(Commands.literal("stop")
						.executes(ctx -> siegeStop(ctx.getSource())))
				.then(Commands.literal("in")
						.then(Commands.argument("days", IntegerArgumentType.integer(0, 100))
								.executes(ctx -> siegeIn(
										ctx.getSource(),
										IntegerArgumentType.getInteger(ctx, "days")
								))))));

		// Threat Scale (see Threat Scale.java).
		//   The day shows the day, the phase of the moon and the current threat.
		//   day set <N> - make it as if the installation of the fashion has passed N days (the time of the world and the moon do not change).
		//                 day set 0 – start the scale again. To test any stage.
		// Shows every mod card on the advancements tab. No items, no experience, no chat spam.
		// Clear them again with: /advancement revoke @s from evolving-threats:root
		dispatcher.register(root.then(Commands.literal("advancements")
				.executes(ctx -> revealAdvancements(ctx.getSource()))));

		// Points and wake chance for the block under your feet, as if that block were the bed.
		dispatcher.register(root.then(Commands.literal("sleep")
				.executes(ctx -> sleepChance(ctx.getSource()))));

		dispatcher.register(root.then(Commands.literal("day")
				.executes(ctx -> dayStatus(ctx.getSource()))
				.then(Commands.literal("set")
						.then(Commands.argument("day", IntegerArgumentType.integer(0, 100000))
								.executes(ctx -> daySet(
										ctx.getSource(),
										IntegerArgumentType.getInteger(ctx, "day")
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

		dispatcher.register(Commands.literal("etfast")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnFastCreeper(ctx.getSource())));

		dispatcher.register(Commands.literal("etfeint")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnFeintCreeper(ctx.getSource())));

		dispatcher.register(Commands.literal("etmagnet")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnMagnetCreeper(ctx.getSource())));

		dispatcher.register(Commands.literal("etraid")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnRaid(ctx.getSource(), null)));

		dispatcher.register(Commands.literal("etpatrol")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnPatrol(ctx.getSource())));

		dispatcher.register(Commands.literal("ettactician")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnTactician(ctx.getSource())));

		dispatcher.register(Commands.literal("etarrows")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnRandomArrow(ctx.getSource())));

		dispatcher.register(Commands.literal("etsniper")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnSniper(ctx.getSource())));

		dispatcher.register(Commands.literal("etalchemist")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnAlchemist(ctx.getSource())));

		dispatcher.register(Commands.literal("etwitch")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnNullifierWitch(ctx.getSource())));

		dispatcher.register(Commands.literal("etwitherspawner")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnWitherSpawner(ctx.getSource())));

		dispatcher.register(Commands.literal("etcaptain")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnCaptain(ctx.getSource())));

		dispatcher.register(Commands.literal("etbasilisk")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnBasilisk(ctx.getSource())));

		dispatcher.register(Commands.literal("etbastion")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnBastion(ctx.getSource(), false))
				.then(Commands.literal("skeleton")
						.executes(ctx -> spawnBastion(ctx.getSource(), true))));

		dispatcher.register(Commands.literal("etspiky")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnSpiky(ctx.getSource(), false))
				.then(Commands.literal("skeleton")
						.executes(ctx -> spawnSpiky(ctx.getSource(), true))));

		var etmirror = Commands.literal("etmirror")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnMirror(ctx.getSource(), "zombie"));
		for (String kind : MIRROR_KINDS) {
			String word = kind;
			etmirror.then(Commands.literal(word).executes(ctx -> spawnMirror(ctx.getSource(), word)));
		}
		dispatcher.register(etmirror);

		dispatcher.register(Commands.literal("ethorseman")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnHorseman(ctx.getSource())));

		dispatcher.register(Commands.literal("ettnt")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnTntZombie(ctx.getSource())));

		dispatcher.register(Commands.literal("etsorcerer")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnSorcerer(ctx.getSource())));

		dispatcher.register(Commands.literal("etexcalibur")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnExcalibur(ctx.getSource())));

		dispatcher.register(Commands.literal("etmjolnir")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnMjolnir(ctx.getSource())));

		dispatcher.register(Commands.literal("etritual")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnRitual(ctx.getSource(), 0))
				.then(Commands.literal("player")
						.executes(ctx -> spawnRitual(ctx.getSource(), RitualBand.HEAD_PLAYER)))
				.then(Commands.literal("creeper")
						.executes(ctx -> spawnRitual(ctx.getSource(), RitualBand.HEAD_CREEPER)))
				.then(Commands.literal("piglin")
						.executes(ctx -> spawnRitual(ctx.getSource(), RitualBand.HEAD_PIGLIN)))
				.then(Commands.literal("wither")
						.executes(ctx -> spawnRitual(ctx.getSource(), RitualBand.HEAD_WITHER))));

		dispatcher.register(Commands.literal("ettool")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnFloatingTool(ctx.getSource())));

		dispatcher.register(Commands.literal("etgolem")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnDamagedGolem(ctx.getSource())));

		dispatcher.register(Commands.literal("etpunch")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> spawnPunchBow(ctx.getSource(), SkeletonPunchBow.PUNCH_LEVEL_VI))
				.then(Commands.literal("v")
						.executes(ctx -> spawnPunchBow(ctx.getSource(), SkeletonPunchBow.PUNCH_LEVEL_V))));

		dispatcher.register(Commands.literal("etadvancements")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> revealAdvancements(ctx.getSource())));

		dispatcher.register(Commands.literal("etkit")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> SurvivalKit.give(ctx.getSource())));
	}

	private static int revealAdvancements(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Run this as a player."));
			return 0;
		}
		int count = ModAdvancements.revealAll(player);
		source.sendSuccess(() -> Component.literal(
				"Opened " + count + " mod advancements. No items and no experience. "
						+ "To hide them again: /advancement revoke @s from evolving-threats:root"), true);
		return Math.max(1, count);
	}

	private static int sleepChance(CommandSourceStack source) {
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Run this as a player."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal(SleepShelter.describe(player)), false);
		return 1;
	}

	private static int dayStatus(CommandSourceStack source) {
		ServerLevel overworld = source.getServer().overworld();
		source.sendSuccess(() -> Component.literal(ThreatScale.describe(overworld)), false);
		return 1;
	}

	private static int daySet(CommandSourceStack source, int day) {
		var server = source.getServer();
		ServerLevel overworld = server.overworld();
		// We do not touch the time of the world and the moon: we shift only the “start point” of the scale so that exactly the day days pass.
		long start = overworld.getOverworldClockTime() - day * ThreatScale.TICKS_PER_DAY;
		ThreatEvents.setStartClock(server, start);
		// The schedule of the siege is tied to the days of the scale, so we recalculate it (if the siege is not going now).
		SiegeNight.reschedule(overworld);
		source.sendSuccess(() -> Component.literal("Day set. " + ThreatScale.describe(overworld)), true);
		return 1;
	}

	private static int siegeStatus(CommandSourceStack source) {
		ServerLevel overworld = source.getServer().overworld();
		source.sendSuccess(() -> Component.literal(SiegeNight.status(overworld)), false);
		return 1;
	}

	private static int siegeWarn(CommandSourceStack source, SiegeComposition.Faction faction) {
		SiegeNight.forceWarning(source.getServer().overworld(), faction);
		return 1;
	}

	private static int siegeStart(CommandSourceStack source, SiegeComposition.Faction faction) {
		ServerLevel overworld = source.getServer().overworld();
		SiegeNight.forceStart(overworld, faction);
		source.sendSuccess(() -> Component.literal("Siege night started; " + SiegeNight.status(overworld)), true);
		return 1;
	}

	private static int siegeWarnKind(CommandSourceStack source, SiegeComposition.Kind kind) {
		SiegeNight.forceWarning(source.getServer().overworld(), kind);
		return 1;
	}

	private static int siegeStartKind(CommandSourceStack source, SiegeComposition.Kind kind) {
		ServerLevel overworld = source.getServer().overworld();
		SiegeNight.forceStart(overworld, kind);
		source.sendSuccess(() -> Component.literal("Siege night started; " + SiegeNight.status(overworld)), true);
		return 1;
	}

	private static int siegeStop(CommandSourceStack source) {
		SiegeNight.forceStop(source.getServer().overworld());
		source.sendSuccess(() -> Component.literal("Siege stopped, next one rescheduled"), true);
		return 1;
	}

	private static int siegeIn(CommandSourceStack source, int days) {
		if (!SiegeNight.scheduleIn(source.getServer().overworld(), days)) {
			source.sendFailure(Component.literal("A siege is running now. Use /evolvingthreats siege stop first"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Next siege in " + days + " day(s)"), true);
		return 1;
	}

	private static int spawnPunchBow(CommandSourceStack source, int punchLevel) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		LivingEntity lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(6.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		if (!SkeletonPunchBow.spawn(level, pos, lookAt, punchLevel)) {
			source.sendFailure(Component.literal("Could not spawn skeleton"));
			return 0;
		}
		String roman = punchLevel >= SkeletonPunchBow.PUNCH_LEVEL_VI ? "VI" : "V";
		source.sendSuccess(() -> Component.literal("Spawned skeleton with Punch " + roman), true);
		return 1;
	}

	private static int spawnFloatingTool(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		LivingEntity lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(6.0)).add(0.0, 1.2, 0.0);
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		if (FloatingTool.spawn(level, pos, lookAt) == null) {
			source.sendFailure(Component.literal("Could not spawn floating tool"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned floating tool"), true);
		return 1;
	}

	private static int spawnDamagedGolem(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		LivingEntity lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			Vec3 look = self.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0.0, look.z);
			if (flat.lengthSqr() < 1.0E-4) {
				flat = new Vec3(0.0, 0.0, 1.0);
			}
			pos = self.position().add(flat.normalize().scale(6.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		if (DamagedGolem.spawn(level, pos, lookAt) == null) {
			source.sendFailure(Component.literal("Could not spawn damaged golem"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned damaged golem"), true);
		return 1;
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

	private static int spawnFastCreeper(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		ServerPlayer lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(12.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		if (FastCreeper.spawn(level, pos, lookAt) == null) {
			source.sendFailure(Component.literal("Fast creeper is disabled"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned fast creeper"), true);
		return 1;
	}

	private static int spawnFeintCreeper(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		ServerPlayer lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(12.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		if (FeintCreeper.spawn(level, pos, lookAt) == null) {
			source.sendFailure(Component.literal("Feint creeper is disabled"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned feint creeper"), true);
		return 1;
	}

	private static int spawnMagnetCreeper(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		ServerPlayer lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(16.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		if (MagnetCreeper.spawn(level, pos, lookAt) == null) {
			source.sendFailure(Component.literal("Magnet creeper is disabled"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned magnet creeper"), true);
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

	private static int spawnPatrol(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only a player can call the patrol"));
			return 0;
		}
		if (!IllagerPatrol.summonFor(level, player)) {
			source.sendFailure(Component.literal("No open ground for the patrol"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned illager patrol"), true);
		return 1;
	}

	private static int spawnTactician(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		LivingEntity lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(4.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		TacticianSkeleton.spawn(level, pos, lookAt);
		source.sendSuccess(() -> Component.literal("Spawned 700 IQ skeleton"), true);
		return 1;
	}

	private static int spawnRandomArrow(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		LivingEntity lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(4.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		RandomArrowSkeleton.spawn(level, pos, lookAt);
		source.sendSuccess(() -> Component.literal("Spawned random-arrow skeleton"), true);
		return 1;
	}

	private static int spawnSniper(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		LivingEntity lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(4.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		SniperSkeleton.spawn(level, pos, lookAt);
		source.sendSuccess(() -> Component.literal("Spawned sniper skeleton"), true);
		return 1;
	}

	private static final String[] MIRROR_KINDS = {
			"skeleton", "wither", "blaze", "spider", "piglin", "brute", "enderman", "silverfish", "creeper", "witch"
	};

	/*Evolvingthreats spawn mirror. Without a word, zombies. */
	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> mirrorSpawn() {
		var mirror = Commands.literal("mirror")
				.executes(ctx -> spawnMirror(ctx.getSource(), "zombie"));
		for (String kind : MIRROR_KINDS) {
			String word = kind;
			mirror = mirror.then(Commands.literal(word).executes(ctx -> spawnMirror(ctx.getSource(), word)));
		}
		return mirror;
	}

	private static int spawnMirror(CommandSourceStack source, String kind) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		LivingEntity lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(6.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		if (!Mirror.spawn(level, pos, lookAt, kind)) {
			source.sendFailure(Component.literal("Mirror is disabled"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned Mirror " + kind), true);
		return 1;
	}

	private static int spawnBastion(CommandSourceStack source, boolean skeleton) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		LivingEntity lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(6.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		if (!Bastion.spawn(level, pos, lookAt, skeleton)) {
			source.sendFailure(Component.literal("Bastion is disabled"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal(skeleton ? "Spawned Bastion skeleton" : "Spawned Bastion zombie"), true);
		return 1;
	}

	private static int spawnSpiky(CommandSourceStack source, boolean skeleton) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		LivingEntity lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(6.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		if (!Spiky.spawn(level, pos, lookAt, skeleton)) {
			source.sendFailure(Component.literal("Spiky is disabled"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal(skeleton ? "Spawned Spiky skeleton" : "Spawned Spiky zombie"), true);
		return 1;
	}

	private static int spawnBasilisk(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		LivingEntity lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(6.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		if (!BasiliskSkeleton.spawn(level, pos, lookAt)) {
			source.sendFailure(Component.literal("Basilisk is disabled"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned Basilisk"), true);
		return 1;
	}

	private static int spawnCaptain(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		LivingEntity lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(6.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		if (SkeletonCaptain.spawn(level, pos, lookAt) == null) {
			source.sendFailure(Component.literal("Skeleton captain is disabled"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned Captain of the Skeletons"), true);
		return 1;
	}

	private static int spawnHorseman(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		LivingEntity lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(8.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		if (SkeletonHorseman.spawn(level, pos, lookAt) == null) {
			source.sendFailure(Component.literal("Skeleton horseman is disabled"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned skeleton horseman"), true);
		return 1;
	}

	private static int spawnTntZombie(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		LivingEntity lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(6.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		if (TntZombie.spawn(level, pos, lookAt) == null) {
			source.sendFailure(Component.literal("TNT zombie is disabled"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned TNT zombie"), true);
		return 1;
	}

	private static int spawnSorcerer(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		LivingEntity lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(6.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		if (ZombieSorcerer.spawn(level, pos, lookAt) == null) {
			source.sendFailure(Component.literal("Zombie sorcerer is disabled"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned zombie sorcerer"), true);
		return 1;
	}

	private static int spawnExcalibur(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		LivingEntity lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(6.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		if (ZombieExcalibur.spawn(level, pos, lookAt) == null) {
			source.sendFailure(Component.literal("Excalibur is disabled"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned Excalibur"), true);
		return 1;
	}

	private static int spawnMjolnir(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		LivingEntity lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(6.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		if (ZombieMjolnir.spawn(level, pos, lookAt) == null) {
			source.sendFailure(Component.literal("Mjolnir is disabled"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned Mjolnir"), true);
		return 1;
	}

	private static int spawnRitual(CommandSourceStack source, int head) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		LivingEntity lookAt = null;
		Vec3 pos;
		float yaw = 0.0f;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(6.0));
			lookAt = self;
			yaw = self.getYRot();
		} else {
			pos = source.getPosition();
		}
		if (!RitualBand.spawnCommand(level, pos, yaw, head, lookAt)) {
			source.sendFailure(Component.literal("Ritual band is disabled"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned ritual band"), true);
		return 1;
	}

	private static int spawnWitherSpawner(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		LivingEntity lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(4.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		if (SpawnerHeadMob.spawnWitherCarrier(level, pos, lookAt) == null) {
			source.sendFailure(Component.literal("Spawner head is disabled"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned wither skeleton with a spawner"), true);
		return 1;
	}

	private static int spawnAlchemist(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		LivingEntity lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(10.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		if (BlazeAlchemist.spawn(level, pos, lookAt) == null) {
			source.sendFailure(Component.literal("Blaze alchemist is disabled"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned blaze alchemist"), true);
		return 1;
	}

	private static int spawnNullifierWitch(CommandSourceStack source) {
		if (!(source.getLevel() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Must run on server level"));
			return 0;
		}
		LivingEntity lookAt = null;
		Vec3 pos;
		if (source.getEntity() instanceof ServerPlayer self) {
			pos = self.position().add(self.getLookAngle().scale(8.0));
			lookAt = self;
		} else {
			pos = source.getPosition();
		}
		if (WitchNullifier.spawn(level, pos, lookAt) == null) {
			source.sendFailure(Component.literal("Nullifier witch is disabled"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned nullifier witch"), true);
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
