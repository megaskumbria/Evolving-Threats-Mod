package com.example.ritual;

import com.example.entity.RaidParticipantEntity;
import com.example.entity.RitualMember;
import com.example.entity.ZombieOverseerEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Редкая стая: 1–2 зомби и 1–2 скелета в тёмно-зелёной коже.
 * Шлема нет. У всех одна и та же голова, она и решает поведение стаи.
 * В руке у кого-то красный редстоуновый факел, у кого-то кость.
 * Чары, случайное оружие и зелья мода им не выдаются — иначе стая перестаёт выглядеть одинаково.
 */
public final class RitualBand {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — стаи не появляются, команда тоже не спавнит. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный зомби или обычный скелет окажется началом стаи.
	 * 0.008 = 0,8%. Бросают и зомби, и скелеты, поэтому у смешанного спаунера стая чуть чаще.
	 * Призванные согруппники, кадавр, странник и команда /summon этот шанс не бросают.
	 */
	public static final float SPAWN_CHANCE = 0.008f;

	/**
	 * Опыт с каждого. 10 — вдвое больше обычного зомби и скелета (у тех 5).
	 * Падает, только если моба убил игрок: так устроена сама игра.
	 */
	public static final int XP_REWARD = 10;

	/**
	 * Цвет кожи, RGB. 0x1B4F2A — тёмно-зелёный, темнее обычного зелёного красителя.
	 * Таким же цветом падает кусок брони.
	 */
	public static final int LEATHER_COLOR = 0x1B4F2A;

	/**
	 * Шанс (0.0–1.0), что выпадет один надетый кусок кожи. 0.085 ≈ 8,5%, как обычная броня мода.
	 * Шлем не надет, вместо него голова. 0 = кожа не падает.
	 */
	public static final float LEATHER_DROP_CHANCE = 0.085f;

	/**
	 * Шанс, что выпадет красный факел из руки. 0.35 = 35%. Кость из руки не падает:
	 * скелет и так роняет кости.
	 */
	public static final float TORCH_DROP_CHANCE = 0.35f;

	/**
	 * Шанс, что выпадет голова крипера, пиглина или череп иссушителя. 1.0 = всегда.
	 * Голова игрока этим шансом не пользуется: вместо неё падает компас.
	 */
	public static final float HEAD_DROP_CHANCE = 1.0f;

	/** Сколько пороха с моба стаи крипера, включая зомби. 1 = одна кучка. */
	public static final int GUNPOWDER_COUNT = 1;

	/** Сколько золотых слитков с моба стаи пиглина. 1 = один слиток. */
	public static final int GOLD_INGOT_COUNT = 1;

	/** Сколько угля с моба стаи иссушителя. 1 = один уголь. */
	public static final int COAL_COUNT = 1;

	/** Сколько роз визера с моба стаи иссушителя. 1 = одна роза. */
	public static final int ROSE_COUNT = 1;

	/**
	 * Как далеко (блоки) стая с головой игрока чует тебя сквозь стены. 36 = между 30 и 40.
	 * Дальше идут только на того, кого видят. 0 = сквозь стены не чуют.
	 */
	public static final double SENSE_BLOCKS = 36.0;

	/**
	 * Сила хлопка при смерти стаи крипера. У обычного крипера 3.
	 * 1.5 = больно вплотную, блоки почти не ломает. Своих не ранит и не раскидывает.
	 * 0 = взрыва нет.
	 */
	public static final float EXPLOSION_POWER = 1.5f;

	/**
	 * Сколько секунд длится иссушение от удара или стрелы стаи иссушителя. 4 = четыре секунды.
	 * Уровень I, слабее долгого иссушения настоящего скелета-иссушителя.
	 */
	public static final int WITHER_SECONDS = 4;

