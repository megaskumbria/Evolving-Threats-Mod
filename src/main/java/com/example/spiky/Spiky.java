package com.example.spiky;

import com.example.EvolvingThreats;
import com.example.MobNames;
import com.example.RandomArmor;
import com.example.entity.SpikyEntity;
import com.example.siege.SiegeNight;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Редкий обычный зомби или скелет по имени Spiky.
 * Игра его не добавляет: это тот же моб, только один кусок брони колет в ответ.
 * Урон задан здесь, а не уровнем чар «Шипы»: в этой версии игры уровень Шипов урон не поднимает.
 */
public final class Spiky {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — новые Spiky не появляются, команда тоже не спавнит. Уже живые остаются как есть. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный зомби или скелет при появлении станет Spiky.
	 * 0.002 = 0.2%. Бросается отдельно для зомби и для скелета.
	 * До дня ThreatTable.SPIKY_DAY (40) шанса нет. С этого дня число не растёт и луна его не меняет.
	 * Команда этот шанс не бросает. Спаунер бросает. Призванная подмога и /summon — нет.
	 */
	public static final float SPAWN_CHANCE = 0.002f;

	/** Имя над головой. По-английски, как у остальных именных мобов. */
	public static final String NAME = "Spiky";

	/**
	 * Сколько сердец снимает укол, от и до. Оба края входят: 7 и 8 тоже выпадают.
	 * 1 сердце = 2 единицы урона. 7 сердец = 14, 8 сердец = 16.
	 * Броня, Защита и щит это число не режут. Жёлтые сердца от золотого яблока съедаются первыми.
	 * Зелье «Сопротивление урону» всё ещё ослабляет удар.
	 */
	public static final int HEARTS_MIN = 7;
	public static final int HEARTS_MAX = 8;

	/**
	 * Какой уровень «Шипов» написан на колючем куске. 3 = Шипы III, как потолок обычной игры.
	 * На силу укола НЕ влияет: сердца задают HEARTS_MIN и HEARTS_MAX.
	 * 0 = на предмете чары не написаны, колоть всё равно будет.
	 * Если игрок снимет эту броню и наденет сам, она колет уже как обычные Шипы игры, слабо.
	 */
	public static final int THORNS_LEVEL = 3;

	// ========================================================================

