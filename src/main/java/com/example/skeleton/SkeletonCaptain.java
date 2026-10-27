package com.example.skeleton;

import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;

import com.example.MobEnchantments;
import com.example.entity.RaidParticipantEntity;
import com.example.entity.SkeletonSquadMember;
import com.example.siege.SiegeNight;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Капитан скелетов и два помощника.
 * Капитан: алмазная броня, лук (сила V, откидывание IV) и алмазный меч
 * (острота V, заговор огня II, отдача II). Лук с откидыванием IV в выживании
 * получить нельзя, поэтому с капитана он не падает никогда.
 * Помощники: полный железный сет и железный меч в каждой руке.
 * Броня и мечи помощников чаруются случайно, кусок за куском: может не быть чар,
 * может часть, может всё.
 * Капитан сам идёт к ближайшему игроку, как гнилой надсмотрщик.
 * Помощники не отходят от капитана слишком далеко и не дерутся со своими.
 */
public final class SkeletonCaptain {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — капитаны не появляются, команда тоже не спавнит отряд. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный скелет при появлении окажется капитаном и позовёт двоих.
	 * 0.006 = 0.6%, примерно как гнилой надсмотрщик. Команда этот шанс не бросает.
	 * Спаунер скелетов тоже бросает этот шанс. Призванная подмога и команда /summon — нет.
	 */
	public static final float SPAWN_CHANCE = 0.006f;

	/** Сколько помощников выходит вместе с капитаном. 2 = двое. */
	public static final int HELPER_COUNT = 2;

	/** Имя над головой. Это бирка: пока имя есть, капитан сам не исчезает, как названный моб. */
	public static final String CAPTAIN_NAME = "Captain of the Skeletons";

	/**
	 * Шанс (0.0–1.0) зачаровать КАЖДЫЙ кусок брони капитана и помощников.
	 * 0.45 = 45% на шлем, отдельно 45% на нагрудник и так далее.
	 * Поэтому бывает голый алмаз, бывает одна зачарованная деталь, бывает весь сет.
	 * 0 = броня всегда без чар. 1 = чары на каждом куске.
	 */
	public static final float ARMOR_ENCHANT_CHANCE = 0.45f;

	/**
	 * Шанс (0.0–1.0) зачаровать КАЖДЫЙ железный меч помощника. 0.55 = 55% на меч.
	 * Какие чары и какого уровня — как у обычного оружия мода (чаще низкие).
	 * Меч и лук капитана этим шансом не пользуются: у них чары заданы жёстко.
	 */
	public static final float HELPER_SWORD_ENCHANT_CHANCE = 0.55f;

	/** Откидывание на луке капитана. У ванильного лука потолок II. 4 = IV, нарочно выше. */
	public static final int BOW_PUNCH_LEVEL = 4;

	/** Сила на луке капитана. 5 = V, это ванильный потолок. */
	public static final int BOW_POWER_LEVEL = 5;

	/** Острота меча капитана. 5 = V. */
	public static final int SWORD_SHARPNESS_LEVEL = 5;

	/** Заговор огня на мече капитана. 2 = II. */
	public static final int SWORD_FIRE_ASPECT_LEVEL = 2;

	/** Отдача на мече капитана. 2 = II. */
	public static final int SWORD_KNOCKBACK_LEVEL = 2;

	/**
	 * Как далеко (блоки) помощник может отойти от капитана, пока нет боя.
	 * Дальше — догоняет. 6 = держатся кучкой.
	 */
	public static final double IDLE_FOLLOW_BLOCKS = 6.0;

	/**
	 * То же в бою. 14 = помощник успевает добежать до игрока, но не уходит за ним
	 * на другой край поля, пока капитан стоит и стреляет.
	 */
	public static final double COMBAT_LEASH_BLOCKS = 14.0;

	/**
	 * Если кто-то из отряда видит цель, остальные подхватывают её, пока она не дальше
	 * этого (блоки) от них. 32 = весь отряд вступает, даже если сам игрока ещё не видит.
	 */
	public static final double CALL_RANGE_BLOCKS = 32.0;

