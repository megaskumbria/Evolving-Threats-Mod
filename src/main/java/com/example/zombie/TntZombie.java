package com.example.zombie;

import com.example.threat.ThreatScale;

import com.example.digging.BlockBreaking;
import com.example.entity.RaidParticipantEntity;
import com.example.entity.TntZombieEntity;
import com.example.entity.ZombieOverseerEntity;
import com.example.grudge.PlayerGrudge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Zombie holding TNT instead of a sword.
 * Does not break blocks. If the path to the player is blocked or health is below the threshold,
 * drops lit TNT at its feet (vanilla primed TNT).
 */
public final class TntZombie {

	// ===================== SETTINGS (change these numbers) =====================

	/** Enable the mechanic. false — no TNT zombies spawn; command spawn is disabled too. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) that a normal zombie spawns with TNT.
	 * 0.04 = 4% on day 100 (GROWING: 10× lower at game start; see ThreatTable.DANGEROUS_START_RATIO).
	 * Drowned, zombified piglin, overseer, and raid zombies never roll this.
	 * Command spawn ignores the chance.
	 */
	public static final float SPAWN_CHANCE = 0.04f;

	/**
	 * Drop TNT when health is below this value.
	 * 3 = below 3 health (1.5 hearts). 6 = below 3 hearts.
	 * Exactly 3 health does not trigger. 0 = never drop because of health.
	 */
	public static final float LOW_HEALTH = 3.0f;

	/**
	 * How far (blocks) to still treat the path to the player as "blocked".
	 * 32 = same as block breaking. Beyond that the zombie does not drop TNT.
	 */
	public static final double PATH_CHECK_BLOCKS = 32.0;

	/**
	 * How often to check the path, in ticks. 10 = twice per second.
	 * More often = more accurate but heavier on the server. Less often = longer wait at a wall before dropping.
	 */
	public static final int PATH_CHECK_INTERVAL_TICKS = 10;

	/**
	 * Cooldown between TNT drops, in seconds. 4 = one at a time, roughly while the fuse burns.
	 * 0 = every tick while the condition holds — a stack of TNT under the feet.
	 */
	public static final int THROW_COOLDOWN_SECONDS = 4;

	/**
	 * Fuse length in ticks (20 ticks = 1 second). 80 = 4 seconds, like vanilla primed TNT.
	 * Explosion power is normal vanilla; blocks break unless the world rules forbid it.
	 */
	public static final int FUSE_TICKS = 80;

	/**
	 * Chance (0.0–1.0) that TNT in hand drops on death. 0.085 ≈ 8.5%, like other mob gear.
	 * 0 = never from hand. TNT placed at the feet often destroys the item anyway.
	 */
	public static final float TNT_DROP_CHANCE = 0.085f;

	// ========================================================================

	private TntZombie() {
	}

	public static boolean isTntZombie(Zombie zombie) {
		return ENABLED
				&& zombie instanceof TntZombieEntity data
				&& data.evolvingthreats$isTntZombie();
	}

	/**
	 * End of zombie spawn. true = TNT zombie: no sword, shield, or totem.
	 * Mod armor and potion buffs still apply.
	 */
	public static boolean tryClaim(Zombie zombie, EntitySpawnReason reason) {
		if (!canBeTntZombie(zombie)) {
			return false;
		}
		TntZombieEntity data = (TntZombieEntity) zombie;
		if (data.evolvingthreats$isTntZombie()) {
			equip(zombie);
			return true;
		}
		if (reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.MOB_SUMMONED) {
			return false;
		}
		if (zombie.getRandom().nextFloat() >= ThreatScale.dangerousChance(zombie.level(), SPAWN_CHANCE)) {
			return false;
		}
		data.evolvingthreats$setTntZombie(true);
		equip(zombie);
		return true;
	}

