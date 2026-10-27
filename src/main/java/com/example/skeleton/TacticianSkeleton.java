package com.example.skeleton;

import com.example.threat.ThreatScale;

import com.example.MobEnchantments;
import com.example.RandomArmor;
import com.example.entity.TacticianSkeletonEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Скелет «700 IQ». Особый тип, не смесь остальных.
 * Стреляет одной и той же стрелой. Попал — достаёт меч и бежит.
 * Когда эффекты с игрока спадают, снова берёт лук.
 * Меч, который сейчас не в руке, виден на спине.
 */
public final class TacticianSkeleton {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — такие скелеты не появляются. Уже живущие остаются как есть. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный скелет или странник будет этим типом.
	 * 0.05 = 5% на 100-й день (РАСТЁТ: в начале игры в 10 раз меньше, см. ThreatTable.DANGEROUS_START_RATIO).
	 * Реже маленького и дальнего: этот сильнее в бою.
	 * 0 = таких скелетов не будет.
	 */
	public static final float SPAWN_CHANCE = 0.05f;

	/**
	 * Здоровье, оба края входят. У обычного скелета в этом моде 20–40.
	 * 20 и 35 = от 10 до 17.5 сердец.
	 */
	public static final int HEALTH_MIN = 20;
	public static final int HEALTH_MAX = 35;

	/** Слабость на игроке после попадания, в секундах. Уровень I. */
	public static final int WEAKNESS_SECONDS = 20;

	/** Замедление после попадания, в секундах. Уровень I. */
	public static final int SLOWNESS_SECONDS = 20;

	/** Слепота после попадания, в секундах. Короче остальных: почти без обзора, но не на весь бой. */
	public static final int BLINDNESS_SECONDS = 5;

	/**
	 * Мечи за спиной: дерево, камень, золото, медь, железо, алмаз. Незерита нет.
	 * Веса такие же, как у случайного оружия мобов. Сумма 100: дерево 30% … алмаз 10%.
	 */
	private static final Item[] SWORDS = {
			Items.WOODEN_SWORD,
			Items.STONE_SWORD,
			Items.GOLDEN_SWORD,
			Items.COPPER_SWORD,
			Items.IRON_SWORD,
			Items.DIAMOND_SWORD,
	};

	private static final int[] SWORD_WEIGHTS = { 30, 25, 10, 10, 15, 10 };

	// ========================================================================

	/** Имя на стреле, чтобы отличить её от любой другой стрелы со зельем. */
	public static final String ARROW_NAME = "tactician";

	/**
	 * Слот «предмет на спине». У обычного скелета пустой, у этого — лук или меч.
	 * Номер слота занимается в {@code TacticianSkeletonDataMixin} сразу при загрузке скелета.
	 * Не переноси это поле в метод, который вызывается при спавне: номер совпадёт
	 * с номером самого скелета, и мир упадёт.
	 */
	public static final EntityDataAccessor<ItemStack> BACK_ITEM = SynchedEntityData.defineId(
			AbstractSkeleton.class, EntityDataSerializers.ITEM_STACK);

	private TacticianSkeleton() {
	}

	/**
	 * true — этот скелет стал «700 IQ». Дальше ему не нужны маленький рост, тотем,
	 * два оружия, вредная стрела, щит, броня и дальний лук.
	 */
	public static boolean tryRoll(AbstractSkeleton skeleton) {
		if (!ENABLED || SPAWN_CHANCE <= 0.0f || !(skeleton instanceof TacticianSkeletonEntity data)) {
			return false;
		}
		if (!(skeleton.level() instanceof ServerLevel)) {
			return false;
		}
		// Команда ставит метку до появления. Тогда шанс не бросаем — сразу выдаём лук и меч.
		if (data.evolvingthreats$isTactician()) {
			equip(skeleton, data);
			return true;
		}
		if (skeleton.getRandom().nextFloat() >= ThreatScale.dangerousChance(skeleton.level(), SPAWN_CHANCE)) {
			return false;
		}
		equip(skeleton, data);
		return true;
	}

	/**
	 * Команда: этот скелет перед игроком, без случайного шанса.
	 * Смотрит на того, кто позвал, и сразу с луком. Меч на спине.
	 */
	public static void spawn(ServerLevel level, Vec3 pos, LivingEntity lookAt) {
		AbstractSkeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
		if (skeleton == null) {
			return;
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		skeleton.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (skeleton instanceof TacticianSkeletonEntity data) {
			data.evolvingthreats$setTactician(true);
		}
		skeleton.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(skeleton.blockPosition()),
				EntitySpawnReason.COMMAND,
				null);
		if (lookAt != null) {
			skeleton.setTarget(lookAt);
		}
		level.addFreshEntity(skeleton);
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}

