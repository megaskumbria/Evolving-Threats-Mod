package com.example.ritual;

import com.example.entity.RaidParticipantEntity;
import com.example.entity.RitualMember;
import com.example.entity.ZombieOverseerEntity;
import com.example.siege.SiegeNight;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 Rare pack: 1-2 zombies and 1-2 skeletons in dark green skin.
 No helmet. Everyone has the same head, and it decides the behavior of the pack.
 Someone has a red redstone torch in their hand, someone has a bone.
 Charms, random weapons and potions of fashion are not given to them - otherwise the pack ceases to look the same.
 */
public final class RitualBand {

	// ===================== SETTINGS (change these numbers) =====================

	/** Turn on the mechanics. False - packs do not appear, the team does not spawn either. */
	public static final boolean ENABLED = true;

	/**
	 Chance (0.0–1.0) that a common zombie or common skeleton will be the beginning of the pack.
	 0.002 = 0.2%, one in five hundred. FIXED: From day one, the threat scale does not increase.
	 Drop and zombies, and skeletons, so the mixed spawner pack a little more often.
	 Called groupmates, husk, wanderer and team /summon skip this roll.
	 * 0 = commands only.
	 */
	public static final float SPAWN_CHANCE = 0.002f;

	/**
	 * Everyone's experience. 50 is its number, the total multiplier of experience does not double it.
	 In the common zombie and skeleton in Game 5, the mod doubles them to 10.
	 Falls only if a moba kills a player: this is how the game works.
	 * 0 = no experience.
	 */
	public static final int XP_REWARD = 50;

	/**
	 Skin color, RGB. 0x1B4F2A is dark green, darker than a regular green dye.
	 * A piece of armor falls in the same color.
	 */
	public static final int LEATHER_COLOR = 0x1B4F2A;

	/**
	 * Chance (0.0-1.0) that one piece of skin will fall out. 0.085 ≈ 8.5%, as standard armor fashion.
	 Helmet is not worn, head instead. 0 = skin doesn't fall.
	 */
	public static final float LEATHER_DROP_CHANCE = 0.085f;

	/**
	 The chance of a red torch falling out of your hand. 0.35 = 35%. The bone doesn't fall out of your hand.
	 The skeleton is already falling.
	 */
	public static final float TORCH_DROP_CHANCE = 0.35f;

	/**
	 * Chance (0.0-1.0) that the head of the cryper, piglin or the skull of the dryer will fall,
	 If a mob killed a player. 0.025 = 2.5%, like a skull from an exhumerator skeleton.
	 The head of the player does not use this chance: instead of it, a compass falls with the same chance.
	 * I/II/III mining raises both heads and compass to 3.5%/4.5%/5.5%.
	 0 = these heads and compass do not fall. 1 = always.
	 */
	public static final float HEAD_DROP_CHANCE = 0.025f;

	/**
	 The health of everyone in the pack: zombies, skeletons, any head. 50 = 25 hearts.
	 The common zombie and skeleton in this mod accidentally 20-40. That number is put after that throw.
	 Less than 1 game kills almost immediately.
	 */
	public static final float MAX_HEALTH = 50.0f;

	/**
	 Damage to everyone in the pack: zombies, skeletons, any head. 2 damage = 1 heart.
	 A common zombie 3 (heart and a half), a skeleton with bone 2 (one heart).
	 8 = four hearts to armor. Without armor, the player suffers two strokes.
	 14 was too painful. 4 = like the exhumerator skeleton.
	 */
	public static final double ATTACK_DAMAGE = 8.0;

	/*How much gunpowder from a mob pack of crypers, including zombies. 1 = one pile. */
	public static final int GUNPOWDER_COUNT = 1;

	/*How many gold bars from a mob of piglin. 1 = one bar. */
	public static final int GOLD_INGOT_COUNT = 1;

	/*How much coal from the mob of the dryer pack. 1 = one coal. */
	public static final int COAL_COUNT = 1;

	/*How many roses from the mob of the dryer pack. 1 = one rose. */
	public static final int ROSE_COUNT = 1;

	/**
	 How far (blocks) does anyone in the pack notice a player if they see him?
	 52 = further than normal zombie (35) and skeleton (32).
	 The head of the player at the same distance smells through the walls.
	 0 = range like a regular mob, no one can feel through the walls.
	 */
	public static final double SENSE_BLOCKS = 52.0;

	/**
	 The power of cotton at the death of the cryper pack. A regular cryper has 3.
	 1.5 = painfully close, blocks almost does not break. He doesn't hurt his own.
	 * 0 = no explosion.
	 */
	public static final float EXPLOSION_POWER = 1.5f;

	/**
	 How many seconds does the drying from the impact or arrow of the pack of dryer last? 4 = four seconds.
	 Level I, weaker than the long drying of a real dryer skeleton.
	 */
	public static final int WITHER_SECONDS = 4;

	/**
	 As long as the target has more than so many seconds of drying, a new strike does not renew it.
	 1 = for a duration of 4 seconds, repeat about once every 3 seconds, not every point.
	 */
	public static final int WITHER_REFRESH_IF_BELOW_SECONDS = 1;

	/**
	 * How far (blocks) to look for other living members of the same pack.
	 * 64 covers a fight that has spread out. Members farther away, or in
	 * unloaded chunks, are not seen, so the pack counts as finished.
	 */
	public static final double GROUP_ALIVE_SEARCH_BLOCKS = 64.0;

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
	 The end of a zombie or skeleton. true = this pack: accidental weapon, armor,
	 Charms, shield, totem and special heads are not given to him.
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

	/*How much experience to give. -1 = not a pack, let the game decide. */
	public static int experienceReward(Mob mob) {
		if (!isRitual(mob)) {
			return -1;
		}
		return Math.max(0, XP_REWARD);
	}

