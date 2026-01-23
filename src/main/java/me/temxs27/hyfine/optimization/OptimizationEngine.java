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
 * Current optimizations focus on mod-specific settings (like itemDespawnTicks) and potential safe WorldConfig adjustments.
 * Note: Direct control of NPC spawning via WorldConfig might not be effective without SpawningPlugin integration.
 */
public class OptimizationEngine {

    private final HyFine plugin;
    private Thread optimizationThread;
    private volatile boolean running = false;

    // Internal state influenced by presets, can be used by other parts of the plugin
    private int itemDespawnTicks = 6000; // Configuration setting influenced by the preset
    private boolean aggressiveMode = false; // Flag influenced by the preset

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

        for (World world : universe.getWorlds().values()) {
            PerformanceMonitor.PerformanceData data = plugin.getPerformanceMonitor().getData(world.getName());

            // Determine base settings based on the preset
            int baseItemDespawnTicks;
            boolean baseAggressiveMode;

            switch (preset) {
                case LOW:
                    baseItemDespawnTicks = 6000; // 5 mins
                    baseAggressiveMode = false;
                    break;

                case BALANCED:
                    baseItemDespawnTicks = 3600; // 3 mins
                    baseAggressiveMode = false;
                    break;

                case ULTRA:
                    baseItemDespawnTicks = 1200; // 1 min
                    baseAggressiveMode = true;
                    break;

                default:
                    // Fallback to BALANCED if preset is unknown
                    baseItemDespawnTicks = 3600;
                    baseAggressiveMode = false;
                    break;
            }

            // Apply base settings influenced by preset
            this.itemDespawnTicks = baseItemDespawnTicks;
            this.aggressiveMode = baseAggressiveMode;

            // Apply TPS-based emergency optimizations on top of preset settings
            if (data.tps < 15) {
                // Example: Force faster item despawn in emergencies if the current preset allows longer times
                if (this.itemDespawnTicks > 1200) {
                     this.itemDespawnTicks = 1200; // 1 minute
                     System.out.println("[HyFine] Emergency: Set itemDespawnTicks to 1200 due to low TPS (" + data.tps + ") in world: " + world.getName());
                }
            }

            // Trigger specific optimization routines based on preset and TPS
            // These currently only print messages as the core optimizations happen via state changes (itemDespawnTicks, aggressiveMode)
            // or potentially via external systems reacting to these states.
            switch (preset) {
                case LOW:
                    if (data.tps < 15) {
                        applyEmergencyOptimizations(world);
                    }
                    break;

                case BALANCED:
                    if (data.tps < 18) {
                        applyModerateOptimizations(world);
                    }
                    break;

                case ULTRA:
                    applyAggressiveOptimizations(world); // ULTRA always applies its core logic
                    break;
            }
        }
    }

    private void applyEmergencyOptimizations(World world) {
        // Currently a marker for when emergency optimizations are triggered.
        // The actual optimization (faster item despawn) is handled by setting itemDespawnTicks above.
        System.out.println("[HyFine] Emergency optimizations conceptually applied for world: " + world.getName());
    }

    private void applyModerateOptimizations(World world) {
        // Currently a marker for when moderate optimizations are triggered.
        System.out.println("[HyFine] Moderate optimizations conceptually applied for world: " + world.getName());
    }

    private void applyAggressiveOptimizations(World world) {
        // Currently a marker for when aggressive optimizations are triggered.
        // The actual optimization (fastest item despawn, aggressive mode flag) is handled by setting itemDespawnTicks/aggressiveMode above.
        System.out.println("[HyFine] Aggressive optimizations conceptually applied for world: " + world.getName());
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
