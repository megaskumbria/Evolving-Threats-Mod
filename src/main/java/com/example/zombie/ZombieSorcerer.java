package com.example.zombie;

import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;

import com.example.entity.RaidParticipantEntity;
import com.example.entity.ZombieOverseerEntity;
import com.example.entity.ZombieSorcererEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Редкий зомби в чёрной коже с незеритовой мотыгой.
 * Чары на вещи не получает. Мотыга при смерти не падает.
 * Раз в несколько секунд, если видит игрока, колдует один приём.
 * Чаще всего подбрасывает вверх. Реже поджигает или тянет к себе.
 */
public final class ZombieSorcerer {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — чародеи не появляются, команда тоже не спавнит. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный зомби окажется чародеем. 0.001 = 0,1%, один из тысячи.
	 * ФИКС: с 40-го дня (ThreatTable.SORCERER_DAY), от шкалы угрозы не растёт.
	 * Утопленник, кадавр, зомби-житель, детёныш, надсмотрщик и рейд этот шанс не бросают.
	 * Команда шанс не использует. 0 = только команда.
	 */
	public static final float SPAWN_CHANCE = 0.001f;

	/**
	 * Здоровье. 80 = 40 сердец. Обычный зомби в этом моде случайно 20–40.
	 * На чародея тот бросок не действует: здесь всегда это число.
	 * Меньше 1 игра почти сразу убивает.
	 */
	public static final float HEALTH = 80.0f;

	/**
	 * Опыт за убийство. 15 — в три раза больше обычного зомби (у того 5).
	 * Падает, только если зомби убил игрок: так устроена сама игра.
	 */
	public static final int XP_REWARD = 15;

	/**
	 * Как далеко (блоки) он колдует. 12 = игрок ещё не вплотную, но уже рядом.
	 * Дальше чародей просто идёт, как обычный зомби. 0 = приёмов нет.
	 */
	public static final double SPELL_RANGE_BLOCKS = 12.0;

	/**
	 * Пауза между приёмами, секунды. 8 = следующий поджог, подброс или рывок не раньше чем через 8 секунд.
	 * 0 = каждый тик, пока игрок в зоне: его будет подбрасывать без остановки.
	 * Пока игрока не видно, пауза не тратится: первый приём случается, как только чародей его увидел.
	 */
	public static final int SPELL_COOLDOWN_SECONDS = 8;

	/**
	 * Сколько секунд горит игрок после приёма огня. 6 = шесть секунд.
	 * Огнестойкость урон от огня снимает, как обычно. Щит этот приём не ловит.
	 */
	public static final int FIRE_SECONDS = 6;

	/**
	 * Как часто какой приём. Это веса: сумма 100 значит подброс 70%, огонь 15%, рывок 15%.
	 * Подброс — основной приём. 0 у веса = этого приёма нет.
	 * Если игрок ближе {@link #PULL_MIN_BLOCKS}, рывок не выбирается,
	 * и шансы подброса и огня делят оставшееся между собой.
	 */
	public static final int LAUNCH_WEIGHT = 70;
	public static final int FIRE_WEIGHT = 15;
	public static final int PULL_WEIGHT = 15;

	/**
	 * Обычный подброс, в блоках. Оба края входят: 25 и 30 тоже бывают, поровну между ними.
	 * Считается без зелий. Прыгучесть поднимет выше, плавное падение — тоже.
	 * Падение с 25 блоков бьёт почти на всю жизнь (урон падения = высота минус 3).
	 */
	public static final int LAUNCH_MIN_BLOCKS = 25;
	public static final int LAUNCH_MAX_BLOCKS = 30;

	/**
	 * Редкий подброс, в блоках. 50 = заметно выше обычных 25–30.
	 * Выпадает с шансом {@link #LAUNCH_RARE_CHANCE}, иначе высота из обычного диапазона.
	 */
	public static final int LAUNCH_RARE_BLOCKS = 50;