	/**
	 * Extra possessions if the player is killed. The head on the slot falls separately, except for the player's head.
	 The skin and the torch also fall on their own.
	 */
	public static void dropBonus(Mob mob, ServerLevel level, DamageSource source, boolean killedByPlayer) {
		if (!isRitual(mob) || !killedByPlayer || !(mob instanceof RitualMember data)) {
			return;
		}
		switch (data.evolvingthreats$getRitualHead()) {
			case HEAD_PLAYER -> {
				if (mob.getRandom().nextFloat() < rareDropChance(source)) {
					drop(mob, level, new ItemStack(Items.COMPASS));
				}
			}
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

	/** Before AI targets. The pack with the head of the player again takes the nearest, even behind the wall. */
	public static void syncHuntTarget(Mob mob) {
		if (!isPlayerHead(mob) || SENSE_BLOCKS <= 0.0 || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		Player player = nearestPlayer(level, mob, SENSE_BLOCKS);
		if (player != null && mob.getTarget() != player) {
			mob.setTarget(player);
		}
	}

	/** true = this damage to a pack of piglin does not pass: lava, fire and sun. */
	public static boolean shouldCancelFire(Mob mob, DamageSource source) {
		if (!isPiglinHead(mob)) {
			return false;
		}
		return source.is(DamageTypes.LAVA) || source.is(DamageTypeTags.IS_FIRE);
	}

	/*After the Mob's step. Otherwise, the sun sets zombies on fire again in the same tick. */
	public static void keepOutOfFire(Mob mob) {
		if (!isPiglinHead(mob) || mob.getRemainingFireTicks() <= 0) {
			return;
		}
		mob.clearFire();
	}

	/** Strike or arrow of the dryer pack. The undead does not hang: drying it cures. */
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

	/*Cryper's pack. It does not hurt its own: damage from an ally is cut separately. */
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

	/**
	 * True when this mob is the last living member of its pack that the game can see.
	 * The advancement for wiping the pack uses this on the killing blow.
	 */
	public static boolean isLastLiving(Mob mob) {
		if (!(mob instanceof RitualMember data) || !data.evolvingthreats$isRitual()) {
			return false;
		}
		UUID group = data.evolvingthreats$getRitualGroup();
		if (group == null || !(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		double reach = Math.max(0.0, GROUP_ALIVE_SEARCH_BLOCKS);
		var box = new AABB(mob.position(), mob.position()).inflate(reach);
		for (Mob other : level.getEntitiesOfClass(Mob.class, box)) {
			if (other == mob || !other.isAlive()) {
				continue;
			}
			if (areAllies(mob, other)) {
				return false;
			}
		}
		return true;
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
	 * Team. head = 0 means random head.
	 False: Mechanics off or head number off the list.
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

	/**
	 Head Chance, Health, Strength of Impact and Range of View.
	 It is called when it appears and when the pack is loaded from the world:
	 Otherwise, those already living are left with an old reserve of lives, a weak stroke and a short range.
	 */
	public static void applyTuning(Mob mob) {
		if (!(mob instanceof RitualMember data) || !data.evolvingthreats$isRitual()) {
			return;
		}
		int head = data.evolvingthreats$getRitualHead();
		mob.setDropChance(EquipmentSlot.HEAD, head == HEAD_PLAYER ? 0.0f : Math.max(0.0f, HEAD_DROP_CHANCE));
		if (SENSE_BLOCKS > 0.0) {
			var follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
			if (follow != null && follow.getBaseValue() != SENSE_BLOCKS) {
				follow.setBaseValue(SENSE_BLOCKS);
			}
		}
		var attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null) {
			attack.setBaseValue(ATTACK_DAMAGE);
		}
		applyHealth(mob);
	}

	/**
	 * Raises stock to {@link #MAX HEALTH} If the strip was full, it becomes full and on a new stock.
	 Already removed health when loading the world is not treated.
	 */
	private static void applyHealth(Mob mob) {
		if (MAX_HEALTH < 1.0f) {
			return;
		}
		var maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null) {
			return;
		}
		double previous = maxHealth.getBaseValue();
		if (previous == MAX_HEALTH) {
			return;
		}
		float current = mob.getHealth();
		maxHealth.setBaseValue(MAX_HEALTH);
		if (current >= previous - 0.5f || current > MAX_HEALTH) {
			mob.setHealth(MAX_HEALTH);
		}
	}

	private static void equip(Mob mob) {
		if (!(mob instanceof RitualMember data)) {
			return;
		}
		mob.setCanPickUpLoot(false);
		int head = data.evolvingthreats$getRitualHead();
		mob.setItemSlot(EquipmentSlot.HEAD, headStack(head));
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
		applyTuning(mob);
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
		// On the night of the siege, ritual packs do not appear.
		if (SiegeNight.isWindowOpen(mob.level())) {
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

	/**
	 Same chance as the head on the slot. Mining adds 1% per level:
	 * I = 3,5%, II = 4,5%, III = 5,5%.
	 */
	private static float rareDropChance(DamageSource source) {
		float chance = Math.max(0.0f, HEAD_DROP_CHANCE);
		if (!(source.getEntity() instanceof LivingEntity killer) || !(killer.level() instanceof ServerLevel level)) {
			return chance;
		}
		var looting = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
		int levels = Math.max(
				EnchantmentHelper.getItemEnchantmentLevel(looting, killer.getMainHandItem()),
				EnchantmentHelper.getItemEnchantmentLevel(looting, killer.getOffhandItem()));
		if (levels <= 0) {
			return chance;
		}
		return Math.min(1.0f, chance + levels * 0.01f);
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

	/*At least one torch and one bone, then the order is mixed. */
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