	/**
	 * Как далеко капитан сам идёт за игроком, блоки. 112 — как радиус погони гнилого надсмотрщика.
	 * У помощников остаётся FOLLOW_RANGE: они не ищут игрока сами, они бегут за капитаном.
	 */
	public static final double CAPTAIN_FOLLOW_RANGE = 112.0;

	/**
	 * Дальше этого капитан бросает закреплённого игрока, блоки. 128 — как у надсмотрщика.
	 * Ближе — идёт к нему, даже если между ними стена и капитан его ещё не видел.
	 * Наблюдатель не закрепляется.
	 */
	public static final double HUNT_ABANDON_DISTANCE = 128.0;

	/**
	 * true — капитан идёт и за игроком в креативе. Так команда /etcaptain показывает погоню.
	 * false — как у надсмотрщика: креатив не закрепляет. Наблюдателя не трогает в любом случае.
	 */
	public static final boolean HUNT_CREATIVE = true;

	/** Скорость, с которой помощник догоняет капитана. 1.15 = чуть быстрее шага скелета. */
	public static final double GROUP_SPEED = 1.15;

	/** Как далеко помощник держит цель, блоки. У обычного скелета около 16. 32 = не теряет игрока рядом с капитаном. */
	public static final double FOLLOW_RANGE = 32.0;

	// ----- лут капитана (помощники этого не роняют) -----

	/**
	 * Шанс (0.0–1.0), что с капитана упадёт алмазный меч с его чарами.
	 * 0.15 = 15%. Лук сюда не входит: он не падает никогда.
	 */
	public static final float SWORD_DROP_CHANCE = 0.15f;

	/**
	 * Шанс (0.0–1.0) на КАЖДЫЙ кусок брони капитана. 0.085 ≈ 8.5%, как у остальной брони мода.
	 * Чары на упавшем куске сохраняются.
	 */
	public static final float ARMOR_DROP_CHANCE = 0.085f;

	/**
	 * Шанс (0.0–1.0) дополнительно выронить 1 алмаз. 0.20 = 20%.
	 * Это сверх меча и брони.
	 */
	public static final float DIAMOND_DROP_CHANCE = 0.20f;

	/**
	 * Сколько бирок падает с капитана всегда. 1 = одна бирка.
	 * 0 = бирку не ронять. Лук от этого числа не зависит.
	 */
	public static final int NAME_TAG_COUNT = 1;

	/** Шанс выпадения железной брони и мечей помощника, на каждый предмет. 0.085 ≈ 8.5%. */
	public static final float HELPER_DROP_CHANCE = 0.085f;

	// ========================================================================

	public static final int ROLE_NONE = 0;
	public static final int ROLE_CAPTAIN = 1;
	public static final int ROLE_HELPER = 2;

	private static final EquipmentSlot[] ARMOR = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private SkeletonCaptain() {
	}

	public static boolean isCaptain(Mob mob) {
		return ENABLED
				&& mob instanceof SkeletonSquadMember squad
				&& squad.evolvingthreats$getSquadRole() == ROLE_CAPTAIN;
	}

