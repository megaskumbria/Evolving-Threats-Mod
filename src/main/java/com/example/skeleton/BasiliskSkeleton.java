package com.example.skeleton;

import com.example.MobNames;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;

import com.example.entity.BasiliskSkeletonEntity;
import com.example.entity.RaidParticipantEntity;
import com.example.mixin.RangedBowAttackGoalAccessor;
import com.example.siege.SiegeNight;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Редкий скелет Basilisk. Игра его не добавляет: это обычный скелет в незерите
 * с луком и чёрным щитом. Лук уносит игрока очень далеко и поджигает.
 * Лук, щит и броня не падают. С него всегда падает бирка с его именем.
 */
public final class BasiliskSkeleton {

	// ===================== SETTINGS (меняй эти числа) =====================

	/** false — новые Василиски не появляются, команда тоже не спавнит. Уже живые остаются как есть. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный скелет при появлении станет Василиском.
	 * 0.002 = 0.2%, реже капитана (у того 0.6%). Команда этот шанс не бросает.
	 * Спаунер скелетов тоже бросает этот шанс. Призванная подмога и /summon — нет.
	 */
	public static final float SPAWN_CHANCE = 0.002f;

	/** Имя над головой и текст на бирке. По-английски, как у капитана. */
	public static final String NAME = "Basilisk";

	/**
	 * Здоровье. У обычного скелета 20 (10 сердец), случайный бросок мода (20–40) ему не оставляем:
	 * это число ставится после того броска и всегда одно и то же.
	 * 100 = 50 сердец. Меньше 1 игра почти сразу убивает.
	 */
	public static final float MAX_HEALTH = 100.0f;

	/**
	 * Сколько опыта падает при смерти. 100. Обычный скелет даёт 5.
	 * 0 = опыта нет. Это число вместо обычного, а не сверху.
	 */
	public static final int XP_REWARD = 100;

	/**
	 * Сколько бирок с именем Basilisk падает всегда. 1 = одна.
	 * 0 = бирку не ронять. На лук и броню это не влияет: они не падают.
	 */
	public static final int NAME_TAG_COUNT = 1;

	/**
	 * Шанс (0.0–1.0) дополнительно выронить череп скелета.
	 * 0.025 = 2.5%, как череп скелета-иссушителя. 0 = черепа нет. 1 = падает всегда.
	 */
	public static final float SKULL_DROP_CHANCE = 0.025f;

	/** Горящая стрела. 1 = есть. 0 = стрела не поджигает. */
	public static final int BOW_FLAME_LEVEL = 1;

	/** Сила на луке. 5 = V, ванильный потолок. */
	public static final int BOW_POWER_LEVEL = 5;

	/**
	 * Откидывание на луке. У ванильного лука потолок II, у капитана IV, у редкого лука мода VI.
	 * 20 = XX. Игрока уносит намного дальше, чем Откидывание VI.
	 */
	public static final int BOW_PUNCH_LEVEL = 20;

	/**
	 * Как далеко замечает игрока и не теряет его, в блоках.
	 * У обычного скелета около 16. 64 = в четыре раза дальше: видит через всё поле, если нет стены.
	 * Стену это не пробивает. Капитан ищет сквозь стены на 128, здесь только прямая видимость.
	 */
	public static final double SIGHT_BLOCKS = 64.0;

	/**
	 * На каком расстоянии останавливается и стреляет, в блоках.
	 * У обычного скелета 15. 48 = начинает обстрел издалека, не подходит вплотную.
	 * Должно быть не больше {@link #SIGHT_BLOCKS}, иначе он не видит точку, с которой мог бы стрелять.
	 */
	public static final float ATTACK_RANGE_BLOCKS = 48.0f;

	/**
	 * Сколько попаданий в щит нужно, чтобы он разлетелся целиком.
	 * 4 = четвёртая закрытая стрела Василиска ломает щит.
	 * «Прочность» на щите это число не растягивает: считается каждое попадание, которое щит отбил.
	 * Промах и удар мимо щита не считаются. 0 = щит не ломает. 1 = с первого попадания.
	 */
	public static final int SHIELD_HITS_TO_BREAK = 4;

