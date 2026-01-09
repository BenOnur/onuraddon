package com.example.addon;

import com.example.addon.commands.CommandExample;
import com.example.addon.hud.HudExample;
import com.example.addon.modules.ModuleExample;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class Addon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final String VERSION = "1.0.0";

    @Override
    public void onInitialize() {
        LOG.info("Initializing DonutSMP Addon");

        // Register modules
        Modules.get().add(new ModuleExample());

        // Register commands
        Commands.get().add(new CommandExample());

        // Register HUD elements
        HudExample.init();

        LOG.info("DonutSMP Addon initialized successfully");
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(Categories.DONUT);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public String getVersion() {
        return VERSION;
    }
}
