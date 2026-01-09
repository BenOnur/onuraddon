package com.example.addon.hud;

import com.example.addon.Addon;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;

public class HudExample {
    public static final HudGroup GROUP = new HudGroup("Addon");

    public static void init() {
        // Register HUD elements
        Hud hud = Hud.get();
        hud.register(DonutPriceTrackerHud.INFO);
    }
}
