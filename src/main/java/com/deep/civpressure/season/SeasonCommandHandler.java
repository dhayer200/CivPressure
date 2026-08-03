package com.deep.civpressure.season;

import com.deep.civpressure.biome.BiomeGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public final class SeasonCommandHandler {
    private static final List<String> GROUP_NAMES = buildGroupNames();

    private final SeasonManager seasonManager;

    public SeasonCommandHandler(SeasonManager seasonManager) {
        this.seasonManager = seasonManager;
    }

    public boolean handleSeason(CommandSender sender, String[] args) {
        // Season status is a read-only lookup any player can use.
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            showStatus(sender);
            return true;
        }
        if (!hasPermission(sender)) {
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "end" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /civ season end <group|all>");
                } else {
                    apply(sender, args[2], SeasonType.NORMAL);
                }
                yield true;
            }
            case "reload" -> {
                seasonManager.reload();
                sender.sendMessage(ChatColor.GREEN + "Season configuration and tasks reloaded.");
                yield true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Usage: /civ season <status|end|reload>");
                yield true;
            }
        };
    }

    public boolean handleForceSeason(CommandSender sender, String[] args, SeasonType type) {
        if (!hasPermission(sender)) {
            return true;
        }
        if (args.length < 2) {
            String command = type == SeasonType.DROUGHT ? "drought" : "wetseason";
            sender.sendMessage(ChatColor.RED + "Usage: /civ " + command + " <group|all>");
            return true;
        }

        apply(sender, args[1], type);
        return true;
    }

    public List<String> complete(String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("season")) {
            return prefixMatches(List.of("status", "end", "reload"), args[1]);
        }
        if (args.length == 3
                && args[0].equalsIgnoreCase("season")
                && args[1].equalsIgnoreCase("end")) {
            return prefixMatches(GROUP_NAMES, args[2]);
        }
        if (args.length == 2
                && (args[0].equalsIgnoreCase("drought")
                || args[0].equalsIgnoreCase("wetseason"))) {
            return prefixMatches(GROUP_NAMES, args[1]);
        }
        return List.of();
    }

    private void showStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "CivPressure seasons");
        for (BiomeGroup group : BiomeGroup.values()) {
            SeasonState state = seasonManager.getState(group);
            ChatColor color = switch (state.type()) {
                case NORMAL -> ChatColor.GREEN;
                case DROUGHT -> ChatColor.GOLD;
                case WET -> ChatColor.AQUA;
            };
            String duration = state.type() == SeasonType.NORMAL
                    ? ""
                    : ChatColor.GRAY + " (day " + state.daysActive() + ")";
            sender.sendMessage(ChatColor.GRAY + "- " + seasonManager.getRegionDisplayName(group) + ": "
                    + color + state.type().displayName() + duration);
        }
    }

    private void apply(CommandSender sender, String groupName, SeasonType type) {
        if (groupName.equalsIgnoreCase("all")) {
            for (BiomeGroup group : BiomeGroup.values()) {
                seasonManager.setSeason(group, type, true);
            }
            sender.sendMessage(ChatColor.GREEN + "Set all biome groups to " + type.displayName() + ".");
            return;
        }

        BiomeGroup.parse(groupName).ifPresentOrElse(
                group -> {
                    seasonManager.setSeason(group, type, true);
                    sender.sendMessage(ChatColor.GREEN + "Set " + seasonManager.getRegionDisplayName(group)
                            + " to " + type.displayName() + ".");
                },
                () -> sender.sendMessage(ChatColor.RED + "Unknown biome group: " + groupName));
    }

    private boolean hasPermission(CommandSender sender) {
        if (sender.hasPermission("civ.season")) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + "You do not have permission to manage seasons.");
        return false;
    }

    private static List<String> buildGroupNames() {
        List<String> names = new ArrayList<>();
        names.add("all");
        for (BiomeGroup group : BiomeGroup.values()) {
            names.add(group.name().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(names);
    }

    private List<String> prefixMatches(List<String> options, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.startsWith(prefix))
                .toList();
    }
}