	/** Попал нашей стрелой в игрока. Меч достанется в ближайший тик, не посреди выстрела. */
	public static void onArrowHit(AbstractSkeleton skeleton, Player player) {
		if (!(skeleton instanceof TacticianSkeletonEntity data) || !data.evolvingthreats$isTactician()) {
			return;
		}
		if (!player.isAlive()) {
			return;
		}
		data.evolvingthreats$setMarkedPlayer(player.getUUID());
		data.evolvingthreats$setMeleeUntil(skeleton.level().getGameTime() + meleeTicks());
	}

	/** Каждый тик на сервере: меч, пока эффекты на игроке, иначе лук. */
	public static void serverTick(AbstractSkeleton skeleton) {
		if (!(skeleton instanceof TacticianSkeletonEntity data) || !data.evolvingthreats$isTactician()) {
			return;
		}
		if (!(skeleton.level() instanceof ServerLevel level)) {
			return;
		}
		Player player = markedPlayer(level, data);
		boolean chasing = player != null && level.getGameTime() < data.evolvingthreats$getMeleeUntil() && hasAnyDebuff(player);
		boolean holdingBow = skeleton.getMainHandItem().is(Items.BOW);
		if (chasing) {
			if (holdingBow) {
				swapToMelee(skeleton, data);
			}
			if (skeleton.getTarget() != player) {
				skeleton.setTarget(player);
			}
			return;
		}
		if (!holdingBow) {
			swapToRanged(skeleton, data);
		}
	}

	/**
	 * true — цель ближнего боя не выключать, даже если игрок за углом.
	 * Иначе после попадания скелет останавливается, как только путь оборвался.
	 */
	public static boolean isChasingMarked(LivingEntity mob) {
		if (!(mob instanceof TacticianSkeletonEntity data) || !data.evolvingthreats$isTactician()) {
			return false;
		}
		if (mob.getMainHandItem().is(Items.BOW)) {
			return false;
		}
		UUID marked = data.evolvingthreats$getMarkedPlayer();
		LivingEntity target = mob instanceof AbstractSkeleton skeleton ? skeleton.getTarget() : null;
		return marked != null && target != null && target.isAlive() && marked.equals(target.getUUID());
	}

