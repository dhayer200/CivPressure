package com.deep.civpressure;

import com.deep.civpressure.biome.BiomeGroupRegistry;
import com.deep.civpressure.command.CivCommand;
import com.deep.civpressure.command.CivHelpCommand;
import com.deep.civpressure.compass.BiomeCompassListener;
import com.deep.civpressure.compass.BiomeCompassManager;
import com.deep.civpressure.config.ConfigManager;
import com.deep.civpressure.giant.GiantEventListener;
import com.deep.civpressure.giant.GiantEventManager;
import com.deep.civpressure.mob.MobBuffListener;
import com.deep.civpressure.mob.MobBuffManager;
import com.deep.civpressure.nightfall.NightfallListener;
import com.deep.civpressure.nightfall.NightfallManager;
import com.deep.civpressure.ore.OrePopulateListener;
import com.deep.civpressure.ore.OreRates;
import com.deep.civpressure.ore.OreRedistributor;
import com.deep.civpressure.ore.OreScanner;
import com.deep.civpressure.ore.ProcessedChunkStore;
import com.deep.civpressure.season.SeasonGrowthListener;
import com.deep.civpressure.season.SeasonFarmlandListener;
import com.deep.civpressure.season.SeasonManager;
import com.deep.civpressure.durability.DurabilityPressureListener;
import com.deep.civpressure.survival.SurvivalEventListener;
import com.deep.civpressure.wildlife.MountainPolarBearListener;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CivPressurePlugin extends JavaPlugin {
    private ConfigManager configManager;
    private BiomeGroupRegistry biomeGroupRegistry;
    private OreRates oreRates;
    private OreScanner oreScanner;
    private ProcessedChunkStore processedChunkStore;
    private OreRedistributor oreRedistributor;
    private SeasonManager seasonManager;
    private MobBuffManager mobBuffManager;
    private BiomeCompassManager biomeCompassManager;
    private GiantEventManager giantEventManager;
    private NightfallManager nightfallManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        configManager.load();
        getLogger().info("Config loaded");

        biomeGroupRegistry = new BiomeGroupRegistry();
        oreRates = new OreRates(configManager);
        oreScanner = new OreScanner();
        processedChunkStore = new ProcessedChunkStore(this);
        processedChunkStore.load();
        oreRedistributor = new OreRedistributor(biomeGroupRegistry, oreRates, processedChunkStore);
        seasonManager = new SeasonManager(this, configManager, biomeGroupRegistry);
        mobBuffManager = new MobBuffManager(this, configManager);
        biomeCompassManager = new BiomeCompassManager(this, configManager, biomeGroupRegistry);
        giantEventManager = new GiantEventManager(this, configManager, biomeGroupRegistry);
        nightfallManager = new NightfallManager(this, configManager, giantEventManager);

        registerCommands();
        getServer().getPluginManager().registerEvents(
                new OrePopulateListener(this, configManager, oreRedistributor),
                this);
        getServer().getPluginManager().registerEvents(
                new SeasonGrowthListener(configManager, biomeGroupRegistry, seasonManager),
                this);
        getServer().getPluginManager().registerEvents(
                new SeasonFarmlandListener(configManager, biomeGroupRegistry, seasonManager),
                this);
        getServer().getPluginManager().registerEvents(
                new SurvivalEventListener(configManager),
                this);
        getServer().getPluginManager().registerEvents(
                new DurabilityPressureListener(configManager),
                this);
        getServer().getPluginManager().registerEvents(
                new MobBuffListener(configManager, mobBuffManager),
                this);
        getServer().getPluginManager().registerEvents(
                new BiomeCompassListener(configManager, biomeCompassManager),
                this);
        getServer().getPluginManager().registerEvents(
                new GiantEventListener(giantEventManager),
                this);
        getServer().getPluginManager().registerEvents(
                new MountainPolarBearListener(configManager),
                this);
        getServer().getPluginManager().registerEvents(
                new NightfallListener(nightfallManager),
                this);
        seasonManager.start();
        mobBuffManager.start();
        biomeCompassManager.start();
        giantEventManager.start();
        nightfallManager.start();
        getLogger().info("CivPressure enabled");
    }

    @Override
    public void onDisable() {
        if (seasonManager != null) {
            seasonManager.stop();
        }
        if (biomeCompassManager != null) {
            biomeCompassManager.stop();
        }
        if (giantEventManager != null) {
            giantEventManager.stop();
        }
        if (nightfallManager != null) {
            nightfallManager.stop();
        }
    }

    public ConfigManager getConfigManager() {
        return Objects.requireNonNull(configManager, "ConfigManager is not loaded");
    }

    public BiomeGroupRegistry getBiomeGroupRegistry() {
        return Objects.requireNonNull(biomeGroupRegistry, "BiomeGroupRegistry is not loaded");
    }

    public OreRates getOreRates() {
        return Objects.requireNonNull(oreRates, "OreRates is not loaded");
    }

    public OreScanner getOreScanner() {
        return Objects.requireNonNull(oreScanner, "OreScanner is not loaded");
    }

    public ProcessedChunkStore getProcessedChunkStore() {
        return Objects.requireNonNull(processedChunkStore, "ProcessedChunkStore is not loaded");
    }

    public OreRedistributor getOreRedistributor() {
        return Objects.requireNonNull(oreRedistributor, "OreRedistributor is not loaded");
    }

    public SeasonManager getSeasonManager() {
        return Objects.requireNonNull(seasonManager, "SeasonManager is not loaded");
    }

    public BiomeCompassManager getBiomeCompassManager() {
        return Objects.requireNonNull(biomeCompassManager, "BiomeCompassManager is not loaded");
    }

    public GiantEventManager getGiantEventManager() {
        return Objects.requireNonNull(giantEventManager, "GiantEventManager is not loaded");
    }

    public NightfallManager getNightfallManager() {
        return Objects.requireNonNull(nightfallManager, "NightfallManager is not loaded");
    }

    public void reloadConfiguration() {
        configManager.load();
        seasonManager.refreshConfiguration();
        mobBuffManager.reload();
        biomeCompassManager.reload();
        giantEventManager.reload();
        nightfallManager.reload();
    }

    private void registerCommands() {
        PluginCommand civCommand = getCommand("civ");
        if (civCommand == null) {
            getLogger().log(Level.SEVERE, "Command /civ is missing from plugin.yml");
        } else {
            CivCommand executor = new CivCommand(this, configManager);
            civCommand.setExecutor(executor);
            civCommand.setTabCompleter(executor);
            getLogger().info("Registered command: /civ");
        }

        PluginCommand civHelpCommand = getCommand("civhelp");
        if (civHelpCommand == null) {
            getLogger().log(Level.SEVERE, "Command /civhelp is missing from plugin.yml");
        } else {
            civHelpCommand.setExecutor(new CivHelpCommand());
            getLogger().info("Registered command: /civhelp");
        }
    }
}
