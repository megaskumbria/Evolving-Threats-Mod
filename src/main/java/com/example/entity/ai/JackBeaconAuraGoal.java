package com.example.entity.ai;

import com.example.beacon.JackLanternBeacon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.EnumSet;

/**
 Zombie with jack o'lantern on his head: while he sees the player - periodically accelerates zombies in radius.
 */
public class JackBeaconAuraGoal extends Goal {

	private final Zombie beacon;
	private int ticksUntilPulse;

	public JackBeaconAuraGoal(Zombie beacon) {
		this.beacon = beacon;
		this.setFlags(EnumSet.noneOf(Flag.class));
	}

	@Override
	public boolean canUse() {
		if (!JackLanternBeacon.isBeaconZombie(beacon)) {
			return false;
		}
		LivingEntity target = beacon.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}
		return beacon.getSensing().hasLineOfSight(target);
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		ticksUntilPulse = 0;
	}

	@Override
	public void tick() {
		LivingEntity target = beacon.getTarget();
		if (target == null) {
			return;
		}
		ticksUntilPulse--;
		if (ticksUntilPulse > 0) {
			return;
		}
		ticksUntilPulse = JackLanternBeacon.BEACON_PULSE_INTERVAL_TICKS;
		JackLanternBeacon.pulseAura(beacon, target);
	}
}
