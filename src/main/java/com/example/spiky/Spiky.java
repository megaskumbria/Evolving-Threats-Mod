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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Rare normal zombie or skeleton named Spiky.
 * Not a separate entity type: same mob, one armor piece reflects damage.
 * Damage is set here, not by Thorns enchant level — in this game version Thorns level does not scale reflect damage.
 */
public final class Spiky {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — no new Spiky, command spawn off. Existing ones unchanged. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) that a normal zombie or skeleton becomes Spiky on spawn.
	 * 0.002 = 0.2%. Separate roll for zombie and skeleton.
	 * No chance before ThreatTable.SPIKY_DAY (40). From that day chance is fixed; moon does not change it.
	 * Command skips roll. Spawner rolls. Summoned reinforcements and /summon — no.
	 */
	public static final float SPAWN_CHANCE = 0.002f;

	/** Name tag. English, like other named mobs. */
	public static final String NAME = "Spiky";

	/**
	 * Hearts removed per reflect hit, inclusive range. 7 and 8 both possible.
	 * 1 heart = 2 damage. 7 hearts = 14 HP, 8 = 16.
	 * Armor, Protection, and shield do not reduce this. Absorption hearts are consumed first.
	 * Resistance potion still weakens the hit.
	 */
	public static final int HEARTS_MIN = 7;
	public static final int HEARTS_MAX = 8;

	/**
	 * Thorns level shown on the spiked piece. 3 = Thorns III, vanilla cap.
	 * Does NOT set reflect damage — hearts come from HEARTS_MIN and HEARTS_MAX.
	 * 0 = no enchant text on item; reflect still works.
	 * If the player wears the piece, vanilla Thorns applies weakly.
	 */
	public static final int THORNS_LEVEL = 3;

	// ========================================================================

	/** Reflect damage type: bypasses_armor and bypasses_enchantments. */
	private static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(
			Registries.DAMAGE_TYPE, EvolvingThreats.id("spiky"));

	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
	};

	/** Prevents reflect from chaining when reflect damage hits the player. */
	private static boolean spiking;

	private Spiky() {
	}

	public static boolean isSpiky(Mob mob) {
		return mob instanceof SpikyEntity data && data.evolvingthreats$isSpiky();
	}

	/**
	 * Vanilla Thorns from Spiky armor must be suppressed — otherwise weak Thorns
	 * stacks on top of 7–8 heart reflect.
	 */
	public static boolean isVanillaThorns(DamageSource source) {
		if (!source.is(DamageTypes.THORNS) || !(source.getEntity() instanceof Mob mob)) {
			return false;
		}
		return isSpiky(mob);
	}

	/**
	 * Roll spawn chance. true = Spiky.
	 * Spiked armor is applied in {@link #apply}, after normal armor.
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
	 * Command spawn as Spiky — ignores chance and day gate.
	 * skeleton = false zombie, true skeleton.
	 * false — disabled or spawn failed.
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

	/** Name tag and one spiked armor piece. Other armor already rolled normally. */
	public static void apply(Mob mob) {
		if (!isSpiky(mob)) {
			return;
		}
		mob.setCustomName(Component.literal(NAME).withStyle(Style.EMPTY.withItalic(false)));
		MobNames.showOnlyWhenLookedAt(mob);
		ensureSpikePiece(mob);
	}

	/** After world load. Does not re-roll armor slots. */
	public static void refresh(Mob mob) {
		if (!isSpiky(mob)) {
			return;
		}
		MobNames.showOnlyWhenLookedAt(mob);
	}

	/**
	 * Player hit already landed on Spiky. One reflect hit, not per armor piece.
	 * Hand, sword, arrow, and trident reflect. Fire, explosion, and foreign Thorns — no.
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
		boolean landedOnPlayer;
		try {
			landedOnPlayer = player.hurtServer(level, new DamageSource(type, spiky), damage);
		} finally {
			spiking = false;
		}
		if (landedOnPlayer) {
			level.playSound(
					null,
					player.getX(),
					player.getY(),
					player.getZ(),
					SoundEvents.THORNS_HIT,
					SoundSource.PLAYERS);
		}
	}

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
			chosen = ARMOR_SLOTS[random.nextInt(ARMOR_SLOTS.length)];
			RandomArmor.equipRandomPiece(mob, chosen);
		}
		addThorns(mob, chosen);
	}

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
