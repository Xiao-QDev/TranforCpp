package com.github.tranforcpp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;

import com.github.tranforcpp.compiler.CppCompiler;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class ProcessManager implements Listener {

    private final TranforCPlusPlus plugin;
    private Process process;
    private BufferedReader inputReader;
    private BufferedWriter outputWriter;
    private final Gson gson = new Gson();
    private final ConcurrentLinkedQueue<JsonObject> messageQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong messageCounter = new AtomicLong(0);
    private volatile Thread readerThread;
    private volatile Thread senderThread;
    private final MiniMessage miniMessageInstance;
    private final MemoryOptimizer memoryOptimizer;

    private static final int MAX_QUEUE_SIZE = 1000;

    public ProcessManager(TranforCPlusPlus plugin) {
        this.plugin = plugin;
        this.miniMessageInstance = MiniMessage.miniMessage();
        this.memoryOptimizer = plugin.getMemoryOptimizer();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void start() {
        try {
            File cppDir = new File(plugin.getDataFolder().getParentFile(), "C+plugins");
            if (!cppDir.exists()) {
                if (!cppDir.mkdirs()) {
                    plugin.getLogger().severe("Failed to create C+plugins directory: " + cppDir.getAbsolutePath());
                }
            }

            CppCompiler compiler = new CppCompiler();
            File executable = compiler.compile(cppDir);

            if (executable == null) {
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(executable.getAbsolutePath());
            pb.redirectErrorStream(true);
            process = pb.start();

            inputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            outputWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

            readerThread = new Thread(this::readMessages, "TranforC++-Reader");
            readerThread.setDaemon(true);
            readerThread.setPriority(Thread.NORM_PRIORITY);
            readerThread.start();

            senderThread = new Thread(this::sendMessages, "TranforC++-Sender");
            senderThread.setDaemon(true);
            senderThread.setPriority(Thread.NORM_PRIORITY);
            senderThread.start();

            running.set(true);
            plugin.getLogger().info("C++ plugin process started with performance optimizations");

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start C++ process: " + e.getMessage());
            plugin.getLogger().severe("Exception: " + e);
        }
    }

    private void readMessages() {
        try {
            String line;
            while (running.get() && (line = inputReader.readLine()) != null) {
                try {
                    JsonObject json = gson.fromJson(line, JsonObject.class);
                    if (json != null) {
                        handleCppMessage(json);
                        messageCounter.incrementAndGet();
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to parse C++ message: " + line);
                }
            }
        } catch (IOException e) {
            if (running.get() && process != null && process.isAlive()) {
                plugin.getLogger().severe("Error reading from C++ process: " + e.getMessage());
            }
        }
    }

    private void sendMessages() {
        try {
            while (running.get() && process != null && process.isAlive()) {
                JsonObject msg = messageQueue.poll();
                if (msg != null) {
                    outputWriter.write(gson.toJson(msg));
                    outputWriter.newLine();
                    if (messageQueue.size() < 10) {
                        outputWriter.flush();
                    }
                    messageCounter.incrementAndGet();
                } else {
                    synchronized (messageQueue) {
                        if (messageQueue.isEmpty()) {
                            messageQueue.wait(50);
                        }
                    }
                }
            }
            if (outputWriter != null) {
                outputWriter.flush();
            }
        } catch (InterruptedException | IOException e) {
            if (running.get()) {
                plugin.getLogger().warning("Error sending messages to C++: " + e.getMessage());
            }
        }
    }

    private void handleCppMessage(JsonObject json) {
        try {
            String action = json.get("action").getAsString();
            
            switch (action) {
                case "broadcast":
                    handleBroadcast(json);
                    break;
                case "sendMessage":
                    handlePrivateMessage(json);
                    break;
                case "console":
                    plugin.getLogger().info(json.get("message").getAsString());
                    break;
                default:
                    plugin.getLogger().warning("Unknown action: " + action);
                    break;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error handling C++ message: " + e.getMessage());
        }
    }
    
    private void handleBroadcast(JsonObject json) {
        try {
            Component broadcastMessage = miniMessageInstance.deserialize(json.get("message").getAsString());
            Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
            for (Player player : players) {
                player.sendMessage(broadcastMessage);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error broadcasting message: " + e.getMessage());
        }
    }
    
    private void handlePrivateMessage(JsonObject json) {
        try {
            String playerName = json.get("player").getAsString();
            Player player = Bukkit.getPlayerExact(playerName);
            if (player != null && player.isOnline()) {
                Component privateMessage = miniMessageInstance.deserialize(json.get("message").getAsString());
                player.sendMessage(privateMessage);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error sending private message: " + e.getMessage());
        }
    }

    public void sendEvent(String eventName, Object... args) {
        if (!running.get()) {
            return;
        }

        if (messageQueue.size() > MAX_QUEUE_SIZE) {
            plugin.getLogger().warning("消息队列已满，丢弃事件: " + eventName);
            return;
        }
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // 使用内存优化器的对象池
                String poolKey = "event_" + eventName + "_" + System.nanoTime();
                JsonObject json = new JsonObject();
                json.addProperty("event", eventName);
                
                com.google.gson.JsonArray argsArray = new com.google.gson.JsonArray();
                for (Object arg : args) {
                    argsArray.add(arg != null ? arg.toString() : "null");
                }
                json.add("args", argsArray);
                
                // 将对象添加到内存优化器的对象池
                if (memoryOptimizer != null) {
                    memoryOptimizer.addObjectToPool(poolKey, json);
                }
                
                synchronized (messageQueue) {
                    messageQueue.add(json);
                    messageQueue.notify();
                }

                dispatchToOtherPluginsAsync(eventName, args);
                
            } catch (Exception e) {
                plugin.getLogger().warning("Error sending event " + eventName + ": " + e.getMessage());
            }
        });
    }
    
    /**
     * 异步分发事件到其他插件
     * @param eventName 事件名称
     * @param args 事件参数
     */
    private void dispatchToOtherPluginsAsync(String eventName, Object... args) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                org.bukkit.event.Event customEvent = createCustomEvent(eventName, args);
                Bukkit.getPluginManager().callEvent(customEvent);

                if (plugin.getMessagingManager() != null) {
                    plugin.getMessagingManager().broadcastEvent(eventName, args);
                }
                
            } catch (Exception e) {
                plugin.getLogger().warning("事件分发失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 根据事件名称创建对应的自定义事件对象
     * @param eventName 事件名称
     * @param args 事件参数
     * @return 自定义事件对象
     */
    private org.bukkit.event.Event createCustomEvent(String eventName, Object... args) {
        GenericTranforCEvent event = new GenericTranforCEvent(eventName, args);
        long timestamp = event.getTimestamp();
        int argCount = event.getArgCount();
        long creationTime = event.getCreationNanoTime();
        long processingTime = event.getProcessingTimeNanos();
        
        if (argCount > 0) {
            plugin.getLogger().fine("Created event '" + eventName + "' with " + argCount + " arguments at " + timestamp + " (creation: " + creationTime + " ns, processing: " + processingTime + " ns)");
        }
        return event;
    }
    
    /**
     * 通用的TranforC++事件类
     */
    public static class GenericTranforCEvent extends org.bukkit.event.Event {
        private static final org.bukkit.event.HandlerList handlers = new org.bukkit.event.HandlerList();
        private final String eventName;
        private final Object[] args;
        private final long timestamp;
        private final long creationNanoTime;
        
        public GenericTranforCEvent(String eventName, Object... args) {
            super(false);
            this.eventName = eventName;
            this.args = args;
            this.timestamp = System.currentTimeMillis();
            this.creationNanoTime = System.nanoTime();
        }
        
        public String getEventName() { return eventName; }
        public Object[] getArgs() {
            return args != null ? args.clone() : new Object[0]; 
        }
        public Object getArg(int index) {
            return index >= 0 && index < args.length ? args[index] : null;
        }
        public long getTimestamp() { return timestamp; }
        public int getArgCount() { return args.length; }
        public long getCreationNanoTime() { return creationNanoTime; }
        
        /**
         * 计算事件从创建到现在的处理时间（纳秒）
         * @return 处理时间纳秒数
         */
        public long getProcessingTimeNanos() {
            return System.nanoTime() - creationNanoTime;
        }
        
        @Override
        public org.bukkit.event.HandlerList getHandlers() { return handlers; }
        public static org.bukkit.event.HandlerList getHandlerList() { return handlers; }
    }
    

    


    public void stop() {
        running.set(false);

        messageQueue.clear();
        
        if (outputWriter != null) {
            try {
                outputWriter.write("{\"event\":\"shutdown\"}\n");
                outputWriter.flush();
            } catch (IOException e) {
                plugin.getLogger().warning("Error sending shutdown message: " + e.getMessage());
            } finally {
                try {
                    outputWriter.close();
                } catch (IOException ignored) {}
            }
        }

        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                plugin.getLogger().warning("Interrupted while waiting for process to terminate: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        if (readerThread != null && readerThread.isAlive()) {
            readerThread.interrupt();
        }
        if (senderThread != null && senderThread.isAlive()) {
            senderThread.interrupt();
        }

        readerThread = null;
        senderThread = null;
        inputReader = null;
        outputWriter = null;
        process = null;
        
        plugin.getLogger().info("ProcessManager stopped. Messages processed: " + messageCounter.get());
        messageCounter.set(0);
    }

    public void restart() {
        stop();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            plugin.getLogger().warning("Interrupted during restart delay: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
        start();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        sendEvent("PlayerJoin", event.getPlayer().getName());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sendEvent("PlayerQuit", event.getPlayer().getName());
    }



    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        sendEvent("BlockBreak", event.getPlayer().getName(), event.getBlock().getType().name());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        sendEvent("BlockPlace", event.getPlayer().getName(), event.getBlock().getType().name());
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        sendEvent("EntityDamage", event.getEntity().getName(), String.valueOf(event.getDamage()));
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        sendEvent("EntityDeath", event.getEntity().getName());
    }

    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        String deathMsg = "Player died";
        if (event.deathMessage() != null) {
            deathMsg = String.valueOf(event.deathMessage());
        }
        sendEvent("PlayerDeath", event.getEntity().getName(), deathMsg);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        sendEvent("InventoryClick", event.getWhoClicked().getName(), String.valueOf(event.getSlot()), event.getCurrentItem() != null ? event.getCurrentItem().getType().name() : "AIR");
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        sendEvent("InventoryOpen", event.getPlayer().getName());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        sendEvent("InventoryClose", event.getPlayer().getName());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        sendEvent("PlayerMove", event.getPlayer().getName());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        sendEvent("PlayerRespawn", event.getPlayer().getName());
    }

    @EventHandler
    public void onBlockIgnite(BlockIgniteEvent event) {
        String playerName = event.getPlayer() != null ? event.getPlayer().getName() : "null";
        sendEvent("BlockIgnite", playerName, event.getBlock().getType().name());
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        sendEvent("EntitySpawn", event.getEntityType().name());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        String itemName = event.getItem() != null ? event.getItem().getType().name() : "null";
        sendEvent("PlayerInteract", event.getPlayer().getName(), event.getAction().name(), itemName);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        sendEvent("PlayerDropItem", event.getPlayer().getName(), event.getItemDrop().getItemStack().getType().name());
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            sendEvent("PlayerPickupItem", player.getName(), event.getItem().getItemStack().getType().name());
        }
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        sendEvent("ServerCommand", event.getSender().getName(), event.getCommand());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        sendEvent("WorldLoad", event.getWorld().getName());
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        sendEvent("WeatherChange", event.getWorld().getName(), String.valueOf(event.toWeatherState()));
    }

    @EventHandler
    public void onHangingBreak(HangingBreakEvent event) {
        sendEvent("HangingBreak", event.getEntity().getType().name(), event.getCause().name());
    }
}