	/** Вызвать в конце появления скелета. true = это капитан или помощник, обычный набор мода им не нужен. */
	public static boolean tryClaim(AbstractSkeleton skeleton, ServerLevelAccessor level, EntitySpawnReason reason) {
		if (!ENABLED || !(skeleton instanceof Skeleton) || !(skeleton instanceof SkeletonSquadMember squad)) {
			return false;
		}
		if (squad.evolvingthreats$getSquadRole() == ROLE_CAPTAIN) {
			if (skeleton.getCustomName() == null) {
				equipCaptain(skeleton);
			}
			if (squad.evolvingthreats$getSquadMembers().isEmpty() && level instanceof ServerLevel server) {
				spawnHelpers(server, (Skeleton) skeleton);
			}
			return true;
		}
		if (squad.evolvingthreats$getSquadRole() == ROLE_HELPER) {
			if (!skeleton.getMainHandItem().is(Items.IRON_SWORD)) {
				equipHelper(skeleton);
			}
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
		// С какого дня капитаны вообще появляются (ThreatTable.SKELETON_CAPTAIN_DAY). Шанс ниже от дня не меняется.
		if (!ThreatScale.reached(skeleton.level(), ThreatTable.SKELETON_CAPTAIN_DAY)) {
			return false;
		}
		if (skeleton.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		squad.evolvingthreats$setSquadRole(ROLE_CAPTAIN);
		squad.evolvingthreats$setSquadId(UUID.randomUUID());
		equipCaptain(skeleton);
		if (level instanceof ServerLevel server) {
			spawnHelpers(server, (Skeleton) skeleton);
			beginHunt(server, (Skeleton) skeleton, null);
		}
		return true;
	}

	/** Команда: капитан и два помощника перед игроком. null — механика выключена. */
	public static @Nullable Skeleton spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return null;
		}
		Skeleton captain = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
		if (captain == null) {
			throw new IllegalStateException("Failed to create skeleton captain");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		captain.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (captain instanceof SkeletonSquadMember squad) {
			squad.evolvingthreats$setSquadRole(ROLE_CAPTAIN);
			squad.evolvingthreats$setSquadId(UUID.randomUUID());
		}
		captain.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(captain.blockPosition()),
				EntitySpawnReason.COMMAND,
				(SpawnGroupData) null);
		beginHunt(level, captain, lookAt);
		level.addFreshEntity(captain);
		return captain;
	}

	/** Кто держит отряд. Живой капитан, а если его уже нет — самый «старший» из живых помощников. */
	public static @Nullable Skeleton anchor(Skeleton self, SkeletonSquadMember squad) {
		if (!(self.level() instanceof ServerLevel level)) {
			return null;
		}
		Skeleton captain = null;
		Skeleton eldest = null;
		for (UUID id : squad.evolvingthreats$getSquadMembers()) {
			if (!(level.getEntity(id) instanceof Skeleton member) || !member.isAlive()) {
				continue;
			}
			if (member instanceof SkeletonSquadMember data && data.evolvingthreats$getSquadRole() == ROLE_CAPTAIN) {
				captain = member;
			}
			if (eldest == null || member.getUUID().compareTo(eldest.getUUID()) < 0) {
				eldest = member;
			}
		}
		if (captain != null) {
			return captain;
		}
		return eldest;
	}

	/** Свои по отряду: капитан и его помощники. Чужой отряд и обычный скелет — не свои. */
	public static boolean isSquadAlly(LivingEntity self, LivingEntity other) {
		if (!(self instanceof SkeletonSquadMember a) || !(other instanceof SkeletonSquadMember b)) {
			return false;
		}
		if (a.evolvingthreats$getSquadRole() == ROLE_NONE || b.evolvingthreats$getSquadRole() == ROLE_NONE) {
			return false;
		}
		UUID squadId = a.evolvingthreats$getSquadId();
		return squadId != null && squadId.equals(b.evolvingthreats$getSquadId());
	}

	/** Конец серверного тика: цель одного скелета становится целью всего отряда. Своих в цель не берём. */
	public static void shareTarget(ServerLevel level, Skeleton self) {
		if (!(self instanceof SkeletonSquadMember squad) || squad.evolvingthreats$getSquadRole() == ROLE_NONE) {
			return;
		}
		if (self.getTarget() != null && isSquadAlly(self, self.getTarget())) {
			self.setTarget(null);
		}
		LivingEntity shared = null;
		for (UUID id : squad.evolvingthreats$getSquadMembers()) {
			if (!(level.getEntity(id) instanceof Skeleton member) || !member.isAlive()) {
				continue;
			}
			LivingEntity target = member.getTarget();
			if (target != null && target.isAlive() && !isSquadAlly(self, target)) {
				shared = target;
				break;
			}
		}
		if (shared == null || !self.canAttack(shared)) {
			return;
		}
		if (self.distanceToSqr(shared) > CALL_RANGE_BLOCKS * CALL_RANGE_BLOCKS) {
			return;
		}
		if (self.getTarget() != shared) {
			self.setTarget(shared);
		}
	}