	/** Урон укола. Стоит в bypasses_armor и bypasses_enchantments, поэтому броня и Защита его не режут. */
	private static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(
			Registries.DAMAGE_TYPE, EvolvingThreats.id("spiky"));

	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
	};

	/** Чтобы укол, который сам ранит игрока, не вызвал второй укол. */
	private static boolean spiking;

	private Spiky() {
	}

	public static boolean isSpiky(Mob mob) {
		return mob instanceof SpikyEntity data && data.evolvingthreats$isSpiky();
	}

	/**
	 * Обычные Шипы игры с брони Spiky. Их надо погасить: иначе к 7–8 сердцам
	 * прибавится ещё слабый укол 1–5 с каждого куска.
	 */
	public static boolean isVanillaThorns(DamageSource source) {
		if (!source.is(DamageTypes.THORNS) || !(source.getEntity() instanceof Mob mob)) {
			return false;
		}
		return isSpiky(mob);
	}

	/**
	 * Бросить шанс. true = это Spiky.
	 * Кусок брони с шипами надевает {@link #apply}, уже после обычной брони.
	 */
	public static boolean tryRoll(Mob mob, EntitySpawnReason reason) {
		if (isSpiky(mob)) {
			return true;
		}
		if (!canRoll(mob, reason)) {
			return false;
		}
		if (SPAWN_CHANCE <= 0.0f || mob.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		if (mob instanceof SpikyEntity data) {
			data.evolvingthreats$setSpiky(true);
		}
		return true;
	}

	/**
	 * Команда. Появляется уже Spiky, шанс и день не смотрим.
	 * skeleton = false — зомби, true — скелет.
	 * false — выключен или моба не удалось создать.
	 */
	public static boolean spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt, boolean skeleton) {
		if (!ENABLED) {
			return false;
		}
		Mob mob = skeleton
				? EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND)
				: EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		if (mob == null) {
			return false;
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		mob.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (mob instanceof SpikyEntity data) {
			data.evolvingthreats$setSpiky(true);
		}
		mob.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(mob.blockPosition()),
				EntitySpawnReason.COMMAND,
				(SpawnGroupData) null);
		if (lookAt != null) {
			mob.setTarget(lookAt);
		}
		level.addFreshEntity(mob);
		return true;
	}

	/** Имя и один колючий кусок брони. Остальная броня уже выдана как обычно. */
	public static void apply(Mob mob) {
		if (!isSpiky(mob)) {
			return;
		}
		mob.setCustomName(Component.literal(NAME).withStyle(Style.EMPTY.withItalic(false)));
		MobNames.showOnlyWhenLookedAt(mob);
		ensureSpikePiece(mob);
	}

	/** После загрузки мира. Броню не перебрасывает: какой кусок надели, тот и остался. */
	public static void refresh(Mob mob) {
		if (!isSpiky(mob)) {
			return;
		}
		MobNames.showOnlyWhenLookedAt(mob);
	}

	/**
	 * Удар игрока уже дошёл до Spiky. Один укол в ответ, не с каждого куска брони.
	 * Рука, меч, стрела и трезубец игрока колют. Огонь, взрыв и чужие Шипы — нет.
	 */
	public static void onHurt(LivingEntity mob, ServerLevel level, DamageSource source, boolean landed) {
		if (!landed || spiking || !(mob instanceof Mob spiky) || !isSpiky(spiky)) {
			return;
		}
		if (!(source.getEntity() instanceof Player player) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		if (source.is(DamageTypes.THORNS)
				|| source.is(DamageTypeTags.IS_FIRE)
				|| source.is(DamageTypeTags.IS_EXPLOSION)
				|| source.is(DAMAGE_TYPE)) {
			return;
		}
		float damage = hearts(spiky.getRandom()) * 2.0f;
		if (damage <= 0.0f) {
			return;
		}
		Holder<DamageType> type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE_TYPE);
		spiking = true;
		try {
			player.hurtServer(level, new DamageSource(type, spiky), damage);
		} finally {
			spiking = false;
		}
	}

	/** Случайное число сердец от HEARTS_MIN до HEARTS_MAX. Оба края входят. */
	private static int hearts(RandomSource random) {
		int min = Math.min(HEARTS_MIN, HEARTS_MAX);
		int max = Math.max(HEARTS_MIN, HEARTS_MAX);
		if (max <= 0) {
			return 0;
		}
		if (min < 0) {
			min = 0;
		}
		return min + random.nextInt(max - min + 1);
	}

	private static void ensureSpikePiece(Mob mob) {
		RandomSource random = mob.getRandom();
		List<EquipmentSlot> armored = new ArrayList<>();
		List<EquipmentSlot> empty = new ArrayList<>();
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack stack = mob.getItemBySlot(slot);
			if (RandomArmor.isModArmor(stack)) {
				armored.add(slot);
			} else if (stack.isEmpty()) {
				empty.add(slot);
			}
		}
		EquipmentSlot chosen;
		if (!armored.isEmpty()) {
			chosen = armored.get(random.nextInt(armored.size()));
		} else if (!empty.isEmpty()) {
			chosen = empty.get(random.nextInt(empty.size()));
			RandomArmor.equipRandomPiece(mob, chosen);
		} else {
			// На голове тыква или спавнер, а слоты брони заняты не бронёй. Всё равно нужен один кусок.
			chosen = ARMOR_SLOTS[random.nextInt(ARMOR_SLOTS.length)];
			RandomArmor.equipRandomPiece(mob, chosen);
		}
		addThorns(mob, chosen);
	}

	/** Дописывает Шипы на кусок, остальные чары на нём не стирает. */
	private static void addThorns(Mob mob, EquipmentSlot slot) {
		if (THORNS_LEVEL <= 0) {
			return;
		}
		ItemStack stack = mob.getItemBySlot(slot);
		if (!RandomArmor.isModArmor(stack)) {
			return;
		}
		Holder<Enchantment> thorns = mob.level().registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.THORNS);
		EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(thorns, THORNS_LEVEL));
		mob.setItemSlot(slot, stack);
	}

	private static boolean canRoll(Mob mob, EntitySpawnReason reason) {
		if (!ENABLED || !(mob instanceof SpikyEntity) || !(mob.level() instanceof ServerLevel)) {
			return false;
		}
		if (!(mob instanceof Zombie) && !(mob instanceof Skeleton)) {
			return false;
		}
		if (mob.getType() != EntityTypes.ZOMBIE && mob.getType() != EntityTypes.SKELETON) {
			return false;
		}
		if (mob.isBaby()) {
			return false;
		}
		if (reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.MOB_SUMMONED) {
			return false;
		}
		if (SiegeNight.isWindowOpen(mob.level())) {
			return false;
		}
		return ThreatScale.reached(mob.level(), ThreatTable.SPIKY_DAY);
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
	}
}
