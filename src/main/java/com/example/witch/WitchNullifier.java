package com.example.witch;

import com.example.entity.NullifierWitchEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Редкая ведьма. Время от времени бросает белое взрывное зелье.
 * Попадание в брызги снимает с игрока все эффекты сразу: и полезные, и вредные.
 * Между такими бросками она кидает обычные зелья ведьмы (яд, замедление, урон).
 * Союзникам в рейде по-прежнему кидает лечение — обнуление только по игроку.
 */
public final class WitchNullifier {

	// ===================== SETTINGS (меняй эти числа) =====================

	/** Включить механику. false — такие ведьмы не появляются, и белое зелье ничего не снимает. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычная ведьма при появлении окажется обнуляющей. 0.15 = 15%.
	 * Ведьм в мире меньше, чем блейзов, поэтому шанс выше, чем у блейза-алхимика (4%).
	 * Хижина, рейд и спаунер бросают этот шанс. Команда спавна шанс не использует.
	 */
	public static final float SPAWN_CHANCE = 0.15f;

	/**
	 * Пауза между белыми зельями, секунды.
	 * 15 = следующее обнуление не раньше чем через 15 секунд.
	 * Обычная ведьма бросает примерно раз в 3 секунды, так что белым будет около каждого пятого броска.
	 * 0 = каждый бросок по игроку снимает эффекты.
	 * Первый бросок по игроку паузы не ждёт.
	 */
	public static final int THROW_COOLDOWN_SECONDS = 15;

	/**
	 * Цвет брызг, RGB. 0xF4F4F4 — почти белый, чтобы отличить от фиолетового яда и чёрного урона.
	 * Можно поставить любой цвет, на снятие эффектов это не влияет.
	 */
	public static final int POTION_COLOR = 0xF4F4F4;

	/**
	 * Насколько далеко по горизонтали достают брызги, в блоках. 4 — как у обычного взрывного зелья.
	 * Облако частиц рисует сама игра и всегда около 4 блоков. Больше 4 — эффект снимется там, где брызг уже не видно.
	 */
	public static final double SPLASH_RADIUS_BLOCKS = 4.0;

	/**
	 * Насколько брызги достают вверх и вниз, в блоках. 2 — как у обычного зелья.
	 */
	public static final double SPLASH_VERTICAL_BLOCKS = 2.0;

	// ========================================================================

	/**
	 * Метка внутри предмета зелья, чтобы отличить наш бросок от обычного.
	 * В игре эта строка не показывается: ведьма бутылку в руке не держит, снаряд белый из-за POTION_COLOR.
	 */
	private static final String POTION_MARKER = "evolvingthreats_nullify";

	private WitchNullifier() {
	}

	public static boolean isNullifier(Witch witch) {
		return ENABLED
				&& witch instanceof NullifierWitchEntity data
				&& data.evolvingthreats$isNullifier();
	}

	/** Конец тика ведьмы. Убавляет паузу до следующего белого зелья. На клиенте не считается. */
	public static void tickCooldown(Witch witch) {
		if (witch.level().isClientSide() || !(witch instanceof NullifierWitchEntity data) || !data.evolvingthreats$isNullifier()) {
			return;
		}
		int cooldown = data.evolvingthreats$getNullifyCooldown();
		if (cooldown > 0) {
			data.evolvingthreats$setNullifyCooldown(cooldown - 1);
		}
	}

	/**
	 * Начало броска. Если пауза прошла и цель — игрок, этот бросок станет белым зельем.
	 * Пока ведьма пьёт зелье сама, она ничего не кидает — флаг не ставится.
	 * Лечение союзника в рейде сюда не попадает: цель не игрок.
	 */
	public static void armThrow(Witch witch, LivingEntity target) {
		if (!(witch instanceof NullifierWitchEntity data)) {
			return;
		}
		data.evolvingthreats$setNullifyArmed(false);
		if (!isNullifier(witch) || witch.level().isClientSide() || witch.isDrinkingPotion()) {
			return;
		}
		if (!(target instanceof Player) || data.evolvingthreats$getNullifyCooldown() > 0) {
			return;
		}
		data.evolvingthreats$setNullifyArmed(true);
	}

