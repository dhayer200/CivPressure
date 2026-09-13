package com.deep.civpressure.config;

import com.deep.civpressure.CivPressurePlugin;
import com.deep.civpressure.biome.BiomeGroup;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public final class ConfigManager implements ConfigIntReader {
    private static final String MODULES_PATH = "modules";

    private final CivPressurePlugin plugin;

    public ConfigManager(CivPressurePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
    }

    public boolean isModuleEnabled(String moduleName) {
        return plugin.getConfig().getBoolean(MODULES_PATH + "." + moduleName + ".enabled", false);
    }

    public Map<String, Boolean> getModuleStates() {
        Map<String, Boolean> states = new LinkedHashMap<>();
        states.put("Ore Redistribution", isModuleEnabled("ore-redistribution"));
        states.put("Seasons", isModuleEnabled("seasons"));
        states.put("Fall Damage", isModuleEnabled("fall-damage"));
        states.put("Slow Regen", isModuleEnabled("slow-regen"));
        states.put("Durability Pressure", isModuleEnabled("durability-pressure"));
        states.put("Biome Compass", isModuleEnabled("biome-compass"));
        states.put("Mob Buffs", isModuleEnabled("mob-buffs"));
        states.put("Giant Events", isModuleEnabled("giant-events"));
        states.put("Mountain Polar Bears", isModuleEnabled("mountain-polar-bears"));
        states.put("Nightfall", isModuleEnabled("nightfall"));
        return states;
    }

    public int getMaxScanRadius() {
        return Math.max(0, plugin.getConfig().getInt("ore-redistribution.max-scan-radius", 5));
    }

    public int getMaxProcessRadius() {
        return Math.max(0, plugin.getConfig().getInt("ore-redistribution.max-process-radius", 5));
    }

    public int getInt(String path, int defaultValue) {
        return plugin.getConfig().getInt(path, defaultValue);
    }

    public long getLong(String path, long defaultValue) {
        return plugin.getConfig().getLong(path, defaultValue);
    }

    public double getDouble(String path, double defaultValue) {
        return plugin.getConfig().getDouble(path, defaultValue);
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        return plugin.getConfig().getBoolean(path, defaultValue);
    }

    public String getString(String path, String defaultValue) {
        return plugin.getConfig().getString(path, defaultValue);
    }

    public List<String> getStringList(String path) {
        return plugin.getConfig().getStringList(path);
    }

    /** Reads a config section of {@code key: int} pairs (e.g. weighted tables). */
    public Map<String, Integer> getIntMap(String path) {
        Map<String, Integer> result = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            result.put(key, section.getInt(key));
        }
        return result;
    }

    public String getBiomeGroupDisplayName(BiomeGroup group) {
        String fallback = group.displayName();
        return getString(
                "biome-groups.display-names." + group.name().toLowerCase(Locale.ROOT),
                fallback);
    }
}