	/**
	 * Пока на цели осталось больше стольких секунд иссушения, новый удар его не обновляет.
	 * 1 = при длительности 4 секунды повтор примерно раз в 3 секунды, а не каждый тычок.
	 */
	public static final int WITHER_REFRESH_IF_BELOW_SECONDS = 1;

	// ========================================================================

	public static final int HEAD_PLAYER = 1;
	public static final int HEAD_CREEPER = 2;
	public static final int HEAD_PIGLIN = 3;
	public static final int HEAD_WITHER = 4;

	private static final EquipmentSlot[] LEATHER_SLOTS = {
			EquipmentSlot.CHEST,
			EquipmentSlot.LEGS,
			EquipmentSlot.FEET
	};

	private RitualBand() {
	}

	public static boolean isRitual(Mob mob) {
		return ENABLED && mob instanceof RitualMember data && data.evolvingthreats$isRitual();
	}

	/**
	 * Конец появления зомби или скелета. true = это стая: случайное оружие, броня,
	 * чары, щит, тотем и особые головы мода ему не выдаём.
	 */
	public static boolean tryClaim(Mob mob, EntitySpawnReason reason) {
		if (!(mob instanceof RitualMember member)) {
			return false;
		}
		if (member.evolvingthreats$isRitual()) {
			equip(mob);
			return true;
		}
		if (!canStart(mob, reason)) {
			return false;
		}
		if (mob.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		if (!(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		begin(level, mob.position(), mob.getYRot(), randomHead(mob), mob, null);
		return true;
	}

	/** Сколько опыта дать. -1 = это не стая, пусть решает игра. */
	public static int experienceReward(Mob mob) {
		if (!isRitual(mob)) {
			return -1;
		}
		return Math.max(0, XP_REWARD);
	}

	/**
	 * Лишние вещи, если убил игрок. Голова на слоте падает отдельно, кроме головы игрока.
	 * Кожа и факел тоже падают сами, по своим шансам.
	 */
	public static void dropBonus(Mob mob, ServerLevel level, boolean killedByPlayer) {
		if (!isRitual(mob) || !killedByPlayer || !(mob instanceof RitualMember data)) {
			return;
		}
		switch (data.evolvingthreats$getRitualHead()) {
			case HEAD_PLAYER -> drop(mob, level, new ItemStack(Items.COMPASS));
			case HEAD_CREEPER -> drop(mob, level, new ItemStack(Items.GUNPOWDER, Math.max(1, GUNPOWDER_COUNT)));
			case HEAD_PIGLIN -> drop(mob, level, new ItemStack(Items.GOLD_INGOT, Math.max(1, GOLD_INGOT_COUNT)));
			case HEAD_WITHER -> {
				drop(mob, level, new ItemStack(Items.COAL, Math.max(1, COAL_COUNT)));
				drop(mob, level, new ItemStack(Items.WITHER_ROSE, Math.max(1, ROSE_COUNT)));
			}
			default -> {
			}
		}
	}

	/** Перед целями ИИ. Стая с головой игрока снова берёт ближайшего, даже за стеной. */
	public static void syncHuntTarget(Mob mob) {
		if (!isPlayerHead(mob) || SENSE_BLOCKS <= 0.0 || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		Player player = nearestPlayer(level, mob, SENSE_BLOCKS);
		if (player != null && mob.getTarget() != player) {
			mob.setTarget(player);
		}
	}

	/** true = этот урон стае пиглина не проходит: лава, огонь в тени. Солнце на открытом месте жжёт. */
	public static boolean shouldCancelFire(Mob mob, DamageSource source) {
		if (!isPiglinHead(mob)) {
			return false;
		}
		if (source.is(DamageTypes.LAVA)) {
			return true;
		}
		return source.is(DamageTypeTags.IS_FIRE) && !inSun(mob);
	}

	/** Удар или стрела стаи иссушителя. На нежить не вешается: иссушение её лечит. */
	public static void applyWither(DamageSource source, LivingEntity victim) {
		LivingEntity attacker = null;
		if (source.getEntity() instanceof LivingEntity living) {
			attacker = living;
		} else if (source.getDirectEntity() instanceof Projectile projectile
				&& projectile.getOwner() instanceof LivingEntity owner) {
			attacker = owner;
		}
		if (!(attacker instanceof Mob mob) || !isWitherHead(mob)) {
			return;
		}
		if (areAllies(mob, victim) || victim.isInvertedHealAndHarm()) {
			return;
		}
		MobEffectInstance current = victim.getEffect(MobEffects.WITHER);
		int refreshBelow = Math.max(0, WITHER_REFRESH_IF_BELOW_SECONDS) * 20;
		if (current != null && current.getDuration() > refreshBelow) {
			return;
		}
		victim.addEffect(new MobEffectInstance(
				MobEffects.WITHER,
				Math.max(1, WITHER_SECONDS) * 20,
				0), attacker);
	}

	/** Хлопок стаи крипера. Своих не задевает: урон от союзника режется отдельно. */
	public static void onDeath(Mob mob) {
		if (!isCreeperHead(mob) || EXPLOSION_POWER <= 0.0f || !(mob instanceof RitualMember data)) {
			return;
		}
		if (data.evolvingthreats$ritualExploded() || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		data.evolvingthreats$setRitualExploded(true);
		List<Mob> allies = nearbyAllies(mob, 8.0);
		Vec3[] speeds = new Vec3[allies.size()];
		for (int i = 0; i < allies.size(); i++) {
			speeds[i] = allies.get(i).getDeltaMovement();
		}
		level.explode(
				mob,
				mob.getX(),
				mob.getY(),
				mob.getZ(),
				EXPLOSION_POWER,
				Level.ExplosionInteraction.MOB);
		for (int i = 0; i < allies.size(); i++) {
			Mob ally = allies.get(i);
			if (!ally.isAlive()) {
				continue;
			}
			ally.setDeltaMovement(speeds[i]);
			ally.syncVelocity = true;
		}
	}

	public static boolean areAllies(LivingEntity first, LivingEntity second) {
		if (!(first instanceof RitualMember a) || !(second instanceof RitualMember b)) {
			return false;
		}
		if (!a.evolvingthreats$isRitual() || !b.evolvingthreats$isRitual()) {
			return false;
		}
		UUID group = a.evolvingthreats$getRitualGroup();
		return group != null && group.equals(b.evolvingthreats$getRitualGroup());
	}

	/**
	 * Команда. head = 0 значит случайная голова.
	 * false — механика выключена или номер головы не из списка.
	 */
	public static boolean spawnCommand(ServerLevel level, Vec3 pos, float yaw, int head, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return false;
		}
		int chosen = head == 0 ? randomHead(level) : head;
		if (chosen < HEAD_PLAYER || chosen > HEAD_WITHER) {
			return false;
		}
		begin(level, pos, yaw, chosen, null, lookAt);
		return true;
	}

	private static void begin(
			ServerLevel level,
			Vec3 pos,
			float yaw,
			int head,
			@Nullable Mob existing,
			@Nullable LivingEntity lookAt) {
		var random = level.getRandom();
		int zombies = 1 + random.nextInt(2);
		int skeletons = 1 + random.nextInt(2);
		boolean leaderZombie = existing instanceof Zombie || existing == null;
		int total = zombies + skeletons;
		boolean[] torch = hands(total, random);
		UUID group = UUID.randomUUID();
		int index = 0;
		if (existing != null) {
			prepare(existing, head, group, torch[0]);
			equip(existing);
			if (lookAt instanceof Player player) {
				existing.setTarget(player);
			}
			index = 1;
		}
		for (; index < total; index++) {
			boolean zombie = leaderZombie ? index < zombies : index >= skeletons;
			spawnMember(level, slotPos(level, pos, yaw, index, total), yaw, zombie, head, group, torch[index], lookAt);
		}
	}

	private static void spawnMember(
			ServerLevel level,
			Vec3 pos,
			float yaw,
			boolean zombie,
			int head,
			UUID group,
			boolean torch,
			@Nullable LivingEntity lookAt) {
		Mob mob = zombie
				? EntityTypes.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED)
				: EntityTypes.SKELETON.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (mob == null) {
			return;
		}
		mob.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		prepare(mob, head, group, torch);
		mob.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(mob.blockPosition()),
				EntitySpawnReason.MOB_SUMMONED,
				(SpawnGroupData) null);
		if (lookAt instanceof Player player) {
			mob.setTarget(player);
		}
		level.addFreshEntity(mob);
	}

	private static void prepare(Mob mob, int head, UUID group, boolean torch) {
		if (!(mob instanceof RitualMember data)) {
			return;
		}
		data.evolvingthreats$setRitual(true);
		data.evolvingthreats$setRitualHead(head);
		data.evolvingthreats$setRitualGroup(group);
		data.evolvingthreats$setRitualTorch(torch);
	}

	private static void equip(Mob mob) {
		if (!(mob instanceof RitualMember data)) {
			return;
		}
		mob.setCanPickUpLoot(false);
		int head = data.evolvingthreats$getRitualHead();
		mob.setItemSlot(EquipmentSlot.HEAD, headStack(head));
		mob.setDropChance(EquipmentSlot.HEAD, head == HEAD_PLAYER ? 0.0f : Math.max(0.0f, HEAD_DROP_CHANCE));
		boolean torch = data.evolvingthreats$isRitualTorch();
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(torch ? Items.REDSTONE_TORCH : Items.BONE));
		mob.setDropChance(EquipmentSlot.MAINHAND, torch ? Math.max(0.0f, TORCH_DROP_CHANCE) : 0.0f);
		mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		mob.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
		ItemStack leather = blackGreenLeather(Items.LEATHER_CHESTPLATE);
		mob.setItemSlot(EquipmentSlot.CHEST, leather);
		mob.setItemSlot(EquipmentSlot.LEGS, blackGreenLeather(Items.LEATHER_LEGGINGS));
		mob.setItemSlot(EquipmentSlot.FEET, blackGreenLeather(Items.LEATHER_BOOTS));
		float leatherDrop = Math.max(0.0f, LEATHER_DROP_CHANCE);
		for (EquipmentSlot slot : LEATHER_SLOTS) {
			mob.setDropChance(slot, leatherDrop);
		}
		if (head == HEAD_PLAYER) {
			var follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
			if (follow != null && follow.getBaseValue() < SENSE_BLOCKS) {
				follow.setBaseValue(SENSE_BLOCKS);
			}
		}
		if (mob instanceof Skeleton skeleton) {
			skeleton.reassessWeaponGoal();
		}
	}

	private static boolean canStart(Mob mob, EntitySpawnReason reason) {
		if (!ENABLED || mob.isBaby()) {
			return false;
		}
		if (reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.MOB_SUMMONED) {
			return false;
		}
		if (mob instanceof Zombie zombie) {
			if (zombie.getType() != EntityTypes.ZOMBIE) {
				return false;
			}
			if (zombie instanceof ZombieOverseerEntity overseer && overseer.evolvingthreats$isOverseer()) {
				return false;
			}
		} else if (mob instanceof Skeleton skeleton) {
			if (skeleton.getType() != EntityTypes.SKELETON) {
				return false;
			}
		} else {
			return false;
		}
		return !(mob instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant());
	}

	private static boolean isPlayerHead(Mob mob) {
		return isRitual(mob) && ((RitualMember) mob).evolvingthreats$getRitualHead() == HEAD_PLAYER;
	}

	private static boolean isCreeperHead(Mob mob) {
		return isRitual(mob) && ((RitualMember) mob).evolvingthreats$getRitualHead() == HEAD_CREEPER;
	}

	private static boolean isPiglinHead(Mob mob) {
		return isRitual(mob) && ((RitualMember) mob).evolvingthreats$getRitualHead() == HEAD_PIGLIN;
	}

	private static boolean isWitherHead(Mob mob) {
		return isRitual(mob) && ((RitualMember) mob).evolvingthreats$getRitualHead() == HEAD_WITHER;
	}

	/** День и небо над головой. Тогда огонь от солнца стае пиглина проходит. */
	private static boolean inSun(Mob mob) {
		Level level = mob.level();
		if (!level.isBrightOutside()) {
			return false;
		}
		return level.canSeeSky(mob.blockPosition().above());
	}

	private static @Nullable Player nearestPlayer(ServerLevel level, Mob mob, double range) {
		double max = range * range;
		AABB box = mob.getBoundingBox().inflate(range);
		Player closest = null;
		double best = max;
		for (Player player : level.getEntitiesOfClass(Player.class, box)) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			double dist = mob.distanceToSqr(player);
			if (dist <= best) {
				best = dist;
				closest = player;
			}
		}
		return closest;
	}

