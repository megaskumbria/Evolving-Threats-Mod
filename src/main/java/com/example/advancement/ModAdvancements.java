package com.example.advancement;

import com.example.EvolvingThreats;
import com.example.MobShields;
import com.example.RandomWeapons;
import com.example.bastion.Bastion;
import com.example.entity.RaidParticipantEntity;
import com.example.raid.RaidAnchor;
import com.example.siege.SiegeComposition;
import com.example.spiky.Spiky;
import com.example.threat.ThreatData;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import com.example.tool.FloatingTool;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Separate tab on the advancements screen.
 *
 * <p>Icons, frames, and who unlocks whom are JSON files in
 * {@code data/evolving-threats/advancement}. The words the player reads are in
 * {@code lang/en_us.json} and {@code lang/ru_ru.json}.
 *
 * <p>Minecraft cannot see a siege, a Spiky, or the threat scale, so every file
 * uses the "impossible" trigger. This class grants it when the moment happens.
 * The criterion name in every file is {@link #CRITERION} — keep them the same.
 *
 * <p>Phase days are not copied here. They follow the skull on the threat bar
 * and the full bar: day {@link ThreatTable#SIEGE_FIRST_DAY} (25),
 * day {@link ThreatTable#SKELETON_CAPTAIN_DAY} (60),
 * day {@link ThreatTable#BASILISK_DAY} (80),
 * then {@link ThreatScale#DAYS_TO_MAX} (100). Change those and these move with them.
 * {@code /evolvingthreats day set} grants every phase the new day has reached.
 * Setting the day backward does not take a phase away.
 */
public final class ModAdvancements {

	// ===================== SETTINGS (change these numbers) =====================

	/**
	 * false — the mod tab never unlocks and none of these advancements are granted.
	 * true — each one is granted when its moment happens (see the methods below).
	 */
	public static final boolean ENABLED = true;

	/**
	 * How often to look through inventories for a shield a mob was wearing.
	 * 20 = once a second. Lower = the toast shows sooner after pickup. 1 = every tick.
	 * Phase days are checked on their own, every tick; this number is only the shield.
	 */
	public static final int SHIELD_CHECK_INTERVAL_TICKS = 20;

	/**
	 * Rewards are not numbers here. The advancements screen reads them from JSON,
	 * and the game gives them once, the first time the advancement completes.
	 * Change a number there and both the screen and the payout change.
	 *
	 * <p>Experience is {@code rewards.experience} in
	 * {@code data/evolving-threats/advancement/<name>.json}.
	 * These are points on the level bar, not orbs on the ground.
	 * 20 is a small bump early. 500 at the end is a couple of levels, not fifty.
	 * Items are {@code data/evolving-threats/loot_table/advancement/<name>.json}.
	 * {@code count} is how many of that item. The advancement file names that table
	 * in {@code rewards.loot}. No loot line means experience only.
	 *
	 * <p>What each one gives:
	 * <ul>
	 *   <li>phase_1 — 8 iron, 20 xp. Day 25, a start on armor, not a full set.</li>
	 *   <li>until_dawn — 1 golden apple, 16 arrows, 50 xp.</li>
	 *   <li>mob_shield — 25 xp. The shield is already in the inventory.</li>
	 *   <li>dual_wield — 40 xp.</li>
	 *   <li>floating_tool — 40 xp.</li>
	 *   <li>mirror — regeneration potion (45 seconds, potion id {@code minecraft:regeneration}), 30 xp.</li>
	 *   <li>phase_2 — 3 diamonds, 100 xp. Enough for a pickaxe.</li>
	 *   <li>phase_3 — 1 totem, 150 xp. One save, once Basilisk and the raid anchor exist.</li>
	 *   <li>illager_siege — 8 emeralds, 100 xp.</li>
	 *   <li>spiky — Protection III book ({@code minecraft:protection} level 3), 100 xp.</li>
	 *   <li>phase_4 — enchanted golden apple, 500 xp. The scale is full.</li>
	 *   <li>bastion — 1 netherite scrap, 200 xp. One step of an upgrade, not an ingot.</li>
	 *   <li>raid_anchor — 4 golden apples, 200 xp. Servants are still alive.</li>
	 * </ul>
	 * An advancement that was already earned in this world does not pay again.
	 * To try it a second time: {@code /advancement revoke @s only evolving-threats:phase_1}
	 * (swap in the file name).
	 */

	// ========================================================================

	/**
	 * Criterion name inside every advancement JSON. Granting any other name does nothing.
	 */
	public static final String CRITERION = "done";

	private static final Identifier ROOT = EvolvingThreats.id("root");
	private static final Identifier PHASE_1 = EvolvingThreats.id("phase_1");
	private static final Identifier PHASE_2 = EvolvingThreats.id("phase_2");
	private static final Identifier PHASE_3 = EvolvingThreats.id("phase_3");
	private static final Identifier PHASE_4 = EvolvingThreats.id("phase_4");
	private static final Identifier UNTIL_DAWN = EvolvingThreats.id("until_dawn");
	private static final Identifier ILLAGER_SIEGE = EvolvingThreats.id("illager_siege");
	private static final Identifier MOB_SHIELD = EvolvingThreats.id("mob_shield");
	private static final Identifier DUAL_WIELD = EvolvingThreats.id("dual_wield");
	private static final Identifier FLOATING_TOOL = EvolvingThreats.id("floating_tool");
	private static final Identifier MIRROR = EvolvingThreats.id("mirror");
	private static final Identifier SPIKY = EvolvingThreats.id("spiky");
	private static final Identifier BASTION = EvolvingThreats.id("bastion");
	private static final Identifier RAID_ANCHOR = EvolvingThreats.id("raid_anchor");

	/** Last scale day we already handed out. -1 so the first tick always checks. */
	private static int lastPhaseDay = -1;

	private ModAdvancements() {
	}

	public static void register() {
		// Join: a world already on day 80 should hand phases 1–3 immediately, not on the next day.
		ServerPlayerEvents.JOIN.register(ModAdvancements::onJoin);
		ServerTickEvents.END_LEVEL_TICK.register(ModAdvancements::tick);
	}

	/**
	 * Start of death, while both hands are still full.
	 * Called from mixin/LivingEntityAdvancementMixin. A totem stops death before this runs.
	 */
	public static void onDying(LivingEntity entity, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
			return;
		}
		if (entity instanceof FloatingTool) {
			grant(player, FLOATING_TOOL);
		}
		if (!(entity instanceof Mob mob)) {
			return;
		}
		if (Spiky.isSpiky(mob)) {
			grant(player, SPIKY);
		}
		if (Bastion.isBastion(mob)) {
			grant(player, BASTION);
		}
		if (holdsWeaponInBothHands(mob)) {
			grant(player, DUAL_WIELD);
		}
		if (mob instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidAnchor()) {
			UUID raidId = raid.evolvingthreats$getRaidId();
			if (raidId != null
					&& entity.level() instanceof ServerLevel level
					&& RaidAnchor.hasLivingMinion(level, raidId, entity.position())) {
				grant(player, RAID_ANCHOR);
			}
		}
	}

	/**
	 * Melee hit that actually landed on a Mirror. Arrows and explosions do not count.
	 * Called from {@link com.example.mirror.Mirror#onHurt} after those are filtered out.
	 */
	public static void onMirrorMelee(ServerPlayer player) {
		grant(player, MIRROR);
	}

	/**
	 * Dawn after a real siege night (not {@code /siege stop}, not a night skipped in peaceful).
	 * Only players alive in the overworld. Someone who spent the night in the Nether or End
	 * is not in this level, so they do not get it. An illager mix also grants the second one.
	 */
	public static void onSiegeDawn(ServerLevel level, SiegeComposition.@Nullable Faction faction) {
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			grant(player, UNTIL_DAWN);
			if (faction == SiegeComposition.Faction.ILLAGER) {
				grant(player, ILLAGER_SIEGE);
			}
		}
	}

	private static void onJoin(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		// Before the scale is saved, day() would count from world creation and hand out late phases.
		ServerLevel overworld = server.overworld();
		if (!ThreatScale.ENABLED || ThreatData.get(overworld).initialized) {
			grantReached(overworld, player);
		} else {
			grant(player, ROOT);
		}
		if (MobShields.carriesMobShield(player)) {
			grant(player, MOB_SHIELD);
		}
	}

	private static void tick(ServerLevel level) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		// Scale not remembered yet: day() would count from world creation and hand out late phases.
		if (ThreatScale.ENABLED && !ThreatData.get(level).initialized) {
			return;
		}
		int day = ThreatScale.day(level);
		boolean dayChanged = day != lastPhaseDay;
		lastPhaseDay = day;
		boolean shieldTick = level.getGameTime() % Math.max(1, SHIELD_CHECK_INTERVAL_TICKS) == 0;
		if (!dayChanged && !shieldTick) {
			return;
		}
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			if (dayChanged) {
				grantReached(level, player);
			}
			if (shieldTick && MobShields.carriesMobShield(player)) {
				grant(player, MOB_SHIELD);
			}
		}
	}

	/**
	 * Root opens the tab. Phases use {@link ThreatScale#reached}: with the scale turned off
	 * every day gate is open, so all four phases complete at once.
	 */
	private static void grantReached(ServerLevel overworld, ServerPlayer player) {
		grant(player, ROOT);
		if (ThreatScale.reached(overworld, ThreatTable.SIEGE_FIRST_DAY)) {
			grant(player, PHASE_1);
		}
		if (ThreatScale.reached(overworld, ThreatTable.SKELETON_CAPTAIN_DAY)) {
			grant(player, PHASE_2);
		}
		if (ThreatScale.reached(overworld, ThreatTable.BASILISK_DAY)) {
			grant(player, PHASE_3);
		}
		if (ThreatScale.reached(overworld, ThreatScale.DAYS_TO_MAX)) {
			grant(player, PHASE_4);
		}
	}

	/** Zombie or skeleton (any kind) with one of our melee weapons in each hand. A bow in one hand does not count. */
	private static boolean holdsWeaponInBothHands(Mob mob) {
		if (!(mob instanceof Zombie) && !(mob instanceof AbstractSkeleton)) {
			return false;
		}
		return RandomWeapons.isModMeleeWeapon(mob.getMainHandItem())
				&& RandomWeapons.isModMeleeWeapon(mob.getOffhandItem());
	}

	private static void grant(ServerPlayer player, Identifier id) {
		if (!ENABLED) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		AdvancementHolder holder = server.getAdvancements().get(id);
		if (holder == null) {
			return;
		}
		var progress = player.getAdvancements().getOrStartProgress(holder);
		if (progress.isDone()) {
			return;
		}
		player.getAdvancements().award(holder, CRITERION);
	}
}