	/**
	 * Скорость стрелы. У обычного скелета 1.6, у дальнего быстрого лука 2.4.
	 * 3.2 = чтобы стрела долетала до дальней дистанции, а не падала по пути.
	 * Прицел подстраивается сам: быстрее стрела — меньше задирается вверх.
	 */
	public static final float ARROW_SPEED = 3.2f;

	/** Скорость стрелы у обычного скелета. От неё считается прицел. */
	private static final float VANILLA_ARROW_SPEED = 1.6f;

	/** Насколько обычный скелет задирает стрелу вверх. */
	private static final float VANILLA_ARC = 0.2f;

	// ========================================================================

	/** Сколько стрел этот щит уже отбил. Лежит на самом щите, не на игроке. */
	private static final String SHIELD_HITS_KEY = "EvolvingThreatsBasiliskShieldHits";

	private static final EquipmentSlot[] ARMOR = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private BasiliskSkeleton() {
	}

	public static boolean isBasilisk(Mob mob) {
		return mob instanceof BasiliskSkeletonEntity data && data.evolvingthreats$isBasilisk();
	}

	/**
	 * Насколько задирать стрелу. Считается из {@link #ARROW_SPEED}.
	 * При 1.6 получается 0.2, как у обычного скелета.
	 */
	public static float arrowArc() {
		if (ARROW_SPEED <= 0.01f) {
			return VANILLA_ARC;
		}
		return (float) (VANILLA_ARC * Math.pow(VANILLA_ARROW_SPEED / ARROW_SPEED, 1.25));
	}