	/** Дополнительный лут капитана. Лук не роняется даже здесь. */
	public static void dropBonus(ServerLevel level, Mob mob) {
		if (!isCaptain(mob)) {
			return;
		}
		ItemStack sword = diamondSword(mob);
		if (!sword.isEmpty() && mob.getRandom().nextFloat() < SWORD_DROP_CHANCE) {
			mob.spawnAtLocation(level, sword.copy());
		}
		if (NAME_TAG_COUNT > 0) {
			mob.spawnAtLocation(level, new ItemStack(Items.NAME_TAG, NAME_TAG_COUNT));
		}
		if (DIAMOND_DROP_CHANCE > 0.0f && mob.getRandom().nextFloat() < DIAMOND_DROP_CHANCE) {
			mob.spawnAtLocation(level, new ItemStack(Items.DIAMOND));
		}
	}

	private static void equipCaptain(AbstractSkeleton skeleton) {
		skeleton.setCustomName(Component.literal(CAPTAIN_NAME));
		skeleton.setCustomNameVisible(true);
		skeleton.setCanPickUpLoot(false);
		skeleton.setPersistenceRequired();

		ItemStack bow = new ItemStack(Items.BOW);
		enchant(skeleton, bow, Enchantments.POWER, BOW_POWER_LEVEL);
		enchant(skeleton, bow, Enchantments.PUNCH, BOW_PUNCH_LEVEL);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow);

		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		enchant(skeleton, sword, Enchantments.SHARPNESS, SWORD_SHARPNESS_LEVEL);
		enchant(skeleton, sword, Enchantments.FIRE_ASPECT, SWORD_FIRE_ASPECT_LEVEL);
		enchant(skeleton, sword, Enchantments.KNOCKBACK, SWORD_KNOCKBACK_LEVEL);
		skeleton.setItemSlot(EquipmentSlot.OFFHAND, sword);

		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		skeleton.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		skeleton.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
		skeleton.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
		MobEnchantments.rollArmorPieces(skeleton, ARMOR_ENCHANT_CHANCE);