	/**
	 * Шанс (0.0–1.0) подбросить на {@link #LAUNCH_RARE_BLOCKS}, а не на 25–30.
	 * 0.10 = 10%, примерно каждый десятый подброс. 0 = всегда 25–30. 1 = всегда на 50.
	 */
	public static final float LAUNCH_RARE_CHANCE = 0.10f;

	/**
	 * Сила рывка к чародею. 1.6 = заметно притягивает, но не телепортирует вплотную.
	 * Больше — игрок пролетает почти до зомби. 0 = рывок ничего не делает.
	 * Сопротивление отбрасыванию этот рывок не ослабляет: это не удар.
	 */
	public static final double PULL_SPEED = 1.6;

	/**
	 * Ближе этого (блоки) рывок не выбирается: игрок и так рядом.
	 * Тогда приём будет поджогом или подбросом. 2.5 = примерно дистанция удара.
	 */
	public static final double PULL_MIN_BLOCKS = 2.5;

	/**
	 * Шанс (0.0–1.0), что при смерти от игрока упадёт одна лишняя вещь. 1.0 = всегда.
	 * 0 = только опыт, без сахара и остального. Если зомби сгорел или его взорвало — лишней вещи нет.
	 * Вещь одна, случайная, шансы равные: сахар, золотая морковка, фуга (звезда фейерверка),
	 * красный факел (редстоуновый) или один кусок чёрной кожи.
	 * Надетая броня и мотыга сами не падают.
	 */
	public static final float BONUS_DROP_CHANCE = 1.0f;

	// ========================================================================

