package com.deep.civpressure.command;

import com.deep.civpressure.CivPressurePlugin;
import com.deep.civpressure.biome.BiomeGroup;
import com.deep.civpressure.biome.BiomeGroupRegistry;
import com.deep.civpressure.config.ConfigManager;
import com.deep.civpressure.giant.GiantEventManager;
import com.deep.civpressure.nightfall.NightfallManager;
import com.deep.civpressure.ore.OreRates;
import com.deep.civpressure.ore.OreRedistributionResult;
import com.deep.civpressure.ore.OreRedistributor;
import com.deep.civpressure.ore.OreScanResult;
import com.deep.civpressure.ore.OreScanner;
import com.deep.civpressure.ore.OreType;
import com.deep.civpressure.ore.ProcessedChunkStore;
import com.deep.civpressure.season.SeasonCommandHandler;
import com.deep.civpressure.season.SeasonType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CivCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT_SUBCOMMANDS = List.of(
            "status",
            "reload",
            "ore",
            "season",
            "drought",
            "wetseason",
            "durability",
            "compass",
            "giant",
            "nightfall");
    private static final List<String> ORE_SUBCOMMANDS = List.of(
            "info",
            "chunk",
            "radius",
            "debug",
            "processchunk",
            "unprocesschunk",
            "clearprocessed",
            "process",
            "unprocess");

    private final CivPressurePlugin plugin;
    private final ConfigManager configManager;
    private final BiomeGroupRegistry biomeGroupRegistry;
    private final OreRates oreRates;
    private final OreScanner oreScanner;
    private final ProcessedChunkStore processedChunkStore;
    private final OreRedistributor oreRedistributor;
    private final SeasonCommandHandler seasonCommandHandler;
    private final GiantEventManager giantEventManager;
    private final NightfallManager nightfallManager;
    private final Set<UUID> activeProcessJobs = new HashSet<>();

    public CivCommand(CivPressurePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        biomeGroupRegistry = plugin.getBiomeGroupRegistry();
        oreRates = plugin.getOreRates();
        oreScanner = plugin.getOreScanner();
        processedChunkStore = plugin.getProcessedChunkStore();
        oreRedistributor = plugin.getOreRedistributor();
        seasonCommandHandler = new SeasonCommandHandler(plugin.getSeasonManager());
        giantEventManager = plugin.getGiantEventManager();
        nightfallManager = plugin.getNightfallManager();
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> showStatus(sender);
            case "reload" -> reload(sender);
            case "ore" -> handleOre(sender, args);
            case "season" -> seasonCommandHandler.handleSeason(sender, args);
            case "drought" -> seasonCommandHandler.handleForceSeason(sender, args, SeasonType.DROUGHT);
            case "wetseason" -> seasonCommandHandler.handleForceSeason(sender, args, SeasonType.WET);
            case "durability" -> handleDurability(sender, args);
            case "compass" -> handleCompass(sender, args);
            case "giant" -> handleGiant(sender, args);
            case "nightfall" -> handleNightfall(sender, args);
            default -> {
                sendUsage(sender, label);
                yield true;
            }
        };
    }

    private boolean showStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "CivPressure " + plugin.getPluginMeta().getVersion());
        sender.sendMessage(ChatColor.YELLOW + "Module status:");
        for (Map.Entry<String, Boolean> module : configManager.getModuleStates().entrySet()) {
            ChatColor color = module.getValue() ? ChatColor.GREEN : ChatColor.RED;
            String state = module.getValue() ? "enabled" : "disabled";
            sender.sendMessage(ChatColor.GRAY + "- " + module.getKey() + ": " + color + state);
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("civ.reload")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to reload CivPressure.");
            return true;
        }

        plugin.reloadConfiguration();
        sender.sendMessage(ChatColor.GREEN + "CivPressure configuration reloaded.");
        return true;
    }

    private boolean handleOre(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendOreUsage(sender);
            return true;
        }

        // "info" is a harmless, read-only lookup any player can use to learn the world.
        if (args[1].equalsIgnoreCase("info")) {
            return withPlayer(sender, this::showOreInfo);
        }
        if (!sender.hasPermission("civ.ore")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use ore commands.");
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "info" -> withPlayer(sender, this::showOreInfo);
            case "chunk" -> withPlayer(sender, this::scanCurrentChunk);
            case "radius" -> withPlayer(sender, player -> scanRadius(player, args));
            case "debug" -> withPlayer(sender, this::showOreDebug);
            case "processchunk" -> withPlayer(sender, player -> processCurrentChunk(player, args));
            case "unprocesschunk" -> withPlayer(sender, this::unprocessCurrentChunk);
            case "clearprocessed" -> clearProcessed(sender, args);
            case "process" -> withPlayer(sender, player -> processRadius(player, args));
            case "unprocess" -> withPlayer(sender, player -> unprocessRadius(player, args));
            default -> {
                sendOreUsage(sender);
                yield true;
            }
        };
    }

    private boolean handleCompass(CommandSender sender, String[] args) {
        if (!sender.hasPermission("civ.compass.give")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to give biome compasses.");
            return true;
        }
        if (args.length != 2 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage(ChatColor.RED + "Usage: /civ compass give");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
            return true;
        }

        plugin.getBiomeCompassManager().giveCompass(player);
        return true;
    }

    private boolean handleDurability(CommandSender sender, String[] args) {
        if (!sender.hasPermission("civ.durability")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to view durability settings.");
            return true;
        }
        if (args.length != 2 || !args[1].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.RED + "Usage: /civ durability status");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "Durability pressure");
        sender.sendMessage(ChatColor.GRAY + "Module: "
                + stateColor(configManager.isModuleEnabled("durability-pressure"))
                + (configManager.isModuleEnabled("durability-pressure") ? "enabled" : "disabled"));
        sender.sendMessage(ChatColor.GRAY + "Tools multiplier: " + ChatColor.WHITE
                + configManager.getDouble("durability-pressure.tools-multiplier", 1.5));
        sender.sendMessage(ChatColor.GRAY + "Weapons multiplier: " + ChatColor.WHITE
                + configManager.getDouble("durability-pressure.weapons-multiplier", 1.35));
        sender.sendMessage(ChatColor.GRAY + "Armor multiplier: " + ChatColor.WHITE
                + configManager.getDouble("durability-pressure.armor-multiplier", 1.35));
        sender.sendMessage(ChatColor.GRAY + "Shields multiplier: " + ChatColor.WHITE
                + configManager.getDouble("durability-pressure.shields-multiplier", 1.5));
        sender.sendMessage(ChatColor.GRAY + "Bows/crossbows multiplier: " + ChatColor.WHITE
                + configManager.getDouble("durability-pressure.bows-crossbows-multiplier", 1.25));
        sender.sendMessage(ChatColor.GRAY + "Utility multiplier: " + ChatColor.WHITE
                + configManager.getDouble("durability-pressure.utility-multiplier", 1.25));
        sender.sendMessage(ChatColor.GRAY + "Mending included: " + stateColor(
                configManager.getBoolean("durability-pressure.include-mending-items", true))
                + configManager.getBoolean("durability-pressure.include-mending-items", true));
        sender.sendMessage(ChatColor.GRAY + "Unbreaking respected: " + stateColor(
                configManager.getBoolean("durability-pressure.respect-unbreaking", true))
                + configManager.getBoolean("durability-pressure.respect-unbreaking", true));
        sender.sendMessage(ChatColor.GRAY + "Max extra damage per event: " + ChatColor.WHITE
                + configManager.getInt("durability-pressure.max-extra-damage-per-event", 4));
        return true;
    }

    private boolean handleNightfall(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendNightfallUsage(sender);
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> showNightfallStatus(sender);
            case "reset" -> setNightfallDay(sender, args, 0L, true);
            case "set" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /civ nightfall set <day> [world|all]");
                    yield true;
                }
                Long day = parseNightfallDay(args[2]);
                if (day == null) {
                    sender.sendMessage(ChatColor.RED + "Nightfall day must be a whole number of 0 or more.");
                    yield true;
                }
                yield setNightfallDay(sender, args, day, false);
            }
            default -> {
                sendNightfallUsage(sender);
                yield true;
            }
        };
    }

    private boolean showNightfallStatus(CommandSender sender) {
        if (!sender.hasPermission("civ.nightfall") && !sender.hasPermission("civ.nightfall.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to view Nightfall status.");
            return true;
        }
        boolean enabled = configManager.isModuleEnabled("nightfall");
        sender.sendMessage(ChatColor.GOLD + "Nightfall");
        sender.sendMessage(ChatColor.GRAY + "Module: "
                + stateColor(enabled) + (enabled ? "enabled" : "disabled"));
        sender.sendMessage(ChatColor.GRAY + "Danger cap: " + ChatColor.WHITE
                + nightfallManager.capNights() + " nights");
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) {
                continue;
            }
            long nights = nightfallManager.nightsSurvived(world);
            int percent = (int) Math.round(nightfallManager.progress(world) * 100.0);
            sender.sendMessage(ChatColor.GRAY + "- " + world.getName() + ": night "
                    + ChatColor.WHITE + nights + ChatColor.GRAY + " (" + percent + "% escalated, "
                    + "mob health x" + round2(nightfallManager.currentHealthMultiplier(world))
                    + ", damage x" + round2(nightfallManager.currentDamageMultiplier(world)) + ")");
        }
        return true;
    }

    private boolean setNightfallDay(CommandSender sender, String[] args, long day, boolean reset) {
        if (!sender.hasPermission("civ.nightfall.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to change the Nightfall clock.");
            return true;
        }

        List<World> worlds = resolveNightfallWorlds(sender, args, reset ? 2 : 3);
        if (worlds == null) {
            return true;
        }
        for (World world : worlds) {
            long applied = reset ? nightfallManager.resetNight(world) : nightfallManager.setNight(world, day);
            sender.sendMessage(ChatColor.GREEN + "Nightfall in " + world.getName()
                    + " is now day " + applied + ".");
        }
        return true;
    }

    private @Nullable List<World> resolveNightfallWorlds(CommandSender sender, String[] args, int worldArgIndex) {
        if (args.length > worldArgIndex && args[worldArgIndex].equalsIgnoreCase("all")) {
            List<World> worlds = new ArrayList<>();
            for (World world : plugin.getServer().getWorlds()) {
                if (world.getEnvironment() == World.Environment.NORMAL) {
                    worlds.add(world);
                }
            }
            if (worlds.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "No overworlds are loaded.");
                return null;
            }
            return worlds;
        }

        World world;
        if (args.length > worldArgIndex) {
            world = plugin.getServer().getWorld(args[worldArgIndex]);
            if (world == null) {
                sender.sendMessage(ChatColor.RED + "Unknown loaded world: " + args[worldArgIndex]);
                return null;
            }
        } else if (sender instanceof Player player) {
            world = player.getWorld();
        } else {
            sender.sendMessage(ChatColor.RED + "Specify a world or 'all' from the console.");
            return null;
        }
        return List.of(world);
    }

    private @Nullable Long parseNightfallDay(String raw) {
        try {
            long day = Long.parseLong(raw);
            if (day < 0L) {
                return null;
            }
            return day;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void sendNightfallUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "CivPressure nightfall commands:");
        if (sender.hasPermission("civ.nightfall") || sender.hasPermission("civ.nightfall.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/civ nightfall status"
                    + ChatColor.GRAY + " - Show how escalated the nights are.");
        }
        if (sender.hasPermission("civ.nightfall.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/civ nightfall reset [world|all]"
                    + ChatColor.GRAY + " - Restart Nightfall at day 0.");
            sender.sendMessage(ChatColor.YELLOW + "/civ nightfall set <day> [world|all]"
                    + ChatColor.GRAY + " - Jump Nightfall to any day.");
        }
        if (!sender.hasPermission("civ.nightfall") && !sender.hasPermission("civ.nightfall.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use Nightfall commands.");
        }
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private boolean handleGiant(CommandSender sender, String[] args) {
        if (!sender.hasPermission("civ.giant.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to manage giant events.");
            return true;
        }
        if (args.length < 2) {
            sendGiantUsage(sender);
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> showGiantStatus(sender);
            case "spawn" -> withPlayer(sender, this::spawnGiant);
            case "clear" -> clearGiants(sender, args);
            default -> {
                sendGiantUsage(sender);
                yield true;
            }
        };
    }

    private boolean showGiantStatus(CommandSender sender) {
        boolean enabled = configManager.isModuleEnabled("giant-events");
        sender.sendMessage(ChatColor.GOLD + "Giant events");
        sender.sendMessage(ChatColor.GRAY + "Module: "
                + stateColor(enabled) + (enabled ? "enabled" : "disabled"));
        sender.sendMessage(ChatColor.GRAY + "Active loaded event giants: "
                + ChatColor.WHITE + giantEventManager.countAll());
        sender.sendMessage(ChatColor.GRAY + "Maximum per world: "
                + ChatColor.WHITE + giantEventManager.getMaxGiantsPerWorld());
        for (World world : plugin.getServer().getWorlds()) {
            int count = giantEventManager.count(world);
            if (count > 0) {
                sender.sendMessage(ChatColor.GRAY + "- " + world.getName() + ": "
                        + ChatColor.WHITE + count);
            }
        }
        return true;
    }

    private boolean spawnGiant(Player player) {
        if (!configManager.isModuleEnabled("giant-events")) {
            player.sendMessage(ChatColor.RED + "The giant-events module is disabled.");
            return true;
        }
        org.bukkit.entity.Giant giant = giantEventManager.spawnForCommand(player);
        if (giant == null) {
            player.sendMessage(ChatColor.RED
                    + "No giant was spawned. Check the world, per-world cap, and nearby open terrain.");
            return true;
        }
        player.sendMessage(ChatColor.GREEN + "Spawned an event giant at "
                + giant.getLocation().getBlockX() + ", "
                + giant.getLocation().getBlockY() + ", "
                + giant.getLocation().getBlockZ() + ".");
        return true;
    }

    private boolean clearGiants(CommandSender sender, String[] args) {
        if (args.length >= 3 && args[2].equalsIgnoreCase("all")) {
            int removed = giantEventManager.clearAll();
            sender.sendMessage(ChatColor.GREEN + "Removed " + removed + " event giants.");
            return true;
        }

        World world;
        if (args.length >= 3) {
            world = plugin.getServer().getWorld(args[2]);
            if (world == null) {
                sender.sendMessage(ChatColor.RED + "Unknown loaded world: " + args[2]);
                return true;
            }
        } else if (sender instanceof Player player) {
            world = player.getWorld();
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /civ giant clear <world|all>");
            return true;
        }

        int removed = giantEventManager.clear(world);
        sender.sendMessage(ChatColor.GREEN + "Removed " + removed
                + " event giants from " + world.getName() + ".");
        return true;
    }

    private boolean showOreInfo(Player player) {
        Location location = player.getLocation();
        Biome biome = location.getWorld().getBiome(
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
        BiomeGroup group = biomeGroupRegistry.resolve(biome, location.getBlockY());

        player.sendMessage(ChatColor.GOLD + "You are in: " + ChatColor.WHITE
                + configManager.getBiomeGroupDisplayName(group));
        player.sendMessage(ChatColor.GRAY + "Ores here (compared to a normal world):");
        for (OreType oreType : OreType.values()) {
            int rate = oreRates.getRate(group, oreType);
            player.sendMessage(ChatColor.GRAY + "- " + oreType.displayName() + ": "
                    + abundanceColor(rate) + abundanceLabel(rate)
                    + ChatColor.DARK_GRAY + " (" + rate + "%)");
        }
        player.sendMessage(ChatColor.DARK_GRAY + "Rarer ores still spawn here \u2014 travel or trade for more.");
        return true;
    }

    private String abundanceLabel(int rate) {
        if (rate >= 120) {
            return "Abundant";
        }
        if (rate >= 80) {
            return "Common";
        }
        if (rate >= 40) {
            return "Scarce";
        }
        return "Rare";
    }

    private ChatColor abundanceColor(int rate) {
        if (rate >= 120) {
            return ChatColor.GREEN;
        }
        if (rate >= 80) {
            return ChatColor.YELLOW;
        }
        if (rate >= 40) {
            return ChatColor.GOLD;
        }
        return ChatColor.RED;
    }

    private boolean showOreDebug(Player player) {
        Location location = player.getLocation();
        Chunk chunk = player.getChunk();
        Biome biome = location.getWorld().getBiome(
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
        BiomeGroup group = biomeGroupRegistry.resolve(biome, location.getBlockY());
        boolean processed = processedChunkStore.isProcessed(chunk);

        player.sendMessage(ChatColor.GOLD + "Ore redistribution debug");
        player.sendMessage(ChatColor.GRAY + "World: " + ChatColor.WHITE + player.getWorld().getName());
        player.sendMessage(ChatColor.GRAY + "Chunk: " + ChatColor.WHITE + chunk.getX() + ", " + chunk.getZ());
        player.sendMessage(ChatColor.GRAY + "Processed: " + stateColor(processed) + processed);
        player.sendMessage(ChatColor.GRAY + "Biome: " + ChatColor.WHITE + biome.getKey().asString());
        player.sendMessage(ChatColor.GRAY + "Resolved region: " + ChatColor.WHITE
                + configManager.getBiomeGroupDisplayName(group));
        return true;
    }

    private boolean processCurrentChunk(Player player, String[] args) {
        if (!player.hasPermission("civ.ore.process")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to process chunks.");
            return true;
        }

        if (args.length >= 3 && !args[2].equalsIgnoreCase("force")) {
            player.sendMessage(ChatColor.RED + "Usage: /civ ore processchunk [force]");
            return true;
        }
        boolean force = args.length >= 3;
        if (force) {
            sendForceWarning(player);
        } else {
            sendManualProcessingWarning(player);
        }

        Chunk chunk = player.getChunk();
        OreRedistributionResult result = oreRedistributor.process(chunk, force);
        sendProcessResult(player, chunk, result);
        return true;
    }

    private boolean unprocessCurrentChunk(Player player) {
        if (!player.hasPermission("civ.ore.process")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to change processed records.");
            return true;
        }

        Chunk chunk = player.getChunk();
        boolean removed = processedChunkStore.unmark(player.getWorld(), chunk.getX(), chunk.getZ());
        if (removed) {
            player.sendMessage(ChatColor.GREEN + "Removed the processed flag for chunk "
                    + chunk.getX() + ", " + chunk.getZ() + ". Blocks were not changed.");
        } else {
            player.sendMessage(ChatColor.YELLOW + "The current chunk was not marked as processed.");
        }
        return true;
    }

    private boolean clearProcessed(CommandSender sender, String[] args) {
        if (!sender.hasPermission("civ.ore.process")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to clear processed records.");
            return true;
        }

        if (args.length < 3 || args[2].equalsIgnoreCase("all")) {
            int removed = processedChunkStore.clearAll();
            sender.sendMessage(ChatColor.GREEN + "Cleared " + removed
                    + " processed chunk records across all worlds. Blocks were not changed.");
            return true;
        }

        World world = plugin.getServer().getWorld(args[2]);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "Unknown loaded world: " + args[2]);
            return true;
        }

        int removed = processedChunkStore.clear(world);
        sender.sendMessage(ChatColor.GREEN + "Cleared " + removed + " processed chunk records for "
                + world.getName() + ". Blocks were not changed.");
        return true;
    }

    private boolean processRadius(Player player, String[] args) {
        if (!player.hasPermission("civ.ore.process")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to process chunks.");
            return true;
        }
        if (args.length < 4 || !args[2].equalsIgnoreCase("radius")) {
            player.sendMessage(ChatColor.RED + "Usage: /civ ore process radius <radius> [force]");
            return true;
        }
        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) {
            player.sendMessage(ChatColor.RED + "Ore redistribution only processes Overworld chunks.");
            return true;
        }

        Integer radius = parseRadius(player, args[3], configManager.getMaxProcessRadius());
        if (radius == null) {
            return true;
        }
        if (args.length >= 5 && !args[4].equalsIgnoreCase("force")) {
            player.sendMessage(ChatColor.RED + "Usage: /civ ore process radius <radius> [force]");
            return true;
        }
        boolean force = args.length >= 5;
        if (!activeProcessJobs.add(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You already have an ore processing job running.");
            return true;
        }

        if (force) {
            sendForceWarning(player);
        } else {
            sendManualProcessingWarning(player);
        }

        Chunk center = player.getChunk();
        World world = player.getWorld();
        Deque<ChunkCoordinate> chunks = chunkCoordinates(center, radius);
        int totalChunks = chunks.size();
        player.sendMessage(ChatColor.YELLOW + "Processing " + totalChunks + " chunks, one per tick...");

        new BukkitRunnable() {
            private int processed;
            private int skipped;
            private int failures;
            private int removed;
            private int added;

            @Override
            public void run() {
                ChunkCoordinate coordinate = chunks.pollFirst();
                if (coordinate == null) {
                    activeProcessJobs.remove(player.getUniqueId());
                    player.sendMessage(ChatColor.GOLD + "Ore processing complete: "
                            + ChatColor.GREEN + processed + " processed, "
                            + ChatColor.YELLOW + skipped + " skipped, "
                            + ChatColor.RED + failures + " failed.");
                    player.sendMessage(ChatColor.GRAY + "Blocks removed: " + removed
                            + ", blocks added: " + added + ".");
                    cancel();
                    return;
                }

                oreRedistributor.suppressAutomaticProcessing(world, coordinate.x(), coordinate.z());
                try {
                    Chunk chunk = world.getChunkAt(coordinate.x(), coordinate.z());
                    OreRedistributionResult result = oreRedistributor.process(chunk, force);
                    removed += result.removed();
                    added += result.added();
                    switch (result.status()) {
                        case PROCESSED -> processed++;
                        case ALREADY_PROCESSED, UNSUPPORTED_WORLD -> skipped++;
                        case PERSISTENCE_FAILED -> failures++;
                    }
                } catch (RuntimeException exception) {
                    failures++;
                    plugin.getLogger().log(
                            Level.SEVERE,
                            "Could not process chunk " + coordinate.x() + "," + coordinate.z()
                                    + " in world " + world.getName(),
                            exception);
                } finally {
                    oreRedistributor.restoreAutomaticProcessing(world, coordinate.x(), coordinate.z());
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private boolean unprocessRadius(Player player, String[] args) {
        if (!player.hasPermission("civ.ore.process")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to change processed records.");
            return true;
        }
        if (args.length < 4 || !args[2].equalsIgnoreCase("radius")) {
            player.sendMessage(ChatColor.RED + "Usage: /civ ore unprocess radius <radius>");
            return true;
        }

        Integer radius = parseRadius(player, args[3], configManager.getMaxProcessRadius());
        if (radius == null) {
            return true;
        }

        Chunk center = player.getChunk();
        int removed = processedChunkStore.unmarkRadius(
                player.getWorld(),
                center.getX(),
                center.getZ(),
                radius);
        player.sendMessage(ChatColor.GREEN + "Removed " + removed
                + " processed chunk records. Blocks were not changed.");
        return true;
    }

    private Integer parseRadius(CommandSender sender, String value, int maxRadius) {
        int radius;
        try {
            radius = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            sender.sendMessage(ChatColor.RED + "Radius must be a whole number.");
            return null;
        }

        if (radius < 0 || radius > maxRadius) {
            sender.sendMessage(ChatColor.RED + "Radius must be between 0 and " + maxRadius + ".");
            return null;
        }
        return radius;
    }

    private Deque<ChunkCoordinate> chunkCoordinates(Chunk center, int radius) {
        Deque<ChunkCoordinate> chunks = new ArrayDeque<>();
        for (int chunkX = center.getX() - radius; chunkX <= center.getX() + radius; chunkX++) {
            for (int chunkZ = center.getZ() - radius; chunkZ <= center.getZ() + radius; chunkZ++) {
                chunks.addLast(new ChunkCoordinate(chunkX, chunkZ));
            }
        }
        return chunks;
    }

    private void sendProcessResult(Player player, Chunk chunk, OreRedistributionResult result) {
        switch (result.status()) {
            case PROCESSED -> player.sendMessage(ChatColor.GREEN + "Processed chunk "
                    + chunk.getX() + ", " + chunk.getZ() + ": removed " + result.removed()
                    + ", added " + result.added() + ".");
            case ALREADY_PROCESSED -> player.sendMessage(ChatColor.YELLOW
                    + "That chunk is already processed. Use 'force' only if you accept ore distortion.");
            case UNSUPPORTED_WORLD -> player.sendMessage(ChatColor.RED
                    + "Ore redistribution only processes Overworld chunks.");
            case PERSISTENCE_FAILED -> player.sendMessage(ChatColor.RED
                    + "Blocks changed, but the processed flag could not be saved. Check the server log.");
        }
    }

    private void sendForceWarning(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Warning: force-processing can distort ore counts because removed ores "
                + "cannot be restored and added ores may be processed again.");
    }

    private void sendManualProcessingWarning(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Manual processing should only be used on fresh, unmodified chunks; "
                + "existing player-placed ores cannot be distinguished.");
    }

    private ChatColor stateColor(boolean state) {
        return state ? ChatColor.GREEN : ChatColor.RED;
    }

    private boolean withPlayer(CommandSender sender, Function<Player, Boolean> operation) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This ore command must be run by a player.");
            return true;
        }
        return operation.apply(player);
    }

    private boolean scanCurrentChunk(Player player) {
        if (!player.hasPermission("civ.ore.scan")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to scan ores.");
            return true;
        }

        Chunk chunk = player.getChunk();
        player.sendMessage(ChatColor.YELLOW + "Scanning chunk " + chunk.getX() + ", " + chunk.getZ() + "...");
        ChunkSnapshot snapshot = chunk.getChunkSnapshot();
        int minHeight = chunk.getWorld().getMinHeight();
        int maxHeight = chunk.getWorld().getMaxHeight();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            OreScanResult result = oreScanner.scan(snapshot, minHeight, maxHeight);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    sendScanResult(player, result, 1);
                }
            });
        });
        return true;
    }

    private boolean scanRadius(Player player, String[] args) {
        if (!player.hasPermission("civ.ore.scan")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to scan ores.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /civ ore radius <radius>");
            return true;
        }

        int radius;
        try {
            radius = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            player.sendMessage(ChatColor.RED + "Radius must be a whole number.");
            return true;
        }

        int maxRadius = configManager.getMaxScanRadius();
        if (radius < 0 || radius > maxRadius) {
            player.sendMessage(ChatColor.RED + "Radius must be between 0 and " + maxRadius + ".");
            return true;
        }

        Chunk center = player.getChunk();
        World world = player.getWorld();
        Deque<ChunkCoordinate> chunks = new ArrayDeque<>();
        for (int chunkX = center.getX() - radius; chunkX <= center.getX() + radius; chunkX++) {
            for (int chunkZ = center.getZ() - radius; chunkZ <= center.getZ() + radius; chunkZ++) {
                chunks.addLast(new ChunkCoordinate(chunkX, chunkZ));
            }
        }

        int totalChunks = chunks.size();
        player.sendMessage(ChatColor.YELLOW + "Scanning " + totalChunks
                + " loaded chunks over multiple ticks...");
        new BukkitRunnable() {
            private final OreScanResult total = new OreScanResult();
            private int scannedChunks;
            private int skippedChunks;
            private boolean scanning;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (scanning) {
                    return;
                }

                ChunkCoordinate coordinate = chunks.pollFirst();
                if (coordinate == null) {
                    sendScanResult(player, total, scannedChunks);
                    if (skippedChunks > 0) {
                        player.sendMessage(ChatColor.GRAY + "Skipped " + skippedChunks
                                + " unloaded chunks to avoid loading or generating terrain.");
                    }
                    cancel();
                    return;
                }

                if (!world.isChunkLoaded(coordinate.x(), coordinate.z())) {
                    skippedChunks++;
                    return;
                }

                Chunk chunk = world.getChunkAt(coordinate.x(), coordinate.z());
                ChunkSnapshot snapshot = chunk.getChunkSnapshot();
                int minHeight = world.getMinHeight();
                int maxHeight = world.getMaxHeight();
                scanning = true;
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    OreScanResult result = oreScanner.scan(snapshot, minHeight, maxHeight);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        total.merge(result);
                        scannedChunks++;
                        scanning = false;
                    });
                });
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private void sendScanResult(CommandSender sender, OreScanResult result, int chunksScanned) {
        sender.sendMessage(ChatColor.GOLD + "Ore scan complete (" + chunksScanned + " chunks)");
        for (OreType oreType : OreType.values()) {
            sender.sendMessage(ChatColor.GRAY + "- " + oreType.displayName() + ": "
                    + ChatColor.WHITE + result.getCount(oreType));
        }
    }

    private void sendOreUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "CivPressure ore commands:");
        sender.sendMessage(ChatColor.YELLOW + "/civ ore info"
                + ChatColor.GRAY + " - Show biome group and ore rates.");
        sender.sendMessage(ChatColor.YELLOW + "/civ ore chunk"
                + ChatColor.GRAY + " - Count ores in the current chunk.");
        sender.sendMessage(ChatColor.YELLOW + "/civ ore radius <radius>"
                + ChatColor.GRAY + " - Count ores in nearby chunks.");
        sender.sendMessage(ChatColor.YELLOW + "/civ ore debug"
                + ChatColor.GRAY + " - Show current chunk processing state.");
        if (sender.hasPermission("civ.ore.process")) {
            sender.sendMessage(ChatColor.YELLOW + "/civ ore processchunk [force]"
                    + ChatColor.GRAY + " - Process the current chunk.");
            sender.sendMessage(ChatColor.YELLOW + "/civ ore unprocesschunk"
                    + ChatColor.GRAY + " - Remove the current chunk's processed flag.");
            sender.sendMessage(ChatColor.YELLOW + "/civ ore process radius <radius> [force]"
                    + ChatColor.GRAY + " - Process nearby chunks over multiple ticks.");
            sender.sendMessage(ChatColor.YELLOW + "/civ ore unprocess radius <radius>"
                    + ChatColor.GRAY + " - Remove nearby processed flags.");
            sender.sendMessage(ChatColor.YELLOW + "/civ ore clearprocessed [world|all]"
                    + ChatColor.GRAY + " - Clear processed records without changing blocks.");
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "CivPressure commands:");
        sender.sendMessage(ChatColor.YELLOW + "/civhelp"
                + ChatColor.GRAY + " - New player? Start here.");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " ore info"
                + ChatColor.GRAY + " - What ores are common where you're standing.");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " season status"
                + ChatColor.GRAY + " - The current season for every region.");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " status"
                + ChatColor.GRAY + " - Show which features are enabled.");
        if (sender.hasPermission("civ.ore")) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " ore"
                    + ChatColor.GRAY + " - Ore scanning and processing commands.");
        }
        if (sender.hasPermission("civ.reload")) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload"
                    + ChatColor.GRAY + " - Reload the configuration.");
        }
        if (sender.hasPermission("civ.season")) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " drought|wetseason <group|all>"
                    + ChatColor.GRAY + " - Force a season for testing.");
        }
        if (sender.hasPermission("civ.durability")) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " durability status"
                    + ChatColor.GRAY + " - Show durability pressure settings.");
        }
        if (sender.hasPermission("civ.compass.give")) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " compass give"
                    + ChatColor.GRAY + " - Give yourself a Biome Compass.");
        }
        if (sender.hasPermission("civ.giant.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " giant"
                    + ChatColor.GRAY + " - Manage rare giant events.");
        }
        if (sender.hasPermission("civ.nightfall") || sender.hasPermission("civ.nightfall.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " nightfall status"
                    + ChatColor.GRAY + " - Show how escalated the nights are.");
        }
        if (sender.hasPermission("civ.nightfall.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " nightfall reset [world|all]"
                    + ChatColor.GRAY + " - Restart Nightfall at day 0.");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " nightfall set <day> [world|all]"
                    + ChatColor.GRAY + " - Jump Nightfall to any day.");
        }
        sender.sendMessage(ChatColor.YELLOW + "/civhelp"
                + ChatColor.GRAY + " - Show the player help overview.");
    }

    private void sendGiantUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "CivPressure giant commands:");
        sender.sendMessage(ChatColor.YELLOW + "/civ giant status"
                + ChatColor.GRAY + " - Show event giant state and counts.");
        sender.sendMessage(ChatColor.YELLOW + "/civ giant spawn"
                + ChatColor.GRAY + " - Spawn an event giant near you.");
        sender.sendMessage(ChatColor.YELLOW + "/civ giant clear [world|all]"
                + ChatColor.GRAY + " - Remove loaded event giants.");
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            return filterCompletions(ROOT_SUBCOMMANDS, args[0], sender);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ore")) {
            if (sender.hasPermission("civ.ore")) {
                return filterCompletions(ORE_SUBCOMMANDS, args[1], sender);
            }
            return prefixMatches(List.of("info"), args[1]);
        }
        if (sender.hasPermission("civ.season")) {
            List<String> seasonCompletions = seasonCommandHandler.complete(args);
            if (!seasonCompletions.isEmpty()) {
                return seasonCompletions;
            }
        }
        if (args.length == 2
                && args[0].equalsIgnoreCase("compass")
                && sender.hasPermission("civ.compass.give")) {
            return prefixMatches(List.of("give"), args[1]);
        }
        if (args.length == 2
                && args[0].equalsIgnoreCase("durability")
                && sender.hasPermission("civ.durability")) {
            return prefixMatches(List.of("status"), args[1]);
        }
        if (args.length == 2
                && args[0].equalsIgnoreCase("giant")
                && sender.hasPermission("civ.giant.admin")) {
            return prefixMatches(List.of("status", "spawn", "clear"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("nightfall")) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("civ.nightfall") || sender.hasPermission("civ.nightfall.admin")) {
                options.add("status");
            }
            if (sender.hasPermission("civ.nightfall.admin")) {
                options.add("reset");
                options.add("set");
            }
            return prefixMatches(options, args[1]);
        }
        if (args.length == 3
                && args[0].equalsIgnoreCase("nightfall")
                && args[1].equalsIgnoreCase("set")
                && sender.hasPermission("civ.nightfall.admin")) {
            return prefixMatches(List.of("0", "15", "25", "50"), args[2]);
        }
        if (args.length == 3
                && args[0].equalsIgnoreCase("nightfall")
                && args[1].equalsIgnoreCase("reset")
                && sender.hasPermission("civ.nightfall.admin")) {
            return prefixMatches(nightfallWorldCompletions(), args[2]);
        }
        if (args.length == 4
                && args[0].equalsIgnoreCase("nightfall")
                && args[1].equalsIgnoreCase("set")
                && sender.hasPermission("civ.nightfall.admin")) {
            return prefixMatches(nightfallWorldCompletions(), args[3]);
        }
        if (args.length == 3
                && args[0].equalsIgnoreCase("giant")
                && args[1].equalsIgnoreCase("clear")
                && sender.hasPermission("civ.giant.admin")) {
            List<String> worlds = new ArrayList<>();
            worlds.add("all");
            for (World world : plugin.getServer().getWorlds()) {
                worlds.add(world.getName());
            }
            return prefixMatches(worlds, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ore")) {
            if (args[1].equalsIgnoreCase("processchunk")) {
                return prefixMatches(List.of("force"), args[2]);
            }
            if (args[1].equalsIgnoreCase("process") || args[1].equalsIgnoreCase("unprocess")) {
                return prefixMatches(List.of("radius"), args[2]);
            }
            if (args[1].equalsIgnoreCase("clearprocessed")) {
                List<String> worlds = new ArrayList<>();
                worlds.add("all");
                for (World world : plugin.getServer().getWorlds()) {
                    worlds.add(world.getName());
                }
                return prefixMatches(worlds, args[2]);
            }
        }
        if (args.length == 5
                && args[0].equalsIgnoreCase("ore")
                && args[1].equalsIgnoreCase("process")
                && args[2].equalsIgnoreCase("radius")) {
            return prefixMatches(List.of("force"), args[4]);
        }
        return List.of();
    }

    private List<String> filterCompletions(List<String> options, String input, CommandSender sender) {
        String prefix = input.toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();
        for (String subcommand : options) {
            if (subcommand.equals("reload") && !sender.hasPermission("civ.reload")) {
                continue;
            }
            if ((subcommand.equals("drought") || subcommand.equals("wetseason"))
                    && !sender.hasPermission("civ.season")) {
                continue;
            }
            if (subcommand.equals("compass") && !sender.hasPermission("civ.compass.give")) {
                continue;
            }
            if (subcommand.equals("durability") && !sender.hasPermission("civ.durability")) {
                continue;
            }
            if (subcommand.equals("giant") && !sender.hasPermission("civ.giant.admin")) {
                continue;
            }
            if (subcommand.equals("nightfall")
                    && !sender.hasPermission("civ.nightfall")
                    && !sender.hasPermission("civ.nightfall.admin")) {
                continue;
            }
            if ((subcommand.equals("chunk") || subcommand.equals("radius"))
                    && !sender.hasPermission("civ.ore.scan")) {
                continue;
            }
            if (isOreProcessingSubcommand(subcommand) && !sender.hasPermission("civ.ore.process")) {
                continue;
            }
            if (subcommand.startsWith(prefix)) {
                completions.add(subcommand);
            }
        }
        return completions;
    }

    private List<String> nightfallWorldCompletions() {
        List<String> worlds = new ArrayList<>();
        worlds.add("all");
        for (World world : plugin.getServer().getWorlds()) {
            worlds.add(world.getName());
        }
        return worlds;
    }

    private List<String> prefixMatches(List<String> options, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }

    private boolean isOreProcessingSubcommand(String subcommand) {
        return subcommand.equals("processchunk")
                || subcommand.equals("unprocesschunk")
                || subcommand.equals("clearprocessed")
                || subcommand.equals("process")
                || subcommand.equals("unprocess");
    }

    private record ChunkCoordinate(int x, int z) {
    }
}
