package com.github.tranforcpp;

import com.github.tranforcpp.command.TranforCommand;
import com.github.tranforcpp.command.TranforTabCompleter;
import com.github.tranforcpp.listener.PluginListListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;

public class  TranforCPlusPlus extends JavaPlugin {

    private static TranforCPlusPlus instance;
    private ProcessManager processManager;
    private StartupManager startupManager;
    private com.github.tranforcpp.channel.PluginMessagingManager messagingManager;
    private MemoryOptimizer memoryOptimizer;

    @Override
    public void onEnable() {
        instance = this;
        
        try {
            registerTranforCommand();
        } catch (Exception e) {
            getLogger().severe("命令注册失败: " + e.getMessage());
        }
        
        startupManager = new StartupManager(this);
        startupManager.startAsync();
        
        // 初始化内存优化器
        memoryOptimizer = new MemoryOptimizer(this);
        memoryOptimizer.initialize();
        
        // 根据环境智能初始化消息通道
        com.github.tranforcpp.command.SmartEventDispatcher eventDispatcher = 
            new com.github.tranforcpp.command.SmartEventDispatcher(this);
        eventDispatcher.initialize();
        
        if (eventDispatcher.isUsingMessagingChannel()) {
            messagingManager = new com.github.tranforcpp.channel.PluginMessagingManager(this);
            messagingManager.initialize();
        }

        PluginListListener pluginListListener = new PluginListListener();
        getServer().getPluginManager().registerEvents(pluginListListener, this);
    }
    
    private void registerTranforCommand() throws Exception {
        Field commandMapField = getServer().getClass().getDeclaredField("commandMap");
        commandMapField.setAccessible(true);
        CommandMap commandMap = (CommandMap) commandMapField.get(getServer());
        
        Command tranforCommand = createTranforCommand();
        
        commandMap.register("tranforcpp", tranforCommand);
    }
    
    private Command createTranforCommand() {
        TranforCommand commandExecutor = new TranforCommand();
        TranforTabCompleter tabCompleter = new TranforTabCompleter();
        
        Command tranforCommand = new Command("tranforcpp") {
            @Override
            public boolean execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
                return commandExecutor.onCommand(sender, this, label, args);
            }
            
            @Override
            @org.jetbrains.annotations.NotNull
            public java.util.List<String> tabComplete(@org.jetbrains.annotations.NotNull org.bukkit.command.CommandSender sender, @org.jetbrains.annotations.NotNull String alias, @org.jetbrains.annotations.NotNull String[] args) throws IllegalArgumentException {
                java.util.List<String> result = tabCompleter.onTabComplete(sender, this, alias, args);
                return result != null ? result : new java.util.ArrayList<>();
            }
        };
        
        tranforCommand.setDescription("Manage TranforC++ plugin");
        tranforCommand.setUsage("/<command> [reload|version]");
        tranforCommand.setPermission("tranforcpp.use");
        tranforCommand.setAliases(java.util.Collections.singletonList("cpp"));
        
        return tranforCommand;
    }

    @Override
    public void onDisable() {
        if (processManager != null) {
            processManager.stop();
        }
        if (startupManager != null) {
            startupManager.shutdown();
        }
        if (messagingManager != null) {
            messagingManager.cleanup();
        }
        if (memoryOptimizer != null) {
            memoryOptimizer.shutdown();
        }
        getLogger().info("TranforC++ disabled!");
    }

    public static TranforCPlusPlus getInstance() {
        return instance;
    }

    public ProcessManager getProcessManager() {
        return processManager;
    }

    public void setProcessManager(ProcessManager processManager) {
        this.processManager = processManager;
    }

    public void reload() {
        processManager.restart();
        getLogger().info("TranforC++ reloaded!");
    }
    
    public com.github.tranforcpp.channel.PluginMessagingManager getMessagingManager() {
        return messagingManager;
    }
    
    public MemoryOptimizer getMemoryOptimizer() {
        return memoryOptimizer;
    }
}