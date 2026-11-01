package com.example.skeleton;

import com.example.entity.FastBowSkeletonEntity;
import com.example.entity.RandomArrowSkeletonEntity;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Скелет со случайными стрелами. В руке обычный лук, без стрелы с эффектом.
 * Эффект выбирается в момент выстрела. В круге десять разных.
 * Один и тот же не повторяется, пока не вышли остальные.
 * Следующий круг не начинает с того эффекта, который только что выстрелил.
 */
public final class RandomArrowSkeleton {

	// ===================== SETTINGS (меняй эти числа) =====================

	/** Включить механику. false — такие скелеты не появляются. Уже живущие стреляют как обычно. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный скелет или странник будет стрелять этими эффектами.
	 * 0.01 = 1%, один из ста.
	 * FIXED: с первого дня, от шкалы угрозы и луны не растёт.
	 * Два меча лук убирают — такой скелет этот шанс не бросает. 0 = только команда.
	 */
	public static final float SPAWN_CHANCE = 0.01f;

	/**
	 * Поджог после попадания, в секундах. 5 = как огненная стрела в обычной игре.
	 * Огнестойкость урон снимает, как обычно. 1 = почти вспышка.
	 */
	public static final int FIRE_SECONDS = 5;

	/**
	 * Слепота после попадания, в секундах. Уровень I. 8 = почти без обзора.
	 * У вредной стрелы со слепотой тоже 8.
	 */
	public static final int BLINDNESS_SECONDS = 8;

	/**
	 * Яд после попадания, в секундах. Уровень I, не сильный яд.
	 * 5 = примерно как обычная стрела яда. 0 = яда нет, выстрел всё равно занимает очередь.
	 */
	public static final int POISON_SECONDS = 5;

	/**
	 * Голод после попадания, в секундах. Уровень I.
	 * 30 = полминуты. У вредной стрелы голод на минуты: здесь короче, выстрел повторяется по кругу.
	 */
	public static final int HUNGER_SECONDS = 30;

	/**
	 * Замедление после попадания, в секундах. Уровень I.
	 * 15 = заметно, но не на весь бой. 0 = выстрел всё равно занимает очередь.
	 */
	public static final int SLOWNESS_SECONDS = 15;

	/**
	 * Слабость после попадания, в секундах. Уровень I.
	 * 15 = удары слабее ненадолго. 0 = выстрел всё равно занимает очередь.
	 */
	public static final int WEAKNESS_SECONDS = 15;

	/**
	 * Иссушение после попадания, в секундах. Уровень I, не как у скелета-иссушителя на II.
	 * 7 = как у вредной стрелы с иссушением.
	 */
	public static final int WITHER_SECONDS = 7;

	/**
	 * Тошнота после попадания, в секундах. Уровень I: экран качает.
	 * 8 = коротко. У вредной стрелы тошнота на минуты, здесь выстрел возвращается по кругу.
	 */
	public static final int NAUSEA_SECONDS = 8;

	/**
	 * Левитация после попадания, в секундах. Уровень I.
	 * 8 = подъём примерно на 8 блоков, потом падение. Как у вредной стрелы.
	 */
	public static final int LEVITATION_SECONDS = 8;

	/**
	 * Мгновенный урон, уровень. 0 = уровень I, три сердца. 1 = уровень II, шесть сердец.
	 * Длительности у этого эффекта нет: бьёт сразу.
	 */
	public static final int HARM_LEVEL = 0;

	// ========================================================================

	private static final String FIRE = "f";
	private static final String BLIND = "b";
	private static final String POISON = "p";
	private static final String HUNGER = "h";
	private static final String SLOW = "s";
	private static final String WEAK = "w";
	private static final String WITHER = "i";
	private static final String NAUSEA = "n";
	private static final String LEVITATE = "l";
	private static final String HARM = "d";
	private static final String[] EFFECTS = {
			FIRE, BLIND, POISON, HUNGER, SLOW, WEAK, WITHER, NAUSEA, LEVITATE, HARM
	};

	/** Имя на огненной стреле, чтобы попадание ставило свой поджог, а не только ванильные 5 секунд. */
	public static final String FIRE_ARROW_NAME = "random_fire";

	private static final float VANILLA_ARROW_SPEED = 1.6f;
	private static final double VANILLA_ARC = 0.2;

