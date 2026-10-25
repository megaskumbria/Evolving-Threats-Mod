package com.example.overseer;

import com.example.entity.ZombieOverseerEntity;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * Редкий мини-босс: «Гнилой надсмотрщик».
 * Настройки и выдача экипировки — здесь; ИИ — в {@code Overseer*Goal}.
 */
public final class ZombieOverseer {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Шанс при спавне обычного зомби стать надсмотрщиком. 0.006 = 0.6%. */
	public static final float SPAWN_CHANCE = 0.006f;

	public static final float MAX_HEALTH = 50.0f;

	/** Радиус, в котором держит/ищет цель (блоки). */
	public static final double FOLLOW_RANGE = 112.0;

	/** Дальше этого — охота сбрасывается (блоки). */
	public static final double HUNT_ABANDON_DISTANCE = 128.0;

	/** Сколько тиков (20/сек) охотиться, если игрок всё время слишком далеко — 2 минуты. */
	public static final int HUNT_TIMEOUT_TICKS = 20 * 60 * 2;

	public static final double BOSS_BAR_SHOW_DISTANCE = 64.0;

	public static final double SUMMON_PLAYER_MAX_DISTANCE = 96.0;

	public static final int SUMMON_COOLDOWN_TICKS = 25 * 20;

	/**
	 * Если рядом нет свободного места и зомби не появились, пауза короче.
	 * 100 тиков = 5 секунд. Иначе он ждал бы полные 25 секунд у стены впустую.
	 * 20 тиков = 1 секунда.
	 */
	public static final int SUMMON_FAIL_COOLDOWN_TICKS = 100;

	public static final int SUMMON_ZOMBIES_PER_CAST = 2;

	/**
	 * Как далеко от надсмотрщика искать место для помощника, в блоках.
	 * 1.5 и 4.0 = от полутора до четырёх блоков в сторону.
	 */
	public static final double SUMMON_RADIUS_MIN = 1.5;
	public static final double SUMMON_RADIUS_MAX = 4.0;

	/**
	 * Сколько случайных точек вокруг перебрать, прежде чем отказаться.
	 * 12 хватает в коридоре. Больше — дольше ищет, если вокруг тесно.
	 */
	public static final int SUMMON_SPOT_TRIES = 12;

	/**
	 * На сколько блоков вверх и вниз от ног надсмотрщика ещё смотреть пол.
	 * 4 = не ставит зомби в потолок и не роняет в яму глубже четырёх блоков.
	 */
	public static final int SUMMON_VERTICAL_STEPS = 4;

	public static final int MAX_SERVANTS_ALIVE = 5;

	public static final int ARMOR_PROTECTION_LEVEL = 2;

	public static final int MAIN_AXE_SHARPNESS = 3;

	/** Острота топора во второй руке. Она прибавляется к удару вместе с уроном самого топора. */
	public static final int OFFHAND_AXE_SHARPNESS = 2;

	// ========================================================================

	public static final String DISPLAY_NAME = "Rotten Overseer";
	public static final String BOSS_BAR_SUBTITLE = "Rallying the horde";

	private ZombieOverseer() {
	}

	public static boolean tryCreateOnSpawn(Zombie zombie, ServerLevel level) {
		if (!(zombie instanceof ZombieOverseerEntity overseer)) {
			return false;
		}
		if (overseer.evolvingthreats$isOverseer()) {
			return false;
		}
		if (zombie.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}

		applyOverseer(zombie, level);
		Player hunt = findNearestPlayer(level, zombie, HUNT_ABANDON_DISTANCE);
		if (hunt != null) {
			overseer.evolvingthreats$setHuntTargetId(hunt.getUUID());
			overseer.evolvingthreats$setHuntStartedGameTime(level.getGameTime());
			zombie.setTarget(hunt);
		}
		return true;
	}

	/**
	 * Спавн надсмотрщика командой / яйцом (100% мини-босс, без рандом-лута).
	 *
	 * @param hunter игрок-цель; если null — ближайший в радиусе охоты
	 */
	public static Zombie spawnOverseer(ServerLevel level, Vec3 pos, @Nullable ServerPlayer hunter) {
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		if (zombie == null) {
			throw new IllegalStateException("Failed to create zombie for overseer spawn");
		}

		zombie.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0f, 0.0f);
		applyOverseer(zombie, level);

