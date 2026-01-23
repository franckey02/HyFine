package me.temxs27.hyfine.optimization;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import me.temxs27.hyfine.HyFine;
import me.temxs27.hyfine.core.OptimizationManager;
import me.temxs27.hyfine.core.PerformanceMonitor;
import me.temxs27.hyfine.preset.OptimizationPreset;

import java.util.concurrent.TimeUnit;

/**
 * The core optimization engine of the HyFine plugin.
 * It runs in a separate thread and applies optimizations based on the current preset and server performance.
 * Optimizations are applied by adjusting the world's target TPS and internal mod settings.
 * Note: Direct modification of WorldConfig from this thread is not possible due to API restrictions (requires CommandBuffer/ECS integration).
 * Alternative: Potential integration points could be SpawningPlugin/NPCSpawningConfig or a hypothetical ECS task scheduler.
 */
public class OptimizationEngine {

    private final HyFine plugin;
    private Thread optimizationThread;
    private volatile boolean running = false;

    // Internal state influenced by presets, can be used by other parts of the plugin
    private int itemDespawnTicks = 6000; // Configuration setting influenced by the preset
    private boolean aggressiveMode = false; // Flag influenced by the preset

    // --- Configuration Values Based on Presets ---
    // LOW preset settings
    private static final int LOW_ITEM_DESPAWN_TICKS = 6000; // 5 minutes
    private static final int LOW_TARGET_TPS = 30; // Default TPS

    // BALANCED preset settings (default server behavior assumed)
    private static final int BALANCED_ITEM_DESPAWN_TICKS = 3600; // 3 minutes
    private static final int BALANCED_TARGET_TPS = 30; // Default TPS

    // ULTRA preset settings
    private static final int ULTRA_ITEM_DESPAWN_TICKS = 1200; // 1 minute
    private static final int ULTRA_TARGET_TPS = 20; // Lower TPS to reduce load


    public OptimizationEngine(HyFine plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (running) {
            return;
        }

        running = true;
        optimizationThread = new Thread(() -> {
            try {
                optimizationLoop();
            } catch (Exception e) {
                System.err.println("[HyFine] Critical error in optimization loop: " + e.getMessage());
                e.printStackTrace();
            }
        }, "HyFine-Optimization");
        optimizationThread.setDaemon(true);
        optimizationThread.start();
    }

    public void stop() {
        running = false;
        if (optimizationThread != null) {
            optimizationThread.interrupt();
        }
    }

    private void optimizationLoop() {
        while (running) {
            try {
                // Sleep for 1 second between optimization cycles
                TimeUnit.SECONDS.sleep(1);

                // Update performance data
                plugin.getPerformanceMonitor().update();

                // Apply optimizations for all worlds
                applyOptimizations();

            } catch (InterruptedException e) {
                break; // Exit cleanly if interrupted
            } catch (Exception e) {
                System.err.println("[HyFine] Error in optimization loop: " + e.getMessage());
                e.printStackTrace(); // Log full stack trace for debugging
            }
        }
    }

    private void applyOptimizations() {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        OptimizationPreset preset = OptimizationManager.getPreset();
        // PerformanceMonitor.PerformanceData globalData = plugin.getPerformanceMonitor().getGlobalData(); // Removed: Method does not exist

        // Determine base settings based on the preset
        int despawnTicks;
        int targetTps;

        switch (preset) {
            case LOW:
                despawnTicks = LOW_ITEM_DESPAWN_TICKS;
                targetTps = LOW_TARGET_TPS;
                aggressiveMode = false;
                break;
            case BALANCED:
                despawnTicks = BALANCED_ITEM_DESPAWN_TICKS;
                targetTps = BALANCED_TARGET_TPS;
                aggressiveMode = false;
                break;
            case ULTRA:
                despawnTicks = ULTRA_ITEM_DESPAWN_TICKS;
                targetTps = ULTRA_TARGET_TPS; // Lower TPS for ultra preset
                aggressiveMode = true;
                break;
            default:
                // Fallback to BALANCED if preset is unknown
                despawnTicks = BALANCED_ITEM_DESPAWN_TICKS;
                targetTps = BALANCED_TARGET_TPS;
                aggressiveMode = false;
                break;
        }

        // Apply preset-based settings and TPS adjustments to all worlds
        for (World world : universe.getWorlds().values()) {
            PerformanceMonitor.PerformanceData data = plugin.getPerformanceMonitor().getData(world.getName());

            // --- 1. Adjust Target TPS based on preset and current performance ---
            // This affects how fast the world tries to run overall.
            int currentTargetTps = world.getTps(); // Get current target TPS
            if (currentTargetTps != targetTps) {
                try {
                    world.setTps(targetTps); // Attempt to set the new target TPS
                    System.out.println("[HyFine] Applied target TPS=" + targetTps + " for preset " + preset.name() + " in world: " + world.getName());
                } catch (Exception e) {
                    System.err.println("[HyFine] Failed to set TPS for world '" + world.getName() + "': " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // --- 2. Apply Emergency/Moderate TPS-based adjustments ---
            // Example: If TPS is critically low, force a lower TPS temporarily, regardless of preset (except maybe ULTRA which is already low)
            if (data.tps < 15) {
                 int emergencyTps = 15; // Or even lower like 10
                 if (world.getTps() > emergencyTps) { // Only decrease if current target is higher
                     try {
                         world.setTps(emergencyTps);
                         System.out.println("[HyFine] Emergency: Set target TPS to " + emergencyTps + " due to very low TPS (" + data.tps + ") in world: " + world.getName());
                     } catch (Exception e) {
                         System.err.println("[HyFine] Failed to set emergency TPS for world '" + world.getName() + "': " + e.getMessage());
                         e.printStackTrace();
                     }
                 }
                 // Example: Force faster item despawn in emergencies if the current preset allows longer times
                 if (this.itemDespawnTicks > 1200) { // Only if current preset allows longer times
                      this.itemDespawnTicks = 1200; // 1 minute
                      System.out.println("[HyFine] Emergency: Set itemDespawnTicks to 1200 due to low TPS (" + data.tps + ") in world: " + world.getName());
                 }
            } else if (data.tps < 18 && preset == OptimizationPreset.BALANCED) { // Moderate adjustment for BALANCED preset
                 int moderateTps = 20;
                 if (world.getTps() > moderateTps) {
                     try {
                         world.setTps(moderateTps);
                         System.out.println("[HyFine] Moderate: Set target TPS to " + moderateTps + " due to moderate low TPS (" + data.tps + ") in world: " + world.getName());
                     } catch (Exception e) {
                         System.err.println("[HyFine] Failed to set moderate TPS for world '" + world.getName() + "': " + e.getMessage());
                         e.printStackTrace();
                     }
                 }
            }

            // Update internal state influenced by preset
            this.itemDespawnTicks = despawnTicks;
        }
    }


    // --- Getters for State Influenced by Presets ---
    // These can be used by other parts of the plugin (e.g., spawning systems, item despawn logic).

    public int getItemDespawnTicks() {
        return itemDespawnTicks;
    }

    public boolean isAggressiveMode() {
        return aggressiveMode;
    }
}