	private static List<Mob> nearbyAllies(Mob mob, double range) {
		List<Mob> allies = new ArrayList<>();
		if (!(mob.level() instanceof ServerLevel level)) {
			return allies;
		}
		AABB box = mob.getBoundingBox().inflate(range);
		for (Mob other : level.getEntitiesOfClass(Mob.class, box)) {
			if (other != mob && other.isAlive() && areAllies(mob, other)) {
				allies.add(other);
			}
		}
		return allies;
	}

	private static ItemStack headStack(int head) {
		return switch (head) {
			case HEAD_PLAYER -> new ItemStack(Items.PLAYER_HEAD);
			case HEAD_CREEPER -> new ItemStack(Items.CREEPER_HEAD);
			case HEAD_PIGLIN -> new ItemStack(Items.PIGLIN_HEAD);
			case HEAD_WITHER -> new ItemStack(Items.WITHER_SKELETON_SKULL);
			default -> ItemStack.EMPTY;
		};
	}

	private static ItemStack blackGreenLeather(net.minecraft.world.level.ItemLike item) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(LEATHER_COLOR));
		return stack;
	}

	/** Хотя бы один факел и одна кость, потом порядок перемешивается. */
	private static boolean[] hands(int count, net.minecraft.util.RandomSource random) {
		boolean[] torch = new boolean[Math.max(0, count)];
		if (count <= 0) {
			return torch;
		}
		torch[0] = true;
		if (count > 1) {
			torch[1] = false;
		}
		for (int i = 2; i < count; i++) {
			torch[i] = random.nextBoolean();
		}
		for (int i = count - 1; i > 0; i--) {
			int swap = random.nextInt(i + 1);
			boolean tmp = torch[i];
			torch[i] = torch[swap];
			torch[swap] = tmp;
		}
		return torch;
	}

	private static Vec3 slotPos(ServerLevel level, Vec3 center, float yaw, int index, int total) {
		if (index <= 0) {
			return center;
		}
		double angle = (index / (double) total) * Math.PI * 2.0 + yaw * Mth.DEG_TO_RAD;
		Vec3 wanted = center.add(Math.cos(angle) * 2.2, 0.0, Math.sin(angle) * 2.2);
		AABB box = EntityTypes.ZOMBIE.getDimensions().makeBoundingBox(wanted);
		if (level.noCollision(box)) {
			return wanted;
		}
		return center;
	}

	private static int randomHead(Mob mob) {
		return HEAD_PLAYER + mob.getRandom().nextInt(4);
	}

	private static int randomHead(ServerLevel level) {
		return HEAD_PLAYER + level.getRandom().nextInt(4);
	}

	private static void drop(Mob mob, ServerLevel level, ItemStack stack) {
		if (!stack.isEmpty()) {
			mob.spawnAtLocation(level, stack);
		}
	}
}