	/** Притяжение за тик, как у игрока в воздухе. От него считается скорость подброса. */
	private static final double GRAVITY_PER_TICK = 0.08;
	private static final double VERTICAL_DRAG = 0.98;

	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD,
			EquipmentSlot.CHEST,
			EquipmentSlot.LEGS,
			EquipmentSlot.FEET
	};

	private static final Item[] LEATHER = {
			Items.LEATHER_HELMET,
			Items.LEATHER_CHESTPLATE,
			Items.LEATHER_LEGGINGS,
			Items.LEATHER_BOOTS
	};

	/** Сахар, золотая морковка, фуга, красный факел. Кожа считается отдельно, чтобы выпал один кусок. */
	private static final Item[] BONUS_ITEMS = {
			Items.SUGAR,
			Items.GOLDEN_CARROT,
			Items.FIREWORK_STAR,
			Items.REDSTONE_TORCH
	};

	private ZombieSorcerer() {
	}

	public static boolean isSorcerer(Zombie zombie) {
		return ENABLED
				&& zombie instanceof ZombieSorcererEntity data
				&& data.evolvingthreats$isSorcerer();
	}

	/**
	 * Конец появления зомби. true = это чародей: случайное оружие, броня, чары,
	 * щит, тотем, тыква и спавнер на голову ему не выдаём.
	 * Зелья при появлении уже могли повеситься — как у обычного зомби.
	 */
	public static boolean tryClaim(Zombie zombie, EntitySpawnReason reason) {
		if (!canBeSorcerer(zombie)) {
			return false;
		}
		ZombieSorcererEntity data = (ZombieSorcererEntity) zombie;
		if (data.evolvingthreats$isSorcerer()) {
			equip(zombie);
			return true;
		}
		if (reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.MOB_SUMMONED) {
			return false;
		}
		// С какого дня чародеи вообще появляются (ThreatTable.SORCERER_DAY). Шанс ниже от дня не меняется.
		if (!ThreatScale.reached(zombie.level(), ThreatTable.SORCERER_DAY)) {
			return false;
		}
		if (zombie.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		data.evolvingthreats$setSorcerer(true);
		equip(zombie);
		return true;
	}

	/** Сколько опыта дать. -1 = это не чародей, пусть решает игра. */
	public static int experienceReward(Zombie zombie) {
		if (!isSorcerer(zombie)) {
			return -1;
		}
		return Math.max(0, XP_REWARD);
	}

	/** Лишняя вещь при смерти. Мотыга сюда не входит. */
	public static void dropBonus(Zombie zombie, ServerLevel level, boolean killedByPlayer) {
		if (!isSorcerer(zombie) || !killedByPlayer || BONUS_DROP_CHANCE <= 0.0f) {
			return;
		}
		if (zombie.getRandom().nextFloat() >= BONUS_DROP_CHANCE) {
			return;
		}
		zombie.spawnAtLocation(level, rollBonus(zombie));
	}

	/** Конец тика цели. Следит за паузой и колдует, когда игрок в зоне и его видно. */
	public static void tick(Zombie zombie) {
		if (!(zombie instanceof ZombieSorcererEntity data) || !data.evolvingthreats$isSorcerer()) {
			return;
		}
		int cooldown = data.evolvingthreats$getSorcererCooldown();
		if (cooldown > 0) {
			data.evolvingthreats$setSorcererCooldown(cooldown - 1);
			return;
		}
		if (!(zombie.level() instanceof ServerLevel level)) {
			return;
		}
		if (!(zombie.getTarget() instanceof Player player) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		if (SPELL_RANGE_BLOCKS <= 0.0 || zombie.distanceTo(player) > SPELL_RANGE_BLOCKS) {
			return;
		}
		if (!zombie.hasLineOfSight(player)) {
			return;
		}
		cast(level, zombie, player);
		data.evolvingthreats$setSorcererCooldown(Math.max(0, SPELL_COOLDOWN_SECONDS) * 20);
	}

	/** Команда: чародей перед игроком. null — механика выключена. */
	public static @Nullable Zombie spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return null;
		}
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		if (zombie == null) {
			throw new IllegalStateException("Failed to create zombie sorcerer");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		zombie.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (zombie instanceof ZombieSorcererEntity data) {
			data.evolvingthreats$setSorcerer(true);
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

	private static boolean canBeSorcerer(Zombie zombie) {
		if (!ENABLED || !(zombie instanceof ZombieSorcererEntity)) {
			return false;
		}
		if (zombie.getType() != EntityTypes.ZOMBIE || zombie.isBaby()) {
			return false;
		}
		if (zombie instanceof ZombieOverseerEntity overseer && overseer.evolvingthreats$isOverseer()) {
			return false;
		}
		return !(zombie instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant());
	}

	private static void equip(Zombie zombie) {
		zombie.setCanPickUpLoot(false);
		ItemStack hoe = new ItemStack(Items.NETHERITE_HOE);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, hoe);
		zombie.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		zombie.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		zombie.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
		for (int i = 0; i < ARMOR_SLOTS.length; i++) {
			zombie.setItemSlot(ARMOR_SLOTS[i], blackLeather(LEATHER[i]));
			zombie.setDropChance(ARMOR_SLOTS[i], 0.0f);
		}
		var maxHealth = zombie.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			float health = Math.max(1.0f, HEALTH);
			maxHealth.setBaseValue(health);
			zombie.setHealth(health);
		}
	}

	private static void cast(ServerLevel level, Zombie zombie, Player player) {
		switch (pickSpell(zombie, player)) {
			case 0 -> launch(level, player, zombie);
			case 1 -> ignite(level, player);
			default -> pull(level, zombie, player);
		}
		level.playSound(
				null,
				zombie.getX(),
				zombie.getY(),
				zombie.getZ(),
				SoundEvents.EVOKER_CAST_SPELL,
				SoundSource.HOSTILE,
				1.0f,
				0.8f + zombie.getRandom().nextFloat() * 0.4f);
	}

	private static void ignite(ServerLevel level, Player player) {
		player.igniteForSeconds(Math.max(1, FIRE_SECONDS));
		level.sendParticles(
				ParticleTypes.FLAME,
				player.getX(),
				player.getY() + 1.0,
				player.getZ(),
				16,
				0.35,
				0.5,
				0.35,
				0.02);
	}

	/** 0 — подброс, 1 — огонь, 2 — рывок. Подброс выпадает чаще всех. */
	private static int pickSpell(Zombie zombie, Player player) {
		boolean allowPull = PULL_WEIGHT > 0 && zombie.distanceTo(player) >= PULL_MIN_BLOCKS;
		int launch = Math.max(0, LAUNCH_WEIGHT);
		int fire = Math.max(0, FIRE_WEIGHT);
		int pull = allowPull ? Math.max(0, PULL_WEIGHT) : 0;
		int total = launch + fire + pull;
		if (total <= 0) {
			return 0;
		}
		int roll = zombie.getRandom().nextInt(total);
		if (roll < launch) {
			return 0;
		}
		roll -= launch;
		if (roll < fire) {
			return 1;
		}
		return 2;
	}

	private static void launch(ServerLevel level, Player player, Zombie zombie) {
		int blocks = launchBlocks(zombie);
		double vy = velocityForHeight(blocks);
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(motion.x, vy, motion.z);
		player.resetFallDistance();
		player.syncVelocity = true;
		level.sendParticles(
				ParticleTypes.WITCH,
				player.getX(),
				player.getY(),
				player.getZ(),
				20,
				0.3,
				0.2,
				0.3,
				0.05);
	}

	private static void pull(ServerLevel level, Zombie zombie, Player player) {
		Vec3 toward = new Vec3(zombie.getX() - player.getX(), 0.0, zombie.getZ() - player.getZ());
		double len = toward.length();
		if (len < 1.0E-4 || PULL_SPEED <= 0.0) {
			return;
		}
		Vec3 vel = toward.scale(PULL_SPEED / len);
		player.setDeltaMovement(vel.x, Math.max(0.2, player.getDeltaMovement().y), vel.z);
		player.syncVelocity = true;
		level.sendParticles(
				ParticleTypes.WITCH,
				player.getX(),
				player.getY() + 1.0,
				player.getZ(),
				14,
				0.4,
				0.4,
				0.4,
				0.0);
	}

	/** Обычно 25–30 блоков поровну. Редко — ровно {@link #LAUNCH_RARE_BLOCKS}. */
	private static int launchBlocks(Zombie zombie) {
		if (LAUNCH_RARE_CHANCE > 0.0f && zombie.getRandom().nextFloat() < LAUNCH_RARE_CHANCE) {
			return Math.max(0, LAUNCH_RARE_BLOCKS);
		}
		int min = Math.min(LAUNCH_MIN_BLOCKS, LAUNCH_MAX_BLOCKS);
		int max = Math.max(LAUNCH_MIN_BLOCKS, LAUNCH_MAX_BLOCKS);
		return min + zombie.getRandom().nextInt(max - min + 1);
	}

	/**
	 * Скорость вверх, чтобы без зелий долететь примерно до нужной высоты.
	 * Игра сначала двигает игрока, потом уменьшает скорость: минус 0.08 и умножить на 0.98.
	 */
	private static double velocityForHeight(double blocks) {
		if (blocks <= 0.0) {
			return 0.0;
		}
		double low = 0.0;
		double high = 4.0;
		for (int i = 0; i < 24; i++) {
			double mid = (low + high) * 0.5;
			if (peakHeight(mid) < blocks) {
				low = mid;
			} else {
				high = mid;
			}
		}
		return (low + high) * 0.5;
	}

	private static double peakHeight(double vy) {
		double y = 0.0;
		for (int i = 0; i < 200 && vy > 0.0; i++) {
			y += vy;
			vy = (vy - GRAVITY_PER_TICK) * VERTICAL_DRAG;
		}
		return y;
	}

	private static ItemStack rollBonus(Zombie zombie) {
		int roll = zombie.getRandom().nextInt(BONUS_ITEMS.length + 1);
		if (roll < BONUS_ITEMS.length) {
			return new ItemStack(BONUS_ITEMS[roll]);
		}
		return blackLeather(LEATHER[zombie.getRandom().nextInt(LEATHER.length)]);
	}

	private static ItemStack blackLeather(Item item) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(DyeColor.BLACK.getFireworkColor()));
		return stack;
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
