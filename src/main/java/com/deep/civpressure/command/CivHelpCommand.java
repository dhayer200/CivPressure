package com.deep.civpressure.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class CivHelpCommand implements CommandExecutor {
    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Welcome to CivPressure!");
        sender.sendMessage(ChatColor.GRAY
                + "A survival world where the land itself pushes you to explore, settle,");
        sender.sendMessage(ChatColor.GRAY
                + "trade, and band together. Here's what's different from normal Minecraft:");
        sender.sendMessage("");

        sender.sendMessage(ChatColor.YELLOW + "Regions & ores: " + ChatColor.GRAY
                + "each type of land is rich in one ore and poor in others.");
        sender.sendMessage(ChatColor.GRAY
                + "Mountains favor emeralds, snowy lands favor diamonds, deserts gold, and so on.");
        sender.sendMessage(ChatColor.GRAY
                + "Every ore still spawns everywhere \u2014 just rarer away from its home region, so");
        sender.sendMessage(ChatColor.GRAY
                + "no world is ever missing a resource. Travel or trade to round out your supply.");
        sender.sendMessage(ChatColor.AQUA + "  Tip: " + ChatColor.GRAY + "run "
                + ChatColor.WHITE + "/civ ore info" + ChatColor.GRAY
                + " to see what's common where you stand.");
        sender.sendMessage("");

        sender.sendMessage(ChatColor.YELLOW + "Biome Compass: " + ChatColor.GRAY
                + "craft one to hunt down a specific region.");
        sender.sendMessage(ChatColor.GRAY
                + "Recipe: a compass surrounded by 8 saplings (any kind). Right-click it to pick");
        sender.sendMessage(ChatColor.GRAY
                + "a region, and it points to the nearest one \u2014 great for finding ores or land.");
        sender.sendMessage("");

        sender.sendMessage(ChatColor.YELLOW + "Nightfall: " + ChatColor.GRAY
                + "the first night is vanilla. Over many days, nights get harder.");
        sender.sendMessage(ChatColor.GRAY
                + "More spider jockeys, more hostiles, and sieges on beds and villages get more");
        sender.sendMessage(ChatColor.GRAY
                + "likely. After day 100, phantoms can appear and a giant may join a siege.");
        sender.sendMessage(ChatColor.GRAY
                + "It peaks around day 200. Light settlements, sleep carefully, stay together.");
        sender.sendMessage("");

        sender.sendMessage(ChatColor.YELLOW + "Seasons: " + ChatColor.GRAY
                + "regions drift between normal, drought, and wet spells.");
        sender.sendMessage(ChatColor.GRAY
                + "Droughts dry out farmland and slow crops; wet spells help them. Stockpile food.");
        sender.sendMessage(ChatColor.AQUA + "  Tip: " + ChatColor.GRAY + "check it with "
                + ChatColor.WHITE + "/civ season status" + ChatColor.GRAY + ".");
        sender.sendMessage("");

        sender.sendMessage(ChatColor.YELLOW + "Tougher survival: " + ChatColor.GRAY
                + "falls hurt more, natural healing is slower, and gear wears out faster.");
        sender.sendMessage(ChatColor.GRAY
                + "Sleeping safely through the night gives you a short regeneration boost.");
        sender.sendMessage("");

        sender.sendMessage(ChatColor.GOLD + "Handy commands:");
        sender.sendMessage(ChatColor.WHITE + "/civ ore info" + ChatColor.GRAY
                + " - what ores are common where you're standing");
        sender.sendMessage(ChatColor.WHITE + "/civ season status" + ChatColor.GRAY
                + " - the current season for every region");
        sender.sendMessage(ChatColor.WHITE + "/civ status" + ChatColor.GRAY
                + " - which features are turned on");
        sender.sendMessage(ChatColor.WHITE + "/civhelp" + ChatColor.GRAY
                + " - show this guide again");

        if (sender.hasPermission("civ.reload") || sender.hasPermission("civ.giant.admin")) {
            sender.sendMessage(ChatColor.DARK_GRAY + "Admins: run " + ChatColor.GRAY + "/civ"
                    + ChatColor.DARK_GRAY + " for management commands.");
        }
        return true;
    }
}