	/** Command: TNT zombie in front of the player. null — mechanic disabled. */
	public static @Nullable Zombie spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return null;
		}
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		if (zombie == null) {
			throw new IllegalStateException("Failed to create TNT zombie");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		zombie.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (zombie instanceof TntZombieEntity data) {
			data.evolvingthreats$setTntZombie(true);
		}
		zombie.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(zombie.blockPosition()),
				EntitySpawnReason.COMMAND,
				(SpawnGroupData) null);
		if (lookAt != null) {
			zombie.setTarget(lookAt);
		}
		level.addFreshEntity(zombie);
		return zombie;
	}

	/** End-of-tick on the mob. Handles cooldown and drops TNT when appropriate. */
	public static void tick(Zombie zombie) {
		if (!(zombie instanceof TntZombieEntity data) || !data.evolvingthreats$isTntZombie()) {
			return;
		}
		int cooldown = data.evolvingthreats$getTntCooldown();
		if (cooldown > 0) {
			data.evolvingthreats$setTntCooldown(cooldown - 1);
			return;
		}
		if (!(zombie.level() instanceof ServerLevel level)) {
			return;
		}
		boolean lowHealth = LOW_HEALTH > 0.0f && zombie.getHealth() < LOW_HEALTH;
		boolean checkPath = PATH_CHECK_INTERVAL_TICKS <= 1
				|| zombie.tickCount % Math.max(1, PATH_CHECK_INTERVAL_TICKS) == 0;
		if (lowHealth || (checkPath && isPathBlocked(zombie))) {
			dropUnderFeet(level, zombie);
			data.evolvingthreats$setTntCooldown(Math.max(0, THROW_COOLDOWN_SECONDS) * 20);
		}
	}

	/**
	 * No path to the player and a solid block ahead.
	 * Player in melee range or very far away does not count as "blocked".
	 */
	public static boolean isPathBlocked(Zombie zombie) {
		Player player = chasePlayer(zombie);
		if (player == null) {
			return false;
		}
		if (zombie.distanceTo(player) > PATH_CHECK_BLOCKS) {
			return false;
		}
		if (BlockBreaking.shouldPreferMeleeOverDig(zombie, player)) {
			return false;
		}
		Path path = zombie.getNavigation().createPath(player, 0);
		if (path != null && path.canReach()) {
			return false;
		}
		return hasObstacleInFront(zombie, player);
	}

	private static boolean canBeTntZombie(Zombie zombie) {
		if (!ENABLED || !(zombie instanceof TntZombieEntity)) {
			return false;
		}
		if (zombie instanceof Drowned || zombie instanceof ZombifiedPiglin) {
			return false;
		}
		if (zombie instanceof ZombieOverseerEntity overseer && overseer.evolvingthreats$isOverseer()) {
			return false;
		}
		return !(zombie instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant());
	}

	private static void equip(Zombie zombie) {
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TNT));
		zombie.setDropChance(EquipmentSlot.MAINHAND, Math.max(0.0f, TNT_DROP_CHANCE));
	}

	private static void dropUnderFeet(ServerLevel level, Zombie zombie) {
		PrimedTnt tnt = new PrimedTnt(level, zombie.getX(), zombie.getY(), zombie.getZ(), zombie);
		if (FUSE_TICKS > 0) {
			tnt.setFuse(FUSE_TICKS);
		}
		level.addFreshEntity(tnt);
		level.playSound(null, zombie.blockPosition(), SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1.0f, 1.0f);
		Player chasing = chasePlayer(zombie);
		if (chasing instanceof net.minecraft.server.level.ServerPlayer serverPlayer
				&& zombie.distanceTo(chasing) <= Math.max(0.0, PATH_CHECK_BLOCKS)) {
			com.example.advancement.ModAdvancements.onFuseUnderfoot(serverPlayer);
		}
	}

	private static @Nullable Player chasePlayer(Zombie zombie) {
		if (zombie.getTarget() instanceof Player player && player.isAlive()) {
			return player;
		}
		return PlayerGrudge.resolveGrudgePlayer(zombie);
	}

	/** Solid block in front of the zombie (feet or head), or floor when the player is much lower. */
	private static boolean hasObstacleInFront(Zombie zombie, LivingEntity target) {
		Level level = zombie.level();
		BlockPos feet = zombie.blockPosition();
		Direction dir = horizontalDirectionTo(zombie, target);
		BlockPos front = feet.relative(dir);
		for (int above = 0; above <= 1; above++) {
			BlockPos pos = front.above(above);
			if (blocksMovement(level, pos)) {
				return true;
			}
		}
		if (target.getY() <= feet.getY() - 2.0 && blocksMovement(level, feet.below())) {
			return true;
		}
		return false;
	}

	private static boolean blocksMovement(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.getCollisionShape(level, pos).isEmpty();
	}

	private static Direction horizontalDirectionTo(Zombie zombie, LivingEntity target) {
		double dx = target.getX() - zombie.getX();
		double dz = target.getZ() - zombie.getZ();
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0.0 ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0.0 ? Direction.SOUTH : Direction.NORTH;
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