	/**
	 * Ведьма только что создала взрывное зелье. true — подменить его на белое и начать паузу.
	 * Вызывать из конструктора снаряда, пока бросок ещё «вооружён».
	 */
	public static boolean consumeArmed(LivingEntity owner) {
		if (!(owner instanceof Witch witch) || !(witch instanceof NullifierWitchEntity data)) {
			return false;
		}
		if (!data.evolvingthreats$isNullifyArmed()) {
			return false;
		}
		data.evolvingthreats$setNullifyArmed(false);
		data.evolvingthreats$setNullifyCooldown(Math.max(0, THROW_COOLDOWN_SECONDS) * 20);
		return true;
	}

	/** Белое взрывное зелье без своих эффектов. Снятие делает попадание, не содержимое бутылки. */
	public static ItemStack potionStack() {
		ItemStack stack = new ItemStack(Items.SPLASH_POTION);
		stack.set(DataComponents.POTION_CONTENTS, new PotionContents(
				Optional.empty(),
				Optional.of(POTION_COLOR),
				List.of(),
				Optional.of(POTION_MARKER)));
		return stack;
	}

	/**
	 * Попадание взрывного зелья. Обычные зелья сюда тоже приходят и сразу выходят.
	 * Белое снимает все значки эффектов у игроков в брызгах: зелья, яблоко, маяк.
	 * Яд и иссушение тоже спадают. Щит взрывное зелье не ловит — как в обычной игре.
	 * Других мобов не трогает: зомби рядом свои эффекты сохраняют.
	 */
	public static void onSplash(AbstractThrownPotion potion, HitResult hit) {
		if (!(potion.level() instanceof ServerLevel level) || !ENABLED || !isNullifyPotion(potion.getItem())) {
			return;
		}
		double radius = Math.max(0.0, SPLASH_RADIUS_BLOCKS);
		AABB splashBox = potion.getBoundingBox().move(hit.getLocation().subtract(potion.position()));
		AABB search = splashBox.inflate(radius, Math.max(0.0, SPLASH_VERTICAL_BLOCKS), radius);
		float margin = ProjectileUtil.computeMargin(potion);
		double radiusSq = radius * radius;
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, search)) {
			if (!(entity instanceof Player) || !entity.isAffectedByPotions()) {
				continue;
			}
			double dist = splashBox.distanceToSqr(entity.getBoundingBox().inflate(margin));
			if (dist >= radiusSq) {
				continue;
			}
			if (!entity.removeAllEffects()) {
				continue;
			}
			level.playSound(
					null,
					entity.getX(),
					entity.getY(),
					entity.getZ(),
					SoundEvents.WITCH_DRINK,
					SoundSource.PLAYERS,
					0.6f,
					1.4f);
		}
	}

	/** Вызвать из finalizeSpawn. Командный спавн ставит флаг заранее и шанс не бросает. */
	public static void rollOnSpawn(Witch witch, EntitySpawnReason reason) {
		if (!ENABLED || !(witch instanceof NullifierWitchEntity data)) {
			return;
		}
		if (data.evolvingthreats$isNullifier() || reason == EntitySpawnReason.COMMAND) {
			return;
		}
		if (witch.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		data.evolvingthreats$setNullifier(true);
	}

	/** Команда: ведьма-обнулитель перед игроком. null — механика выключена. */
	public static @Nullable Witch spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return null;
		}
		Witch witch = EntityTypes.WITCH.create(level, EntitySpawnReason.COMMAND);
		if (witch == null) {
			throw new IllegalStateException("Failed to create nullifier witch");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		witch.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (witch instanceof NullifierWitchEntity data) {
			data.evolvingthreats$setNullifier(true);
		}
		witch.finalizeSpawn(level, level.getCurrentDifficultyAt(witch.blockPosition()), EntitySpawnReason.COMMAND, null);
		if (witch instanceof NullifierWitchEntity data) {
			data.evolvingthreats$setNullifier(true);
			data.evolvingthreats$setNullifyCooldown(0);
			data.evolvingthreats$setNullifyArmed(false);
		}
		if (lookAt != null) {
			witch.setTarget(lookAt);
		}
		level.addFreshEntity(witch);
		return witch;
	}

	private static boolean isNullifyPotion(ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents != null && POTION_MARKER.equals(contents.customName().orElse(""));
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
