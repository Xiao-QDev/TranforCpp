package com.github.tranforcpp.example;

import com.github.tranforcpp.ProcessManager.GenericTranforCEvent;
import com.github.tranforcpp.TranforCPlusPlus;
import com.github.tranforcpp.channel.PluginMessagingManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;

/**
 * TranforC++ 事件监听示例插件
 * 展示如何同时使用方案A和方案B
 */
public class DualEventListenerExample extends JavaPlugin implements Listener, PluginMessageListener {
    
    private PluginMessagingManager messagingManager;
    
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        messagingManager = TranforCPlusPlus.getInstance().getMessagingManager();
        if (messagingManager != null) {
            getServer().getMessenger().registerIncomingPluginChannel(
                this, 
                PluginMessagingManager.CHANNEL_TRANFORCPP, 
                this
            );
        }
        
        getLogger().info("TranforC++ 双方案事件监听示例已启用");
    }
    
    /**
     * 方案A：监听Bukkit事件系统中的TranforC++事件
     */
    @EventHandler
    public void onTranforCEvent(GenericTranforCEvent event) {
        getLogger().info("[方案A] 收到本地事件: " + event.getEventName());
        
        // 处理具体事件
        switch (event.getEventName()) {
            case "PlayerJoin":
                handleLocalPlayerJoin(event);
                break;
            case "BlockBreak":
                handleLocalBlockBreak(event);
                break;
        }
    }
    
    /**
     * 方案B：通过消息通道接收事件（支持跨服务器）
     */
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (channel.equals(PluginMessagingManager.CHANNEL_TRANFORCPP)) {
            getLogger().info("[方案B] 收到跨服务器事件");
            
            try {
                String jsonData = new String(message, StandardCharsets.UTF_8);
                getLogger().info("接收到JSON数据: " + jsonData);
                
            } catch (Exception e) {
                getLogger().warning("处理跨服务器事件失败: " + e.getMessage());
            }
        }
    }
    
    private void handleLocalPlayerJoin(GenericTranforCEvent event) {
        String playerName = (String) event.getArg(0);
        getLogger().info("本地玩家加入: " + playerName);
    }
    
    private void handleLocalBlockBreak(GenericTranforCEvent event) {
        String playerName = (String) event.getArg(0);
        String blockType = (String) event.getArg(1);
        getLogger().info("本地方块破坏: " + playerName + " 破坏 " + blockType);
    }
    

    
    @Override
    public void onDisable() {
        if (messagingManager != null) {
            getServer().getMessenger().unregisterIncomingPluginChannel(
                this, 
                PluginMessagingManager.CHANNEL_TRANFORCPP, 
                this
            );
        }
        getLogger().info("TranforC++ 双方案事件监听示例已禁用");
    }
}