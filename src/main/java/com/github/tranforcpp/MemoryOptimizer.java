package com.github.tranforcpp;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryOptimizer {
    
    private final TranforCPlusPlus plugin;
    private final MemoryMXBean memoryBean;
    
    private final ConcurrentHashMap<String, Object> objectPool = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cacheExpiry = new ConcurrentHashMap<>();
    
    private BukkitTask cleanupTask;
    private BukkitTask monitorTask;
    
    private static final long CLEANUP_INTERVAL = 300000L;
    private static final long MONITOR_INTERVAL = 60000L;
    private static final long CACHE_EXPIRY_TIME = 1800000L;
    
    public MemoryOptimizer(TranforCPlusPlus plugin) {
        this.plugin = plugin;
        this.memoryBean = ManagementFactory.getMemoryMXBean();
    }
    
    public void initialize() {
        startCleanupTask();
        startMonitorTask();
    }
    
    private void startCleanupTask() {
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            cleanupExpiredCache();
            cleanObjectPool();
            triggerGarbageCollectionIfNeeded();
        }, CLEANUP_INTERVAL, CLEANUP_INTERVAL);
    }
    
    private void startMonitorTask() {
        monitorTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            logMemoryStatistics();
            checkMemoryPressure();
        }, MONITOR_INTERVAL, MONITOR_INTERVAL);
    }
    
    public void addObjectToPool(String key, Object obj) {
        objectPool.put(key, obj);
        cacheExpiry.put(key, System.currentTimeMillis() + CACHE_EXPIRY_TIME);
    }
    
    private void cleanupExpiredCache() {
        long currentTime = System.currentTimeMillis();
        
        for (String key : cacheExpiry.keySet()) {
            Long expiryTime = cacheExpiry.get(key);
            if (expiryTime != null && currentTime > expiryTime) {
                objectPool.remove(key);
                cacheExpiry.remove(key);
            }
        }
    }
    
    private void cleanObjectPool() {
        if (objectPool.size() > 1000) {
            int removeCount = objectPool.size() / 2;
            objectPool.entrySet().stream()
                .limit(removeCount)
                .forEach(entry -> objectPool.remove(entry.getKey()));
        }
    }
    
    private void triggerGarbageCollectionIfNeeded() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double usageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        
        if (usageRatio > 0.85) {
            System.gc();
        }
    }
    
    private void checkMemoryPressure() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double usageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        
        if (usageRatio > 0.9) {
            forceCleanup();
        } else if (usageRatio > 0.75) {
            cleanupExpiredCache();
            if (objectPool.size() > 500) {
                cleanObjectPool();
            }
        }
    }
    
    private void forceCleanup() {
        objectPool.clear();
        cacheExpiry.clear();
        
        System.gc();
    }
    
    private void logMemoryStatistics() {
    }
    
    private void logMemoryInfo() {
    }
    
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        if (monitorTask != null) {
            monitorTask.cancel();
        }
        
        objectPool.clear();
        cacheExpiry.clear();
    }
}