		ServerPlayer huntTarget = hunter;
		if (huntTarget == null) {
			huntTarget = (ServerPlayer) findNearestPlayer(level, zombie, HUNT_ABANDON_DISTANCE);
		}
		if (huntTarget != null && zombie instanceof ZombieOverseerEntity overseer) {
			overseer.evolvingthreats$setHuntTargetId(huntTarget.getUUID());
			overseer.evolvingthreats$setHuntStartedGameTime(level.getGameTime());
			zombie.setTarget(huntTarget);
		}

		level.addFreshEntity(zombie);
		return zombie;
	}

	public static void applyOverseer(Zombie zombie, ServerLevel level) {
		if (!(zombie instanceof ZombieOverseerEntity overseer)) {
			return;
		}
		overseer.evolvingthreats$setOverseer(true);
		overseer.evolvingthreats$setActiveServantCount(0);

		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(MAX_HEALTH);
		zombie.setHealth(MAX_HEALTH);
		zombie.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(FOLLOW_RANGE);

		zombie.setCustomName(Component.literal(DISPLAY_NAME));
		zombie.setCustomNameVisible(true);

		equipArmor(zombie, level);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, enchantAxe(level, MAIN_AXE_SHARPNESS));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, enchantAxe(level, OFFHAND_AXE_SHARPNESS));

		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.isArmor()) {
				zombie.setDropChance(slot, 0.12f);
			}
		}
		zombie.setDropChance(EquipmentSlot.MAINHAND, 0.12f);
		zombie.setDropChance(EquipmentSlot.OFFHAND, 0.0f);

		OverseerBossBars.createFor(zombie);
	}

	private static void equipArmor(Zombie zombie, ServerLevel level) {
		zombie.setItemSlot(EquipmentSlot.HEAD, enchantArmor(level, Items.DIAMOND_HELMET));
		zombie.setItemSlot(EquipmentSlot.CHEST, enchantArmor(level, Items.DIAMOND_CHESTPLATE));
		zombie.setItemSlot(EquipmentSlot.LEGS, enchantArmor(level, Items.DIAMOND_LEGGINGS));
		zombie.setItemSlot(EquipmentSlot.FEET, enchantArmor(level, Items.DIAMOND_BOOTS));
	}

	private static ItemStack enchantArmor(ServerLevel level, net.minecraft.world.level.ItemLike item) {
		ItemStack stack = new ItemStack(item);
		Holder<Enchantment> protection = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.PROTECTION);
		EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(protection, ARMOR_PROTECTION_LEVEL));
		return stack;
	}

	private static ItemStack enchantAxe(ServerLevel level, int sharpness) {
		ItemStack stack = new ItemStack(Items.DIAMOND_AXE);
		Holder<Enchantment> sharp = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.SHARPNESS);
		EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(sharp, sharpness));
		return stack;
	}

	public static Player findNearestPlayer(Level level, Zombie zombie, double maxDistance) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return null;
		}
		double maxDistSqr = maxDistance * maxDistance;
		AABB box = zombie.getBoundingBox().inflate(maxDistance);
		List<ServerPlayer> players = serverLevel.getEntitiesOfClass(ServerPlayer.class, box,
				p -> p.isAlive() && !p.isCreative() && !p.isSpectator());

		return players.stream()
				.filter(p -> zombie.distanceToSqr(p) <= maxDistSqr)
				.min(Comparator.comparingDouble(zombie::distanceToSqr))
				.orElse(null);
	}

	public static BossEvent.BossBarColor bossBarColor() {
		return BossEvent.BossBarColor.GREEN;
	}

	public static BossEvent.BossBarOverlay bossBarOverlay() {
		return BossEvent.BossBarOverlay.NOTCHED_10;
	}

	/** Считает призванных зомби рядом с тем же target (для лимита слуг). */
	public static void refreshServantCount(Zombie overseer, ServerLevel level) {
		if (!(overseer instanceof ZombieOverseerEntity data) || !data.evolvingthreats$isOverseer()) {
			return;
		}
		var huntId = data.evolvingthreats$getHuntTargetId();
		int count = (int) level.getEntitiesOfClass(Zombie.class, overseer.getBoundingBox().inflate(48), z -> {
			if (z == overseer) {
				return false;
			}
			if (z instanceof ZombieOverseerEntity other && other.evolvingthreats$isOverseer()) {
				return false;
			}
			if (huntId == null) {
				return z.getTarget() != null;
			}
			return z.getTarget() != null && huntId.equals(z.getTarget().getUUID());
		}).size();
		data.evolvingthreats$setActiveServantCount(count);
	}
}