	/** Выпустить особую стрелу вместо обычной. false — это не он или сейчас в руке меч. */
	public static boolean shoot(AbstractSkeleton skeleton, LivingEntity target, float power) {
		if (!(skeleton instanceof TacticianSkeletonEntity data) || !data.evolvingthreats$isTactician()) {
			return false;
		}
		if (!skeleton.getMainHandItem().is(Items.BOW) || !(skeleton.level() instanceof ServerLevel level)) {
			return false;
		}
		ItemStack projectile = arrow();
		ItemStack bow = skeleton.getMainHandItem();
		AbstractArrow arrow = ProjectileUtil.getMobArrow(skeleton, projectile, power, bow);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		double xd = target.getX() - skeleton.getX();
		double yd = target.getY(0.3333333333333333) - arrow.getY();
		double zd = target.getZ() - skeleton.getZ();
		double distance = Math.sqrt(xd * xd + zd * zd);
		float uncertainty = ((RangedAttackMob) skeleton).rangedAttackUncertainty(level);
		Projectile.spawnProjectileUsingShoot(arrow, level, projectile, xd, yd + distance * 0.2, zd, 1.6F, uncertainty);
		skeleton.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (skeleton.getRandom().nextFloat() * 0.4F + 0.8F));
		return true;
	}

	/** Меч или лук со спины падает с тем же шансом, что и броня. Предмет в руке роняет сама игра. */
	public static void dropBackItem(AbstractSkeleton skeleton, ServerLevel level) {
		if (!(skeleton instanceof TacticianSkeletonEntity data) || !data.evolvingthreats$isTactician()) {
			return;
		}
		ItemStack back = data.evolvingthreats$getBackItem();
		if (back.isEmpty() || skeleton.getRandom().nextFloat() >= RandomArmor.ARMOR_DROP_CHANCE) {
			return;
		}
		skeleton.spawnAtLocation(level, back.copy());
	}

	private static void equip(AbstractSkeleton skeleton, TacticianSkeletonEntity data) {
		data.evolvingthreats$setTactician(true);
		skeleton.setCanPickUpLoot(false);
		skeleton.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		skeleton.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
		skeleton.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
		skeleton.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		skeleton.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);

		// Сначала меч в руку, чтобы чары оружия повесились на него, а не на лук.
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(pickSword(skeleton.getRandom())));
		MobEnchantments.rollForSkeleton(skeleton);
		data.evolvingthreats$setBackItem(skeleton.getMainHandItem().copy());

		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setDropChance(EquipmentSlot.MAINHAND, RandomArmor.ARMOR_DROP_CHANCE);
		RandomArmor.rollHelmet(skeleton);
		MobEnchantments.rollArmorForSkeleton(skeleton);
		rollHealth(skeleton);
	}

	private static void swapToMelee(AbstractSkeleton skeleton, TacticianSkeletonEntity data) {
		ItemStack bow = skeleton.getMainHandItem().copy();
		ItemStack sword = data.evolvingthreats$getBackItem().copy();
		if (sword.isEmpty()) {
			return;
		}
		data.evolvingthreats$setBackItem(bow);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, sword);
		skeleton.setDropChance(EquipmentSlot.MAINHAND, RandomArmor.ARMOR_DROP_CHANCE);
	}

	private static void swapToRanged(AbstractSkeleton skeleton, TacticianSkeletonEntity data) {
		ItemStack sword = skeleton.getMainHandItem().copy();
		ItemStack bow = data.evolvingthreats$getBackItem().copy();
		if (!bow.is(Items.BOW)) {
			bow = new ItemStack(Items.BOW);
		}
		data.evolvingthreats$setBackItem(sword);
		data.evolvingthreats$setMarkedPlayer(null);
		data.evolvingthreats$setMeleeUntil(0L);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow);
		skeleton.setDropChance(EquipmentSlot.MAINHAND, RandomArmor.ARMOR_DROP_CHANCE);
	}

	private static Player markedPlayer(ServerLevel level, TacticianSkeletonEntity data) {
		UUID id = data.evolvingthreats$getMarkedPlayer();
		if (id == null) {
			return null;
		}
		Player player = level.getPlayerByUUID(id);
		return player != null && player.isAlive() ? player : null;
	}

	/** Пока на игроке осталось хотя бы одно из трёх — скелет с мечом. Молоко снимает все сразу. */
	private static boolean hasAnyDebuff(Player player) {
		return player.hasEffect(MobEffects.WEAKNESS)
				|| player.hasEffect(MobEffects.SLOWNESS)
				|| player.hasEffect(MobEffects.BLINDNESS);
	}

	/** Сколько тиков держать меч, если эффекты не сняли раньше. Это самое долгое из трёх. */
	private static int meleeTicks() {
		int seconds = Math.max(WEAKNESS_SECONDS, Math.max(SLOWNESS_SECONDS, BLINDNESS_SECONDS));
		return Math.max(1, seconds) * 20;
	}

	private static ItemStack arrow() {
		List<MobEffectInstance> effects = List.of(
				new MobEffectInstance(MobEffects.WEAKNESS, Math.max(1, WEAKNESS_SECONDS) * 20),
				new MobEffectInstance(MobEffects.SLOWNESS, Math.max(1, SLOWNESS_SECONDS) * 20),
				new MobEffectInstance(MobEffects.BLINDNESS, Math.max(1, BLINDNESS_SECONDS) * 20));
		ItemStack arrow = new ItemStack(Items.TIPPED_ARROW);
		arrow.set(DataComponents.POTION_CONTENTS, new PotionContents(
				Optional.empty(),
				Optional.empty(),
				effects,
				Optional.of(ARROW_NAME)));
		arrow.set(DataComponents.POTION_DURATION_SCALE, 1.0F);
		return arrow;
	}

	private static void rollHealth(AbstractSkeleton skeleton) {
		int low = Math.min(HEALTH_MIN, HEALTH_MAX);
		int high = Math.max(HEALTH_MIN, HEALTH_MAX);
		int health = low + skeleton.getRandom().nextInt(high - low + 1);
		var maxHealth = skeleton.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null) {
			return;
		}
		maxHealth.setBaseValue(health);
		skeleton.setHealth(health);
	}

	private static Item pickSword(RandomSource random) {
		int total = 0;
		for (int weight : SWORD_WEIGHTS) {
			if (weight > 0) {
				total += weight;
			}
		}
		if (total <= 0) {
			return Items.WOODEN_SWORD;
		}
		int roll = random.nextInt(total);
		for (int i = 0; i < SWORDS.length && i < SWORD_WEIGHTS.length; i++) {
			if (SWORD_WEIGHTS[i] <= 0) {
				continue;
			}
			roll -= SWORD_WEIGHTS[i];
			if (roll < 0) {
				return SWORDS[i];
			}
		}
		return SWORDS[0];
	}
}
