package com.example.client.threat;

import com.example.EvolvingThreats;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatStartPayload;
import com.example.threat.ThreatTable;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

/**
 * The threat scale on the screen: on the left is the moon of the current day, in the center is a strip (day / 100), on the right is the skull of the current step.
 * By default, it hangs in the UPPER LEFT corner: on the top right, vanilla draws icons of potion effects, in the center of the bossbar.
 *
 * <p>Nothing comes from the server: the client counts the day and phase of the moon by itself according to the time of the world,
 * which the game already sends ({@link ThreatScale}). No signatures with the number of days — only pictures.
 * Pictures are in assets/evolving-threats/textures/gui/sprites/threat/ (makes tools/prepare_threat_textures.py).
 *
 * <p>The player can hide and show the pictures with a key (default H).
 * Rebind it in Options → Controls → Evolving Threats. Hiding the pictures does not pause the scale.
 */
public final class ThreatHud {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — the scale is not shown on the screen (this does not affect the mechanics itself). */
	public static final boolean ENABLED = true;

	/** Margin from the left edge of the screen, in interface pixels. More — the scale is to the right. */
	public static final int MARGIN_LEFT = 4;

	/** Margin from the top edge of the screen, in interface pixels. More — the scale is lower. */
	public static final int MARGIN_TOP = 4;

	/**
	 * How many times to resize the entire scale. 1.0 = as drawn (164 pixels wide),
	 * 0.75 = smaller (now), 0.5 = very small, 1.5 = larger. Fractional numbers make the picture a little softer.
	 */
	public static final float SCALE = 0.75f;

	/** Whether to show the moon icon on the left. */
	public static final boolean SHOW_MOON = true;

	/** Whether to show the skull on the right. */
	public static final boolean SHOW_STAGE_ICON = true;

	/** Do not draw the scale while the debug screen (F3) is open: it occupies just the left top. */
	public static final boolean HIDE_WHEN_DEBUG_OPEN = true;

	/**
	 * Default key that hides and shows the scale. 11 = H.
	 * The player changes it in Options → Controls → Evolving Threats; that choice is remembered.
	 * This number is used for a new options file, and when that row is reset.
	 * Letters run A=4 … Z=29 (J=13, K=14). 0 = no key until the player binds one.
	 */
	public static final int DEFAULT_TOGGLE_KEY = InputConstants.KEY_H;

	// ========================================================================

	/** false after the player hides the scale. Starts visible every time the game launches. */
	private static boolean shown = true;

	private static KeyMapping toggleKey;

	// Image sizes. Must match the files (tools/prepare_threat_textures.py writes them at startup).
	private static final int ICON_SIZE = 16;
	private static final int ICON_GAP = 2;
	private static final int FRAME_WIDTH = 128;
	private static final int FRAME_HEIGHT = 12;
	/** The window inside the frame, where the padding is drawn: padding on the left, top and size. */
	private static final int WINDOW_X = 5;
	private static final int WINDOW_Y = 4;
	private static final int WINDOW_WIDTH = 118;
	private static final int WINDOW_HEIGHT = 4;

	private static final Identifier FRAME = EvolvingThreats.id("threat/bar_frame");
	private static final Identifier FILL = EvolvingThreats.id("threat/bar_fill");

	private ThreatHud() {
	}

	/** Called once at client startup. */
	public static void register() {
		// The server reports from what point the world counts the days (otherwise, in the old world, the scale would show the entire age of the world).
		ClientPlayNetworking.registerGlobalReceiver(ThreatStartPayload.TYPE,
				(payload, context) -> ThreatScale.setStartClock(payload.startClock()));
		if (!ENABLED) {
			return;
		}
		// Category name on screen: key.category.evolving-threats.threat
		KeyMapping.Category category = KeyMapping.Category.register(EvolvingThreats.id("threat"));
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.evolving-threats.toggle_threat_hud",
				InputConstants.Type.KEYBOARD,
				DEFAULT_TOGGLE_KEY,
				category));
		ClientTickEvents.END_CLIENT_TICK.register(ThreatHud::onClientTick);
		HudElementRegistry.addLast(EvolvingThreats.id("threat_bar"), ThreatHud::draw);
	}

	/** One press hides the scale, the next press shows it. Does nothing while a menu is open. */
	private static void onClientTick(Minecraft client) {
		while (toggleKey.consumeClick()) {
			shown = !shown;
		}
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		Level level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		if (!shown) {
			return;
		}
		if (HIDE_WHEN_DEBUG_OPEN && minecraft.getDebugOverlay().showDebugScreen()) {
			return;
		}

		graphics.pose().pushMatrix();
		graphics.pose().translate((float) MARGIN_LEFT, (float) MARGIN_TOP);
		graphics.pose().scale(SCALE, SCALE);

		int x = 0;
		if (SHOW_MOON) {
			sprite(graphics, EvolvingThreats.id("threat/moon_" + ThreatScale.moonPhase(level)), x, 0, ICON_SIZE, ICON_SIZE);
			x += ICON_SIZE + ICON_GAP;
		}

		int barY = (ICON_SIZE - FRAME_HEIGHT) / 2;
		int filled = Math.round(WINDOW_WIDTH * ThreatScale.progress(level));
		if (filled > 0) {
			// Partial picture: we take only the left part of the strip so that the color goes from green to red on the scale.
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FILL,
					WINDOW_WIDTH, WINDOW_HEIGHT, 0, 0,
					x + WINDOW_X, barY + WINDOW_Y, filled, WINDOW_HEIGHT);
		}
		sprite(graphics, FRAME, x, barY, FRAME_WIDTH, FRAME_HEIGHT);
		x += FRAME_WIDTH + ICON_GAP;

		if (SHOW_STAGE_ICON) {
			sprite(graphics, EvolvingThreats.id("threat/stage_" + stageIndex(level)), x, 0, ICON_SIZE, ICON_SIZE);
		}

		graphics.pose().popMatrix();
	}

	/** Skull icon number is 0–3 for the current day. Threshold Days — ThreatTable.STAGE_ICON_FROM_DAY. */
	private static int stageIndex(Level level) {
		int day = ThreatScale.day(level);
		int stage = 0;
		for (int i = 0; i < ThreatTable.STAGE_ICON_FROM_DAY.length; i++) {
			if (day >= ThreatTable.STAGE_ICON_FROM_DAY[i]) {
				stage = i;
			}
		}
		return stage;
	}

	private static void sprite(GuiGraphicsExtractor graphics, Identifier id, int x, int y, int width, int height) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, width, height);
	}
}