		skeleton.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		skeleton.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
		for (EquipmentSlot slot : ARMOR) {
			skeleton.setDropChance(slot, ARMOR_DROP_CHANCE);
		}
		setFollowRange(skeleton, CAPTAIN_FOLLOW_RANGE);
		skeleton.reassessWeaponGoal();
	}

	private static void equipHelper(AbstractSkeleton skeleton) {
		skeleton.setCanPickUpLoot(false);
		skeleton.setPersistenceRequired();
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		skeleton.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.IRON_SWORD));
		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		skeleton.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		skeleton.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		skeleton.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		MobEnchantments.rollHeldWeapons(skeleton, HELPER_SWORD_ENCHANT_CHANCE);
		MobEnchantments.rollArmorPieces(skeleton, ARMOR_ENCHANT_CHANCE);

		skeleton.setDropChance(EquipmentSlot.MAINHAND, HELPER_DROP_CHANCE);
		skeleton.setDropChance(EquipmentSlot.OFFHAND, HELPER_DROP_CHANCE);
		for (EquipmentSlot slot : ARMOR) {
			skeleton.setDropChance(slot, HELPER_DROP_CHANCE);
		}
		setFollowRange(skeleton, FOLLOW_RANGE);
		skeleton.reassessWeaponGoal();
	}

	private static void spawnHelpers(ServerLevel level, Skeleton captain) {
		if (!(captain instanceof SkeletonSquadMember squad)) {
			return;
		}
		List<Skeleton> helpers = new ArrayList<>();
		Vec3 side = sideVector(captain);
		int count = Math.max(0, HELPER_COUNT);
		for (int i = 0; i < count; i++) {
			double offset = (i - (count - 1) / 2.0) * 1.8;
			Vec3 pos = findStand(level, captain, captain.position().add(side.scale(offset)));
			Skeleton helper = EntityTypes.SKELETON.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (helper == null) {
				continue;
			}
			helper.snapTo(pos.x, pos.y, pos.z, captain.getYRot(), 0.0f);
			if (helper instanceof SkeletonSquadMember data) {
				data.evolvingthreats$setSquadRole(ROLE_HELPER);
				data.evolvingthreats$setSquadId(squad.evolvingthreats$getSquadId());
			}
			helper.finalizeSpawn(
					level,
					level.getCurrentDifficultyAt(helper.blockPosition()),
					EntitySpawnReason.MOB_SUMMONED,
					(SpawnGroupData) null);
			level.addFreshEntity(helper);
			helpers.add(helper);
		}
		List<UUID> ids = new ArrayList<>();
		ids.add(captain.getUUID());
		for (Skeleton helper : helpers) {
			ids.add(helper.getUUID());
		}
		remember(captain, ids);
		for (Skeleton helper : helpers) {
			remember(helper, ids);
		}
	}

	private static void remember(Skeleton skeleton, List<UUID> ids) {
		if (skeleton instanceof SkeletonSquadMember squad) {
			squad.evolvingthreats$setSquadMembers(new ArrayList<>(ids));
		}
	}

	private static Vec3 findStand(ServerLevel level, Skeleton captain, Vec3 wanted) {
		AABB box = captain.getDimensions(captain.getPose()).makeBoundingBox(wanted);
		if (level.noCollision(box)) {
			return wanted;
		}
		return captain.position();
	}

	private static Vec3 sideVector(Skeleton captain) {
		Vec3 look = captain.getLookAngle();
		Vec3 side = new Vec3(-look.z, 0.0, look.x);
		if (side.lengthSqr() < 1.0E-4) {
			return new Vec3(1.0, 0.0, 0.0);
		}
		return side.normalize();
	}

	private static ItemStack diamondSword(Mob mob) {
		if (mob.getMainHandItem().is(Items.DIAMOND_SWORD)) {
			return mob.getMainHandItem();
		}
		if (mob.getOffhandItem().is(Items.DIAMOND_SWORD)) {
			return mob.getOffhandItem();
		}
		return ItemStack.EMPTY;
	}

	private static void enchant(Mob mob, ItemStack stack, ResourceKey<Enchantment> enchantment, int level) {
		if (level <= 0 || !(mob.level() instanceof ServerLevel)) {
			return;
		}
		var holder = mob.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment);
		EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(holder, level));
	}

	private static void setFollowRange(AbstractSkeleton skeleton, double blocks) {
		var follow = skeleton.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(blocks);
		}
	}

	/**
	 * Закрепить капитана на игроке. prefer — кого указала команда.
	 * Иначе ближайший живой игрок в радиусе охоты, не в креативе и не в наблюдателе.
	 */
	private static void beginHunt(ServerLevel level, Skeleton captain, @Nullable LivingEntity prefer) {
		if (!(captain instanceof SkeletonSquadMember squad)) {
			return;
		}
		Player player = null;
		if (prefer instanceof Player chosen && chosen.isAlive() && !chosen.isSpectator()) {
			player = chosen;
		}
		if (player == null) {
			player = findNearestPlayer(level, captain, HUNT_ABANDON_DISTANCE);
		}
		if (player == null) {
			return;
		}
		squad.evolvingthreats$setHuntTargetId(player.getUUID());
		captain.setTarget(player);
		for (UUID id : squad.evolvingthreats$getSquadMembers()) {
			if (level.getEntity(id) instanceof Skeleton member && member != captain) {
				member.setTarget(player);
			}
		}
	}

	/** Ближайший игрок в радиусе. Наблюдатель пропускается. null — никого нет. */
	public static @Nullable Player tryLockNearestPlayer(Skeleton captain, SkeletonSquadMember squad) {
		if (!(captain.level() instanceof ServerLevel level)) {
			return null;
		}
		Player player = findNearestPlayer(level, captain, HUNT_ABANDON_DISTANCE);
		if (player == null) {
			return null;
		}
		squad.evolvingthreats$setHuntTargetId(player.getUUID());
		return player;
	}

	private static @Nullable Player findNearestPlayer(ServerLevel level, Skeleton skeleton, double maxDistance) {
		double maxDistSqr = maxDistance * maxDistance;
		AABB box = skeleton.getBoundingBox().inflate(maxDistance);
		List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, box,
				player -> player.isAlive()
						&& !player.isSpectator()
						&& (HUNT_CREATIVE || !player.isCreative()));
		return players.stream()
				.filter(player -> skeleton.distanceToSqr(player) <= maxDistSqr)
				.min(Comparator.comparingDouble(skeleton::distanceToSqr))
				.orElse(null);
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
