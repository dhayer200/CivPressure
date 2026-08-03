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
                + "Every region is good at some things and bad at others, so you'll want to");
        sender.sendMessage(ChatColor.GRAY
                + "explore, settle, trade, and team up to get everything you need.");
        sender.sendMessage("");

        sender.sendMessage(ChatColor.YELLOW + "Ores: " + ChatColor.GRAY
                + "Each region is rich in one ore and poorer in the rest.");
        sender.sendMessage(ChatColor.GRAY
                + "You can still find every ore anywhere \u2014 it's just rarer outside its home");
        sender.sendMessage(ChatColor.GRAY
                + "region. No world is ever \"missing\" a resource. Travel or trade for the rest.");
        sender.sendMessage(ChatColor.AQUA + "  Tip: " + ChatColor.GRAY
                + "stand somewhere and run " + ChatColor.WHITE + "/civ ore info"
                + ChatColor.GRAY + " to see what's common here.");
        sender.sendMessage("");

        sender.sendMessage(ChatColor.YELLOW + "Seasons: " + ChatColor.GRAY
                + "Regions drift between normal, drought, and wet spells.");
        sender.sendMessage(ChatColor.GRAY
                + "Droughts slow crops; wet spells help them. Plan your farms and stockpile.");
        sender.sendMessage(ChatColor.AQUA + "  Tip: " + ChatColor.GRAY
                + "check the current weather with " + ChatColor.WHITE + "/civ season status"
                + ChatColor.GRAY + ".");
        sender.sendMessage("");

        sender.sendMessage(ChatColor.YELLOW + "Giants: " + ChatColor.GRAY
                + "Rare, dangerous roaming events \u2014 not normal mobs.");
        sender.sendMessage(ChatColor.GRAY
                + "They hit hard and carry good loot. Bring friends; fight or flee.");
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
