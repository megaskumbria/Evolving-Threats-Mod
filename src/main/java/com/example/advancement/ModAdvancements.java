package com.example.advancement;

import com.example.EvolvingThreats;
import com.example.mixin.PlayerAdvancementsDirtyAccessor;
import com.example.MobShields;
import com.example.RandomArmor;
import com.example.RandomWeapons;
import com.example.bastion.Bastion;
import com.example.creeper.DynamiteCreeper;
import com.example.creeper.FeintCreeper;
import com.example.entity.RaidParticipantEntity;
import com.example.entity.ZombieOverseerEntity;
import com.example.golem.DamagedGolem;
import com.example.raid.RaidAnchor;
import com.example.ritual.RitualBand;
import com.example.siege.SiegeComposition;
import com.example.skeleton.BasiliskSkeleton;
import com.example.skeleton.SkeletonCaptain;
import com.example.skeleton.SniperSkeleton;
import com.example.spiky.Spiky;
import com.example.threat.ThreatData;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import com.example.tool.FloatingTool;
import com.example.zombie.ZombieExcalibur;
import com.example.zombie.ZombieMjolnir;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * The criterion name in every file is {@link #CRITERION}, except {@code tool_shapes}:
 * that card has one criterion per shape ({@code sword}, {@code axe}, {@code shovel},
 * {@code pickaxe}, {@code hoe}) so the screen can show 3 out of 5.
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
	 * Zombie and skeleton kills, counted together, any kind of each.
	 * 10 opens the first card, 100 the goal, 1000 the challenge.
	 * Husks, drowned, zombie villagers, strays, bogged, parched, and wither skeletons count.
	 * Zombified piglins do not: a nether farm would finish the chain without the night horde.
	 * The game already remembers these kills, so an old world can complete a card on login.
	 */
	public static final int KILL_COUNT_FIRST = 10;
	public static final int KILL_COUNT_SECOND = 100;
	public static final int KILL_COUNT_THIRD = 1000;

	/**
	 * How close (blocks) a player must be when a zombie breaks a light
	 * for that card to count. 32 matches the extra sight range mobs use.
	 * Farther than this, the toast would fire for a light you never saw.
	 */
	public static final double LIGHT_BROKEN_NEAR_BLOCKS = 32.0;

	/**
	 * How close (blocks) a player must be when the small spiders appear
	 * beside the big one. 32 = you were there for it.
	 * Farther than this, the card does not count.
	 */
	public static final double LITTLE_ONES_NEAR_BLOCKS = 32.0;

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
	 *   <li>mob_totem — 30 xp. A mob's totem just saved it.</li>
	 *   <li>cobweb — 20 xp. A spider placed web under the player.</li>
	 *   <li>dynamite_creeper — 40 xp.</li>
	 *   <li>full_set — Unbreaking III book ({@code minecraft:unbreaking}), 100 xp.</li>
	 *   <li>excalibur — Sharpness IV book ({@code minecraft:sharpness}), 100 xp. The sword still does not drop.</li>
	 *   <li>mjolnir — Knockback II book ({@code minecraft:knockback}), 100 xp.</li>
	 *   <li>overseer — 1 golden apple, 150 xp.</li>
	 *   <li>captain — 32 arrows, 100 xp.</li>
	 *   <li>nether_tool — fire resistance potion (3 minutes, {@code minecraft:fire_resistance}), 100 xp.</li>
	 *   <li>basilisk — Protection IV book, 200 xp.</li>
	 *   <li>sniper — Power V book ({@code minecraft:power}), 200 xp.</li>
	 *   <li>feint_creeper — swiftness potion (3 minutes, {@code minecraft:swiftness}), 150 xp.</li>
	 *   <li>hostile_golem — 1 iron block, 200 xp.</li>
	 *   <li>ritual_band — Fortune III book ({@code minecraft:fortune}), 200 xp. The whole loaded pack is dead.</li>
	 *   <li>kills_10 — 40 xp. Ten zombies or skeletons.</li>
	 *   <li>kills_100 — 1 diamond, 100 xp.</li>
	 *   <li>kills_1000 — Mending book ({@code minecraft:mending}), 300 xp.</li>
	 *   <li>tool_shapes — Efficiency V book ({@code minecraft:efficiency}), 150 xp. All five shapes, any material.</li>
	 *   <li>through_wall — 30 xp. A creeper lit its fuse because a wall was in the way.</li>
	 *   <li>block_hole — 25 xp. A zombie broke a block to reach you.</li>
	 *   <li>light_out — 20 xp. A zombie broke a light within {@link #LIGHT_BROKEN_NEAR_BLOCKS}.</li>
	 *   <li>swapped — 40 xp. An enderman traded places with you.</li>
	 *   <li>horseman — saddle, 40 xp. The skeleton was still on the horse.</li>
	 *   <li>lifted — slow falling potion (1:30, {@code minecraft:slow_falling}), 100 xp.</li>
	 *   <li>reinforcements — strength potion (3:00, {@code minecraft:strength}), 100 xp.</li>
	 *   <li>varied_arrows — 8 spectral arrows, 100 xp.</li>
	 *   <li>wiped — milk bucket, 100 xp.</li>
	 *   <li>clear_bolt — 1 lightning rod, 100 xp.</li>
	 *   <li>fire_cut — 4 blaze rods, 200 xp. The ball shortens fire resistance, it does not delete it.</li>
	 *   <li>armor_crack — 8 iron, 200 xp.</li>
	 *   <li>relocated — 4 ender pearls, 200 xp.</li>
	 *   <li>patrol_home — 4 emeralds, 100 xp. A pillager, a vindicator, and a ravager are in the yard together.</li>
	 *   <li>shield_silence — 30 xp. An axe hit shut the raised shield off.</li>
	 *   <li>empty_hand — Looting II book ({@code minecraft:looting}), 100 xp. An empty-handed zombie took the item.</li>
	 *   <li>disarmed — 16 arrows, 100 xp. A skeleton arrow knocked the stack out of the hand.</li>
	 *   <li>shield_splinter — 1 shield, 100 xp. The blast destroyed the blocking shield and dealt no damage.</li>
	 *   <li>overheated — 4 magma cream, 100 xp. The cube started the faster, harder stage while chasing you.</li>
	 *   <li>blinded — night vision potion (3:00, {@code minecraft:night_vision}), 100 xp. The bite applied blindness.</li>
	 *   <li>witch_gone — 1 golden carrot, 100 xp. She started the invisibility drink, and you are the one she is fighting.</li>
	 *   <li>little_ones — 4 spider eyes, 100 xp. The small ones appeared within {@link #LITTLE_ONES_NEAR_BLOCKS}.</li>
	 *   <li>tool_groaned — 1 diamond, 200 xp. A hit on the closed shell took half the tool's durability.</li>
	 * </ul>
	 * Nothing is hidden. Parents in the JSON are only the tree: who is drawn after whom.
	 * The game shows the next two cards on a branch, the same way the vanilla tabs do.
	 * Earning a card in the middle of a branch shows the path that leads to it.
	 * A parent does not have to be done before the card can be earned.
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
	private static final Identifier MOB_TOTEM = EvolvingThreats.id("mob_totem");
	private static final Identifier COBWEB = EvolvingThreats.id("cobweb");
	private static final Identifier DYNAMITE_CREEPER = EvolvingThreats.id("dynamite_creeper");
	private static final Identifier FULL_SET = EvolvingThreats.id("full_set");
	private static final Identifier EXCALIBUR = EvolvingThreats.id("excalibur");
	private static final Identifier MJOLNIR = EvolvingThreats.id("mjolnir");
	private static final Identifier OVERSEER = EvolvingThreats.id("overseer");
	private static final Identifier CAPTAIN = EvolvingThreats.id("captain");
	private static final Identifier NETHER_TOOL = EvolvingThreats.id("nether_tool");
	private static final Identifier BASILISK = EvolvingThreats.id("basilisk");
	private static final Identifier SNIPER = EvolvingThreats.id("sniper");
	private static final Identifier FEINT_CREEPER = EvolvingThreats.id("feint_creeper");
	private static final Identifier HOSTILE_GOLEM = EvolvingThreats.id("hostile_golem");
	private static final Identifier RITUAL_BAND = EvolvingThreats.id("ritual_band");
	private static final Identifier KILLS_10 = EvolvingThreats.id("kills_10");
	private static final Identifier KILLS_100 = EvolvingThreats.id("kills_100");
	private static final Identifier KILLS_1000 = EvolvingThreats.id("kills_1000");
	private static final Identifier TOOL_SHAPES = EvolvingThreats.id("tool_shapes");
	private static final Identifier THROUGH_WALL = EvolvingThreats.id("through_wall");
	private static final Identifier BLOCK_HOLE = EvolvingThreats.id("block_hole");
	private static final Identifier LIGHT_OUT = EvolvingThreats.id("light_out");
	private static final Identifier SWAPPED = EvolvingThreats.id("swapped");
	private static final Identifier HORSEMAN = EvolvingThreats.id("horseman");
	private static final Identifier LIFTED = EvolvingThreats.id("lifted");
	private static final Identifier REINFORCEMENTS = EvolvingThreats.id("reinforcements");
	private static final Identifier VARIED_ARROWS = EvolvingThreats.id("varied_arrows");
	private static final Identifier WIPED = EvolvingThreats.id("wiped");
	private static final Identifier CLEAR_BOLT = EvolvingThreats.id("clear_bolt");
	private static final Identifier FIRE_CUT = EvolvingThreats.id("fire_cut");
	private static final Identifier ARMOR_CRACK = EvolvingThreats.id("armor_crack");
	private static final Identifier RELOCATED = EvolvingThreats.id("relocated");
	private static final Identifier PATROL_HOME = EvolvingThreats.id("patrol_home");
	private static final Identifier SHIELD_SILENCE = EvolvingThreats.id("shield_silence");
	private static final Identifier EMPTY_HAND = EvolvingThreats.id("empty_hand");
	private static final Identifier DISARMED = EvolvingThreats.id("disarmed");
	private static final Identifier SHIELD_SPLINTER = EvolvingThreats.id("shield_splinter");
	private static final Identifier OVERHEATED = EvolvingThreats.id("overheated");
	private static final Identifier BLINDED = EvolvingThreats.id("blinded");
	private static final Identifier WITCH_GONE = EvolvingThreats.id("witch_gone");
	private static final Identifier LITTLE_ONES = EvolvingThreats.id("little_ones");
	private static final Identifier TOOL_GROANED = EvolvingThreats.id("tool_groaned");

	/** Entity types that add to {@link #KILL_COUNT_FIRST} and the two cards after it. */
	private static final EntityType<?>[] COUNTED_UNDEAD = {
			EntityTypes.ZOMBIE,
			EntityTypes.HUSK,
			EntityTypes.DROWNED,
			EntityTypes.ZOMBIE_VILLAGER,
			EntityTypes.SKELETON,
			EntityTypes.STRAY,
			EntityTypes.BOGGED,
			EntityTypes.PARCHED,
			EntityTypes.WITHER_SKELETON
	};

	/** Last scale day we already handed out. -1 so the first tick always checks. */
	private static int lastPhaseDay = -1;

	private ModAdvancements() {
	}

	/**
	 * Marks every advancement in this mod done, so the tab can be looked at.
	 * Does not pay items or experience and does not write each one in chat.
	 * A card finished this way will not pay later either: the game only pays the first time.
	 * Returns how many cards were newly completed.
	 */
	public static int revealAll(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return 0;
		}
		var advancements = player.getAdvancements();
		var dirty = (PlayerAdvancementsDirtyAccessor) (Object) advancements;
		int count = 0;
		for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
			if (!EvolvingThreats.MOD_ID.equals(holder.id().getNamespace())) {
				continue;
			}
			AdvancementProgress progress = advancements.getOrStartProgress(holder);
			if (progress.isDone()) {
				continue;
			}
			boolean changed = false;
			for (String name : holder.value().criteria().keySet()) {
				if (progress.grantProgress(name)) {
					changed = true;
				}
			}
			if (!changed) {
				continue;
			}
			dirty.evolvingthreats$getProgressChanged().add(holder);
			dirty.evolvingthreats$markForVisibilityUpdate(holder);
			count++;
		}
		return count;
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
		if (isCountedUndead(entity.getType())) {
			// The statistic is written later in this same death, so this kill is not in it yet.
			grantKillTiers(player, recordedUndeadKills(player) + 1);
		}
		if (entity instanceof AbstractSkeleton && entity.getVehicle() instanceof SkeletonHorse) {
			grant(player, HORSEMAN);
		}
		if (entity instanceof FloatingTool tool) {
			grant(player, FLOATING_TOOL);
			grantToolShape(player, tool.getMainHandItem());
			if (tool.level().dimension() == Level.NETHER && isNetheriteTool(tool.getMainHandItem())) {
				grant(player, NETHER_TOOL);
			}
		}
		if (entity instanceof DamagedGolem) {
			grant(player, HOSTILE_GOLEM);
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
		if ((mob instanceof Zombie || mob instanceof AbstractSkeleton) && RandomArmor.wearsMatchedSet(mob)) {
			grant(player, FULL_SET);
		}
		if (mob instanceof Zombie zombie && ZombieExcalibur.isExcalibur(zombie)) {
			grant(player, EXCALIBUR);
		}
		if (mob instanceof Zombie zombie && ZombieMjolnir.isMjolnir(zombie)) {
			grant(player, MJOLNIR);
		}
		if (mob instanceof ZombieOverseerEntity overseer && overseer.evolvingthreats$isOverseer()) {
			grant(player, OVERSEER);
		}
		if (SkeletonCaptain.isCaptain(mob)) {
			grant(player, CAPTAIN);
		}
		if (BasiliskSkeleton.isBasilisk(mob)) {
			grant(player, BASILISK);
		}
		if (mob instanceof AbstractSkeleton skeleton && SniperSkeleton.isSniper(skeleton)) {
			grant(player, SNIPER);
		}
		if (mob instanceof Creeper creeper && DynamiteCreeper.isDynamite(creeper)) {
			grant(player, DYNAMITE_CREEPER);
		}
		if (mob instanceof Creeper creeper && FeintCreeper.isFeint(creeper)) {
			grant(player, FEINT_CREEPER);
		}
		if (RitualBand.isRitual(mob) && RitualBand.isLastLiving(mob)) {
			grant(player, RITUAL_BAND);
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

	/** A mob's totem just burned and left it alive. The player's own totem does not count. */
	public static void onMobTotem(LivingEntity saved, DamageSource source) {
		if (!(saved instanceof Mob)) {
			return;
		}
		if (!(source.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
			return;
		}
		grant(player, MOB_TOTEM);
	}

	/** A spider just placed cobweb in the block the player is standing in. */
	public static void onCobweb(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, COBWEB);
		}
	}

	/** A sapper creeper just lit its fuse because it could not see the player through a wall. */
	public static void onCoverBlast(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, THROUGH_WALL);
		}
	}

	/** A zombie finished breaking a block while this player was the one it was digging toward. */
	public static void onBlockBroken(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, BLOCK_HOLE);
		}
	}

	/** A zombie destroyed a light. Only players close enough to have seen it. */
	public static void onLightBroken(ServerLevel level, BlockPos pos) {
		double reach = Math.max(0.0, LIGHT_BROKEN_NEAR_BLOCKS);
		double reachSq = reach * reach;
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			if (player.distanceToSqr(x, y, z) <= reachSq) {
				grant(player, LIGHT_OUT);
			}
		}
	}

	/** An enderman and this player finished trading places. Being pulled out of water does not count. */
	public static void onSwapped(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, SWAPPED);
		}
	}

	/** A sorcerer launched this player upward or pulled them in. Setting them on fire does not count. */
	public static void onLifted(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, LIFTED);
		}
	}

	/** A spawner-head mob just called more of its own kind after this player's hits. */
	public static void onReinforcements(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, REINFORCEMENTS);
		}
	}

	/** One of the cycling-effect arrows just hit this player. */
	public static void onVariedArrow(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, VARIED_ARROWS);
		}
	}

	/** A white splash actually removed effects. A player with an empty effect bar does not count. */
	public static void onEffectsWiped(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, WIPED);
		}
	}

	/** A drowned trident called lightning when the storm did not. */
	public static void onClearBolt(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, CLEAR_BOLT);
		}
	}

	/** An alchemist fireball shortened this player's fire resistance. */
	public static void onFireCut(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, FIRE_CUT);
		}
	}

	/** A brute hit just took half the durability off one worn armor piece. */
	public static void onArmorCracked(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, ARMOR_CRACK);
		}
	}

	/** A shulker bullet moved this player to a different spot. A failed attempt does not count. */
	public static void onRelocated(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, RELOCATED);
		}
	}

	/**
	 * The daytime squad is in the yard: one pillager, one vindicator, and the ravager.
	 * Called only for a player already close to that point.
	 */
	public static void onPatrolArrived(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, PATROL_HOME);
		}
	}

	/** An axe blow just disabled this player's raised shield. A wooden axe does not count. */
	public static void onShieldSilenced(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, SHIELD_SILENCE);
		}
	}

	/** An unarmed zombie took the item out of this player's hand. A miss or an empty hand does not count. */
	public static void onEmptyHand(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, EMPTY_HAND);
		}
	}

	/** A skeleton arrow knocked at least one stack out of this player's hands. */
	public static void onDisarmed(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, DISARMED);
		}
	}

	/** The explosion destroyed the shield this player was blocking with, and the blast damage did not land. */
	public static void onShieldSplinter(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, SHIELD_SPLINTER);
		}
	}

	/** The cube just entered the faster, harder stage while chasing this player. Size 1 never does this. */
	public static void onOverheated(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, OVERHEATED);
		}
	}

	/** The bite applied blindness to this player. A bite that did not change the effect does not count. */
	public static void onBlinded(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, BLINDED);
		}
	}

	/** She just started drinking invisibility, and this player is the one she is fighting. */
	public static void onWitchGone(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, WITCH_GONE);
		}
	}

	/** The small spiders appeared. Only players close enough to have seen the pack. */
	public static void onLittleOnes(ServerLevel level, double x, double y, double z) {
		double reach = Math.max(0.0, LITTLE_ONES_NEAR_BLOCKS);
		double reachSq = reach * reach;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			if (player.distanceToSqr(x, y, z) <= reachSq) {
				grant(player, LITTLE_ONES);
			}
		}
	}

	/** A hit on a closed shell just removed durability from this player's tool, or broke it. */
	public static void onToolGroaned(ServerPlayer player) {
		if (!player.isSpectator()) {
			grant(player, TOOL_GROANED);
		}
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
		grantKillTiers(player, recordedUndeadKills(player));
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

	/** Nether floating tools are always netherite. An overworld tool dragged into the Nether is not. */
	private static boolean isNetheriteTool(ItemStack stack) {
		return stack.is(Items.NETHERITE_SWORD)
				|| stack.is(Items.NETHERITE_AXE)
				|| stack.is(Items.NETHERITE_SHOVEL)
				|| stack.is(Items.NETHERITE_PICKAXE)
				|| stack.is(Items.NETHERITE_HOE);
	}

	/** Zombie or skeleton (any kind) with one of our melee weapons in each hand. A bow in one hand does not count. */
	private static boolean holdsWeaponInBothHands(Mob mob) {
		if (!(mob instanceof Zombie) && !(mob instanceof AbstractSkeleton)) {
			return false;
		}
		return RandomWeapons.isModMeleeWeapon(mob.getMainHandItem())
				&& RandomWeapons.isModMeleeWeapon(mob.getOffhandItem());
	}

	/** Sword, axe, shovel, pickaxe, or hoe. Material does not matter, including netherite. */
	private static void grantToolShape(ServerPlayer player, ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		String shape = null;
		if (path.endsWith("_sword")) {
			shape = "sword";
		} else if (path.endsWith("_pickaxe")) {
			shape = "pickaxe";
		} else if (path.endsWith("_shovel")) {
			shape = "shovel";
		} else if (path.endsWith("_hoe")) {
			shape = "hoe";
		} else if (path.endsWith("_axe")) {
			shape = "axe";
		}
		if (shape != null) {
			grantCriterion(player, TOOL_SHAPES, shape);
		}
	}

	private static boolean isCountedUndead(EntityType<?> type) {
		for (EntityType<?> counted : COUNTED_UNDEAD) {
			if (type == counted) {
				return true;
			}
		}
		return false;
	}

	/** Kills the statistics screen already stored. Bow kills are in there too. */
	private static int recordedUndeadKills(ServerPlayer player) {
		int total = 0;
		for (EntityType<?> type : COUNTED_UNDEAD) {
			total += player.getStats().getValue(Stats.ENTITY_KILLED.get(type));
		}
		return total;
	}

	private static void grantKillTiers(ServerPlayer player, int kills) {
		if (kills >= KILL_COUNT_FIRST) {
			grant(player, KILLS_10);
		}
		if (kills >= KILL_COUNT_SECOND) {
			grant(player, KILLS_100);
		}
		if (kills >= KILL_COUNT_THIRD) {
			grant(player, KILLS_1000);
		}
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

	/** One named line on a card that has several, such as a tool shape. The reward waits until every line is done. */
	private static void grantCriterion(ServerPlayer player, Identifier id, String criterion) {
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
		player.getAdvancements().award(holder, criterion);
	}
}