	private RandomArrowSkeleton() {
	}

	public static boolean isActive(AbstractSkeleton skeleton) {
		return ENABLED
				&& skeleton instanceof RandomArrowSkeletonEntity data
				&& data.evolvingthreats$isRandomArrow();
	}

	/**
	 * true — этот скелет стреляет эффектами по кругу.
	 * Вредную стрелу в руку ему не кладём: иначе в руке торчала бы одна и та же.
	 */
	public static boolean tryRoll(AbstractSkeleton skeleton) {
		if (!ENABLED || !(skeleton instanceof RandomArrowSkeletonEntity data)) {
			return false;
		}
		if (!(skeleton.level() instanceof ServerLevel)) {
			return false;
		}
		// Команда ставит метку до появления. Шанс тогда не бросаем.
		if (data.evolvingthreats$isRandomArrow()) {
			return true;
		}
		if (SPAWN_CHANCE <= 0.0f) {
			return false;
		}
		if (skeleton.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		data.evolvingthreats$setRandomArrow(true);
		return true;
	}

	/** Если набор моба забрал лук, вернуть его: без лука круг эффектов не из чего стрелять. */
	public static void ensureBow(AbstractSkeleton skeleton) {
		if (!isActive(skeleton) || skeleton.isHolding(Items.BOW)) {
			return;
		}
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
	}

	/**
	 * Команда: скелет перед игроком, без случайного шанса.
	 * Броня, чары на луке и быстрый лук вешаются тем же кодом, что у обычного спавна.
	 */
	public static void spawn(ServerLevel level, Vec3 pos, LivingEntity lookAt) {
		if (!ENABLED) {
			return;
		}
		AbstractSkeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
		if (skeleton == null) {
			return;
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		skeleton.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (skeleton instanceof RandomArrowSkeletonEntity data) {
			data.evolvingthreats$setRandomArrow(true);
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

	/** Выпустить следующую стрелу круга. false — это не он или в руках нет лука. */
	public static boolean shoot(AbstractSkeleton skeleton, LivingEntity target, float power) {
		if (!isActive(skeleton) || !skeleton.isHolding(Items.BOW)) {
			return false;
		}
		if (!(skeleton.level() instanceof ServerLevel level)) {
			return false;
		}
		String effect = nextEffect(skeleton);
		ItemStack projectile = arrow(effect);
		ItemStack bow = skeleton.getMainHandItem().is(Items.BOW)
				? skeleton.getMainHandItem()
				: skeleton.getOffhandItem();
		AbstractArrow arrow = ProjectileUtil.getMobArrow(skeleton, projectile, power, bow);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		if (FIRE.equals(effect)) {
			// В полёте стрела горит, как с чарами Огонь. Сколько горит игрок — в попадании.
			arrow.igniteForSeconds(100.0f);
		}
		double xd = target.getX() - skeleton.getX();
		double yd = target.getY(0.3333333333333333) - arrow.getY();
		double zd = target.getZ() - skeleton.getZ();
		double distance = Math.sqrt(xd * xd + zd * zd);
		float speed = VANILLA_ARROW_SPEED;
		double arc = VANILLA_ARC;
		// Быстрый лук — та же надбавка, что у остальных скелетов с этой меткой.
		if (skeleton instanceof FastBowSkeletonEntity fast && fast.evolvingthreats$isFastBow()) {
			speed = FastBowSkeleton.ARROW_SPEED;
			arc = FastBowSkeleton.arrowArc();
		}
		float uncertainty = ((RangedAttackMob) skeleton).rangedAttackUncertainty(level);
		Projectile.spawnProjectileUsingShoot(arrow, level, projectile, xd, yd + distance * arc, zd, speed, uncertainty);
		skeleton.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (skeleton.getRandom().nextFloat() * 0.4F + 0.8F));
		return true;
	}

	/** Поджог своей длительности. Ваниль после горящей стрелы ставит 5 секунд, здесь число из настроек. */
	public static void onFireHit(AbstractArrow arrow, Entity hit) {
		if (!(hit instanceof LivingEntity living) || FIRE_SECONDS <= 0) {
			return;
		}
		PotionContents contents = arrow.getPickupItemStackOrigin().get(DataComponents.POTION_CONTENTS);
		if (contents == null || contents.customName().isEmpty() || !FIRE_ARROW_NAME.equals(contents.customName().get())) {
			return;
		}
		living.igniteForSeconds(FIRE_SECONDS);
	}

	private static String nextEffect(AbstractSkeleton skeleton) {
		RandomArrowSkeletonEntity data = (RandomArrowSkeletonEntity) skeleton;
		String queue = data.evolvingthreats$getArrowQueue();
		if (queue == null || queue.isEmpty()) {
			queue = shuffle(skeleton, data.evolvingthreats$getLastArrow());
		}
		String effect = queue.substring(0, 1);
		data.evolvingthreats$setArrowQueue(queue.substring(1));
		data.evolvingthreats$setLastArrow(effect);
		return effect;
	}

	/** Все эффекты круга в случайном порядке. Первый не равен прошлому выстрелу. */
	private static String shuffle(AbstractSkeleton skeleton, String last) {
		List<String> bag = new ArrayList<>(List.of(EFFECTS));
		var random = skeleton.getRandom();
		for (int i = bag.size() - 1; i > 0; i--) {
			int swap = random.nextInt(i + 1);
			String tmp = bag.get(i);
			bag.set(i, bag.get(swap));
			bag.set(swap, tmp);
		}
		if (last != null && !last.isEmpty() && last.equals(bag.get(0)) && bag.size() > 1) {
			int swap = 1 + random.nextInt(bag.size() - 1);
			String tmp = bag.get(0);
			bag.set(0, bag.get(swap));
			bag.set(swap, tmp);
		}
		StringBuilder joined = new StringBuilder();
		for (String effect : bag) {
			joined.append(effect);
		}
		return joined.toString();
	}

	private static ItemStack arrow(String effect) {
		if (FIRE.equals(effect)) {
			return namedArrow(FIRE_ARROW_NAME, 0xFF6A00, List.of());
		}
		if (HARM.equals(effect)) {
			// Уровень I или II. У ванильной стрелы урона своя длительность не нужна.
			ItemStack harm = PotionContents.createItemStack(
					Items.TIPPED_ARROW,
					HARM_LEVEL >= 1 ? Potions.STRONG_HARMING : Potions.HARMING);
			return harm;
		}
		if (BLIND.equals(effect)) {
			return effectArrow(MobEffects.BLINDNESS, BLINDNESS_SECONDS, "random_blind");
		}
		if (POISON.equals(effect)) {
			return effectArrow(MobEffects.POISON, POISON_SECONDS, "random_poison");
		}
		if (HUNGER.equals(effect)) {
			return effectArrow(MobEffects.HUNGER, HUNGER_SECONDS, "random_hunger");
		}
		if (SLOW.equals(effect)) {
			return effectArrow(MobEffects.SLOWNESS, SLOWNESS_SECONDS, "random_slow");
		}
		if (WEAK.equals(effect)) {
			return effectArrow(MobEffects.WEAKNESS, WEAKNESS_SECONDS, "random_weak");
		}
		if (WITHER.equals(effect)) {
			return effectArrow(MobEffects.WITHER, WITHER_SECONDS, "random_wither");
		}
		if (NAUSEA.equals(effect)) {
			return effectArrow(MobEffects.NAUSEA, NAUSEA_SECONDS, "random_nausea");
		}
		return effectArrow(MobEffects.LEVITATION, LEVITATION_SECONDS, "random_levitate");
	}

	private static ItemStack effectArrow(Holder<MobEffect> effect, int durationSeconds, String name) {
		return effectArrow(
				new MobEffectInstance(effect, seconds(durationSeconds)),
				effect.value().getColor(),
				name);
	}

	private static ItemStack effectArrow(MobEffectInstance effect, int color, String name) {
		return namedArrow(name, color, List.of(effect));
	}

	private static ItemStack namedArrow(String name, int color, List<MobEffectInstance> effects) {
		ItemStack arrow = new ItemStack(Items.TIPPED_ARROW);
		arrow.set(DataComponents.POTION_CONTENTS, new PotionContents(
				Optional.empty(),
				Optional.of(color),
				effects,
				Optional.of(name)));
		// 1 = длительность в эффекте уже в тиках, игра её не укорачивает.
		arrow.set(DataComponents.POTION_DURATION_SCALE, 1.0F);
		return arrow;
	}

	private static int seconds(int value) {
		return Math.max(1, value) * 20;
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
