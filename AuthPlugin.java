
package com.blocktopia.auth;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;

public class AuthPlugin extends JavaPlugin implements Listener {


    private static final String API_URL = "https://blacktopia-backend-production.up.railway.app";


    private final Set<UUID> loggedIn = new HashSet<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("register").setExecutor(new RegisterCommand());
        getCommand("login").setExecutor(new LoginCommand());
        getLogger().info("BlocktopiaAuth enabled!");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "Please /login or /register first!");
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) {
            if (e.getFrom().getBlockX() != e.getTo().getBlockX() ||
                e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        p.sendMessage(ChatColor.AQUA + "=== Welcome to Blocktopia! ===");
        p.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.WHITE + "/register <password> <confirmPassword>");
        p.sendMessage(ChatColor.YELLOW + "Or " + ChatColor.WHITE + "/login <password>");
    }

    class RegisterCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;

            if (args.length < 2) {
                p.sendMessage(ChatColor.RED + "Usage: /register <password> <confirmPassword>");
                return true;
            }

            if (!args[0].equals(args[1])) {
                p.sendMessage(ChatColor.RED + "Passwords don't match!");
                return true;
            }

            String password = args[0];
            if (password.length() < 6) {
                p.sendMessage(ChatColor.RED + "Password must be at least 6 characters!");
                return true;
            }

            getServer().getScheduler().runTaskAsynchronously(AuthPlugin.this, () -> {
                try {
                    String body = "{\"username\":\"" + p.getName() + "\","
                                + "\"password\":\"" + password + "\"}";

                    String response = sendPost(API_URL + "/api/register", body);

                    if (response.contains("success")) {
                        loggedIn.add(p.getUniqueId());
                        getServer().getScheduler().runTask(AuthPlugin.this, () -> {
                            p.sendMessage(ChatColor.GREEN + "✅ Registered! You are now logged in.");
                            p.sendMessage(ChatColor.AQUA + "Login on our website with these credentials!");
                        });
                    } else if (response.contains("Already registered")) {
                        getServer().getScheduler().runTask(AuthPlugin.this, () ->
                            p.sendMessage(ChatColor.RED + "Already registered! Use /login instead."));
                    } else {
                        getServer().getScheduler().runTask(AuthPlugin.this, () ->
                            p.sendMessage(ChatColor.RED + "Registration failed. Try again later."));
                    }
                } catch (Exception ex) {
                    getServer().getScheduler().runTask(AuthPlugin.this, () ->
                        p.sendMessage(ChatColor.RED + "Error connecting to auth server!"));
                    ex.printStackTrace();
                }
            });

            return true;
        }
    }

    class LoginCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;

            if (loggedIn.contains(p.getUniqueId())) {
                p.sendMessage(ChatColor.YELLOW + "You are already logged in!");
                return true;
            }

            if (args.length < 1) {
                p.sendMessage(ChatColor.RED + "Usage: /login <password>");
                return true;
            }

            String password = args[0];

            getServer().getScheduler().runTaskAsynchronously(AuthPlugin.this, () -> {
                try {
                    String body = "{\"username\":\"" + p.getName() + "\","
                                + "\"password\":\"" + password + "\"}";

                    String response = sendPost(API_URL + "/api/login", body);

                    if (response.contains("success")) {
                        loggedIn.add(p.getUniqueId());
                        getServer().getScheduler().runTask(AuthPlugin.this, () ->
                            p.sendMessage(ChatColor.GREEN + "✅ Logged in! Welcome back, " + p.getName() + "!"));
                    } else if (response.contains("Not registered")) {
                        getServer().getScheduler().runTask(AuthPlugin.this, () ->
                            p.sendMessage(ChatColor.RED + "Not registered! Use /register first."));
                    } else {
                        getServer().getScheduler().runTask(AuthPlugin.this, () ->
                            p.sendMessage(ChatColor.RED + "Wrong password!"));
                    }
                } catch (Exception ex) {
                    getServer().getScheduler().runTask(AuthPlugin.this, () ->
                        p.sendMessage(ChatColor.RED + "Error connecting to auth server!"));
                    ex.printStackTrace();
                }
            });

            return true;
        }
    }

    private String sendPost(String urlStr, String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        var stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();

        try (Scanner sc = new Scanner(stream, StandardCharsets.UTF_8)) {
            return sc.useDelimiter("\\A").next();
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("BlocktopiaAuth disabled.");
    }
}
