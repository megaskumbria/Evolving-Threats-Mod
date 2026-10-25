package com.example.zombie;

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
 * Зомби с динамитом вместо меча.
 * Блоки не ломает. Если до игрока нет прохода или здоровья меньше порога —
 * роняет под ноги обычный подожжённый динамит.
 */
public final class TntZombie {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — такие зомби не появляются, команда тоже не спавнит. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный зомби при появлении возьмёт динамит.
	 * 0.04 = 4%. Утопленник, зомби-пиглин, надсмотрщик и рейд этот шанс не бросают.
	 * Команда шанс не использует.
	 */
	public static final float SPAWN_CHANCE = 0.04f;

	/**
	 * Бросать динамит, если здоровья меньше этого числа.
	 * 3 = меньше трёх единиц здоровья (полтора сердца). 6 = меньше трёх сердец.
	 * Ровно 3 — ещё не бросает. 0 = из-за здоровья не бросает никогда.
	 */
	public static final float LOW_HEALTH = 3.0f;

	/**
	 * Как далеко (блоки) ещё считать, что путь к игроку «заблокирован».
	 * 32 = как у копания. Дальше зомби просто не видит дорогу и динамит не роняет.
	 */
	public static final double PATH_CHECK_BLOCKS = 32.0;

	/**
	 * Как часто проверять путь, тики. 10 = раз в полсекунды.
	 * Чаще — точнее, но тяжелее для игры. Реже — дольше стоит у стены перед броском.
	 */
	public static final int PATH_CHECK_INTERVAL_TICKS = 10;

	/**
	 * Пауза между динамитами, секунды. 4 = один за раз, как раз пока горит запал.
	 * 0 = каждый тик, пока условие держится: под ногами появится пачка динамита.
	 */
	public static final int THROW_COOLDOWN_SECONDS = 4;

	/**
	 * Запал, тики (20 тиков = 1 секунда). 80 = 4 секунды, как у обычного подожжённого динамита.
	 * Взрыв по силе тоже обычный: блоки ломает, если это не запрещено правилом игры.
	 */
	public static final int FUSE_TICKS = 80;

	/**
	 * Шанс (0.0–1.0), что динамит из руки упадёт при смерти. 0.085 ≈ 8.5%, как прочая экипировка.
	 * 0 = из руки не падает. Взрыв под ногами этот предмет часто всё равно уничтожает.
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
	 * Конец появления зомби. true = это зомби с динамитом: меч, щит и тотем ему не выдаём.
	 * Броня и зелья мода остаются.
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
		if (zombie.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		data.evolvingthreats$setTntZombie(true);
		equip(zombie);
		return true;
	}

	/** Команда: зомби с динамитом перед игроком. null — механика выключена. */
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

	/** Конец тика цели. Следит за паузой и роняет динамит, когда пора. */
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
	 * Путь к игроку не находится и впереди есть блок.
	 * Игрок уже в зоне удара или просто далеко — это не «заблокирован».
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
	}

	private static @Nullable Player chasePlayer(Zombie zombie) {
		if (zombie.getTarget() instanceof Player player && player.isAlive()) {
			return player;
		}
		return PlayerGrudge.resolveGrudgePlayer(zombie);
	}

	/** Блок с толщей прямо перед зомби (ноги или голова) или пол, если игрок сильно ниже. */
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
