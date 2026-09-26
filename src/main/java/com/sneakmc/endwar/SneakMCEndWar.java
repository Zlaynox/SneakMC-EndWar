package com.sneakmc.endwar;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SneakMCEndWar extends JavaPlugin implements Listener {

    private static final String GUI_TITLE = "§5§l⚔ END WAR CONTROL";
    private static final String RTP_CONFIG = "plugins/RtpGUI/config.yml";

    private boolean endUnlocked = false;
    private boolean dragonOn = false;
    private boolean countdownRunning = false;

    private final Pattern endRtpPattern =
            Pattern.compile("(?m)^(\\s*end-rtp:\\s*)(true|false)(\\s*)$");

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        endUnlocked = readEndRtp();

        // Keep Dragon status synchronized with the saved End state
        dragonOn = endUnlocked;

        getLogger().info("SneakMC End War enabled.");
    }

    @Override
    public boolean onCommand(
            org.bukkit.command.CommandSender sender,
            org.bukkit.command.Command command,
            String label,
            String[] args
    ) {

        if (!sender.isOp()) {
            sender.sendMessage(
                    "§c§l✖ §cYou must be OP to use End War controls."
            );
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                openMenu(player);
            } else {
                sender.sendMessage(
                        "§eUse: /endwar <unlock|lock|status>"
                );
            }
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "unlock":
                startUnlock();
                break;

            case "lock":
                lockEnd();
                break;

            case "status":
                sendStatus(sender);
                break;

            default:
                sender.sendMessage("§eUsage: /endwar");
                sender.sendMessage(
                        "§7or: /endwar <unlock|lock|status>"
                );
                break;
        }

        return true;
    }

    // =========================================================
    // GUI
    // =========================================================

    private void openMenu(Player player) {

        Inventory inventory = Bukkit.createInventory(
                null,
                27,
                GUI_TITLE
        );

        ItemStack filler = item(
                Material.PURPLE_STAINED_GLASS_PANE,
                " "
        );

        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, filler);
        }

        ItemStack unlock = item(
                Material.DRAGON_EGG,
                "§5§l🔓 UNLOCK END",
                " ",
                "§7Open The End and start End War.",
                " ",
                "§fClick to begin the §d3-second countdown§f.",
                " "
        );

        ItemStack status = item(
                Material.BOOK,
                "§e§l📖 END STATUS",
                " ",
                "§7Check the current End War state.",
                " ",
                "§7End Access: " + accessStatus(),
                "§7Ender Dragon: " + dragonStatus(),
                "§7End RTP: " + rtpStatus(),
                " "
        );

        ItemStack lock = item(
                Material.BARRIER,
                "§c§l🔒 LOCK END",
                " ",
                "§7Seal The End and disable",
                "§7End RTP and the Ender Dragon.",
                " ",
                "§cClick to lock The End."
        );

        inventory.setItem(11, unlock);
        inventory.setItem(13, status);
        inventory.setItem(15, lock);

        player.openInventory(inventory);
    }

    private ItemStack item(
            Material material,
            String name,
            String... lore
    ) {

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }

        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {

        if (!event.getView().getTitle().equals(GUI_TITLE)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!player.isOp()) {
            player.closeInventory();
            player.sendMessage(
                    "§c§l✖ §cYou must be OP to use this menu."
            );
            return;
        }

        int slot = event.getRawSlot();

        if (slot == 11) {
            player.closeInventory();
            startUnlock();
        }

        else if (slot == 13) {
            player.closeInventory();
            sendStatus(player);
        }

        else if (slot == 15) {
            player.closeInventory();
            lockEnd();
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {

        if (event.getView().getTitle().equals(GUI_TITLE)) {
            event.setCancelled(true);
        }
    }

    // =========================================================
    // UNLOCK
    // =========================================================

    private void startUnlock() {

        if (countdownRunning) {
            Bukkit.broadcastMessage(
                    "§c⚠ §7End War countdown is already running."
            );
            return;
        }

        if (endUnlocked) {
            Bukkit.broadcastMessage(
                    "§e⚠ §7The End is already unlocked."
            );
            return;
        }

        countdownRunning = true;

        broadcast(
                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                "§5⚔ §d§lEND WAR INCOMING",
                "§7The End is about to open.",
                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );

        countdown(3);
    }

    private void countdown(int seconds) {

        if (seconds <= 0) {

            enableEnd();

            for (Player player : Bukkit.getOnlinePlayers()) {

                player.sendTitle(
                        "§5§l🔥 THE END IS OPEN! 🔥",
                        "§dEnd RTP is now available.",
                        5,
                        50,
                        10
                );

                player.playSound(
                        player.getLocation(),
                        Sound.ENTITY_ENDER_DRAGON_GROWL,
                        1.0f,
                        1.0f
                );
            }

            broadcast(
                    "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                    "§5🔥 §d§lTHE END IS NOW OPEN! 🔥",
                    "§7The End has been unlocked.",
                    "§fEnd RTP is now §aavailable§f.",
                    "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            );

            countdownRunning = false;
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {

            player.sendTitle(
                    "§5§l⚔ END WAR",
                    "§eOpening in §c§l" + seconds + "§e...",
                    0,
                    20,
                    0
            );

            player.playSound(
                    player.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_PLING,
                    1.0f,
                    1.0f
            );
        }

        Bukkit.broadcastMessage(
                "§5⚔ §d§lEND WAR §7→ §eOpening in §c§l"
                        + seconds + "§e..."
        );

        Bukkit.getScheduler().runTaskLater(
                this,
                () -> countdown(seconds - 1),
                20L
        );
    }

    // =========================================================
    // ENABLE END
    // =========================================================

    private void enableEnd() {

        endUnlocked = true;
        dragonOn = true;

        setEndRtp(true);

        runConsole("rtpgui reload");

        runConsole("end unlock");

        runConsole("end dragon on");
    }

    // =========================================================
    // LOCK END
    // =========================================================

    private void lockEnd() {

        if (countdownRunning) {
            Bukkit.broadcastMessage(
                    "§c⚠ §7You cannot lock The End during the opening countdown."
            );
            return;
        }

        endUnlocked = false;
        dragonOn = false;

        runConsole("end lock");

        runConsole("end dragon off");

        setEndRtp(false);

        runConsole("rtpgui reload");

        for (Player player : Bukkit.getOnlinePlayers()) {

            player.sendTitle(
                    "§c§l🔒 THE END IS SEALED",
                    "§7End War has ended.",
                    5,
                    50,
                    10
            );

            player.playSound(
                    player.getLocation(),
                    Sound.BLOCK_BEACON_DEACTIVATE,
                    1.0f,
                    1.0f
            );
        }

        broadcast(
                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                "§c🔒 §4§lTHE END HAS BEEN SEALED",
                "§7End access has been locked.",
                "§7End RTP has been disabled.",
                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
    }

    // =========================================================
    // STATUS
    // =========================================================

    private void sendStatus(
            org.bukkit.command.CommandSender sender
    ) {

        sender.sendMessage(
                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                "§5⚔ §d§lEND WAR STATUS"
        );

        sender.sendMessage("");

        sender.sendMessage(
                "§7End Access:     " + accessStatus()
        );

        sender.sendMessage(
                "§7Ender Dragon:   " + dragonStatus()
        );

        sender.sendMessage(
                "§7End RTP:        " + rtpStatus()
        );

        sender.sendMessage("");

        sender.sendMessage(
                endUnlocked
                        ? "§a✔ The End is currently OPEN."
                        : "§c✖ The End is currently LOCKED."
        );

        sender.sendMessage(
                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
    }

    private String accessStatus() {

        return endUnlocked
                ? "§a§lUNLOCKED"
                : "§c§lLOCKED";
    }

    private String dragonStatus() {

        return dragonOn
                ? "§a§lON"
                : "§c§lOFF";
    }

    private String rtpStatus() {

        return readEndRtp()
                ? "§a§lENABLED"
                : "§c§lDISABLED";
    }

    // =========================================================
    // RTPGUI CONFIG
    // =========================================================

    private boolean readEndRtp() {

        Path path = Paths.get(RTP_CONFIG);

        if (!Files.exists(path)) {
            return false;
        }

        try {

            String text = Files.readString(
                    path,
                    StandardCharsets.UTF_8
            );

            Matcher matcher = endRtpPattern.matcher(text);

            if (matcher.find()) {
                return Boolean.parseBoolean(
                        matcher.group(2)
                );
            }

        } catch (IOException exception) {

            getLogger().warning(
                    "Could not read RtpGUI config: "
                            + exception.getMessage()
            );
        }

        return false;
    }

    private void setEndRtp(boolean enabled) {

        Path path = Paths.get(RTP_CONFIG);

        if (!Files.exists(path)) {

            getLogger().severe(
                    "RtpGUI config was not found: "
                            + RTP_CONFIG
            );

            return;
        }

        try {

            String text = Files.readString(
                    path,
                    StandardCharsets.UTF_8
            );

            Matcher matcher = endRtpPattern.matcher(text);

            if (!matcher.find()) {

                getLogger().severe(
                        "Could not find 'end-rtp:' in RtpGUI config."
                );

                return;
            }

            String replacement =
                    matcher.group(1)
                            + enabled
                            + matcher.group(3);

            String updated =
                    matcher.replaceFirst(
                            Matcher.quoteReplacement(replacement)
                    );

            Files.writeString(
                    path,
                    updated,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );

            getLogger().info(
                    "RtpGUI end-rtp changed to "
                            + enabled
            );

        } catch (IOException exception) {

            getLogger().severe(
                    "Could not modify RtpGUI config: "
                            + exception.getMessage()
            );
        }
    }

    // =========================================================
    // UTILITIES
    // =========================================================

    private void runConsole(String command) {

        Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                command
        );
    }

    private void broadcast(String... messages) {

        for (String message : messages) {
            Bukkit.broadcastMessage(message);
        }
    }
            }