	/** true = это Василиск, обычный набор мода ему не нужен. */
	public static boolean tryClaim(AbstractSkeleton skeleton, EntitySpawnReason reason) {
		if (!ENABLED || !(skeleton instanceof Skeleton) || !(skeleton instanceof BasiliskSkeletonEntity data)) {
			return false;
		}
		if (data.evolvingthreats$isBasilisk()) {
			equip(skeleton);
			return true;
		}
		if (reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.MOB_SUMMONED) {
			return false;
		}
		// В ночь осады мини-боссы не появляются.
		if (SiegeNight.isWindowOpen(skeleton.level())) {
			return false;
		}
		if (skeleton instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant()) {
			return false;
		}
		// С какого дня Василиски вообще появляются (ThreatTable.BASILISK_DAY). Шанс ниже от дня не меняется.
		if (!ThreatScale.reached(skeleton.level(), ThreatTable.BASILISK_DAY)) {
			return false;
		}
		if (SPAWN_CHANCE <= 0.0f || skeleton.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		data.evolvingthreats$setBasilisk(true);
		equip(skeleton);
		return true;
	}

	/**
	 * Команда. Появляется уже одетым, шанс не бросается.
	 * false — выключен или скелета не удалось создать.
	 */
	public static boolean spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return false;
		}
		Skeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
		if (skeleton == null) {
			return false;
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		skeleton.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (skeleton instanceof BasiliskSkeletonEntity data) {
			data.evolvingthreats$setBasilisk(true);
		}
		skeleton.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(skeleton.blockPosition()),
				EntitySpawnReason.COMMAND,
				(SpawnGroupData) null);
		if (lookAt != null) {
			skeleton.setTarget(lookAt);
		}
		level.addFreshEntity(skeleton);
		return true;
	}

	/** Дальность взгляда и выстрела. Вызывать после того, как игра заново выбрала «стрелять из лука». */
	public static void applyBow(AbstractSkeleton skeleton, RangedBowAttackGoal<?> bowGoal) {
		if (!isBasilisk(skeleton)) {
			return;
		}
		setSight(skeleton);
		((RangedBowAttackGoalAccessor) bowGoal).evolvingthreats$setAttackRadiusSqr(ATTACK_RANGE_BLOCKS * ATTACK_RANGE_BLOCKS);
	}

	/** Сколько опыта дать. -1 = это не Василиск, пусть решает игра. */
	public static int experienceReward(Mob mob) {
		if (!isBasilisk(mob)) {
			return -1;
		}
		return Math.max(0, XP_REWARD);
	}

	/** Бирка с именем и редкий череп. Лук и незерит сюда не входят. */
	public static void dropBonus(Mob mob, ServerLevel level) {
		if (!isBasilisk(mob)) {
			return;
		}
		if (NAME_TAG_COUNT > 0) {
			ItemStack tag = new ItemStack(Items.NAME_TAG, NAME_TAG_COUNT);
			tag.set(DataComponents.CUSTOM_NAME, name());
			mob.spawnAtLocation(level, tag);
		}
		if (SKULL_DROP_CHANCE > 0.0f && mob.getRandom().nextFloat() < SKULL_DROP_CHANCE) {
			mob.spawnAtLocation(level, new ItemStack(Items.SKELETON_SKULL));
		}
	}

	/**
	 * Щит уже отбил эту стрелу. Четвёртая такая стрела уничтожает его целиком.
	 * Стрела другого скелета сюда не попадает.
	 */
	public static void onShieldBlock(Player player, DamageSource source) {
		if (SHIELD_HITS_TO_BREAK <= 0 || !(player.level() instanceof ServerLevel)) {
			return;
		}
		if (!(source.getDirectEntity() instanceof AbstractArrow arrow)) {
			return;
		}
		if (!(arrow.getOwner() instanceof Mob owner) || !isBasilisk(owner)) {
			return;
		}
		InteractionHand hand = player.getUsedItemHand();
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(Items.SHIELD)) {
			return;
		}
		int hits = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr(SHIELD_HITS_KEY, 0) + 1;
		if (hits >= SHIELD_HITS_TO_BREAK) {
			ItemStack broken = stack.copy();
			player.setItemInHand(hand, ItemStack.EMPTY);
			player.onEquippedItemBroken(broken, hand.asEquipmentSlot());
			player.stopUsingItem();
			return;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(SHIELD_HITS_KEY, hits));
	}

	/** Перед выпадением вещей. Иначе Добыча может уронить лук или незерит. */
	public static void preventDrop(Mob mob) {
		if (!isBasilisk(mob)) {
			return;
		}
		mob.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		mob.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
		for (EquipmentSlot slot : ARMOR) {
			mob.setDropChance(slot, 0.0f);
		}
	}

	private static void equip(AbstractSkeleton skeleton) {
		skeleton.setCustomName(name());
		MobNames.showOnlyWhenLookedAt(skeleton);
		skeleton.setCanPickUpLoot(false);
		skeleton.setPersistenceRequired();

		ItemStack bow = new ItemStack(Items.BOW);
		enchant(skeleton, bow, Enchantments.FLAME, BOW_FLAME_LEVEL);
		enchant(skeleton, bow, Enchantments.POWER, BOW_POWER_LEVEL);
		enchant(skeleton, bow, Enchantments.PUNCH, BOW_PUNCH_LEVEL);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow);
		// Чёрный щит без узора флага: только цвет основы. Другой цвет — DyeColor, например RED.
		// Удар по нему с шансом MobShields.BLOCK_CHANCE (сейчас 10%) гасится целиком, как у обычного щита мода.
		// Щит не падает: шанс выпадения второй руки ниже стоит в 0.
		ItemStack shield = new ItemStack(Items.SHIELD);
		shield.set(DataComponents.BASE_COLOR, DyeColor.BLACK);
		skeleton.setItemSlot(EquipmentSlot.OFFHAND, shield);

		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
		skeleton.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
		skeleton.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
		skeleton.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));

		skeleton.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		skeleton.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
		for (EquipmentSlot slot : ARMOR) {
			skeleton.setDropChance(slot, 0.0f);
		}
		var maxHealth = skeleton.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(MAX_HEALTH);
			skeleton.setHealth(MAX_HEALTH);
		}
		setSight(skeleton);
		skeleton.reassessWeaponGoal();
	}

	private static Component name() {
		return Component.literal(NAME).withStyle(Style.EMPTY.withItalic(false));
	}

	private static void setSight(AbstractSkeleton skeleton) {
		var follow = skeleton.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(SIGHT_BLOCKS);
		}
	}

	private static void enchant(Mob mob, ItemStack stack, ResourceKey<Enchantment> enchantment, int level) {
		if (level <= 0 || !(mob.level() instanceof ServerLevel)) {
			return;
		}
		var holder = mob.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment);
		EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(holder, level));
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
	}
}
