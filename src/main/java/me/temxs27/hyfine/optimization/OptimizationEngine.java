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
 * Note: Direct control of NPC spawning via WorldConfig might not be effective. Interaction with SpawningPlugin is preferred if possible.
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
            // IMPORTANT: Setting WorldConfig properties like isSpawningNPC directly here might not affect the internal SpawningPlugin.
            // The primary goal here is to adjust mod-specific settings and potentially safe WorldConfig flags.
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
            // These could potentially modify WorldConfig if deemed safe and effective by the API.
            // Example: If TPS is critically low, force stricter measures regardless of preset (except maybe ULTRA which is already strict)
            if (data.tps < 15) {
                // Example: Force faster item despawn in emergencies
                if (this.itemDespawnTicks > 1200) { // Only if current preset allows longer times
                     this.itemDespawnTicks = 1200; // 1 minute
                     System.out.println("[HyFine] Emergency: Set itemDespawnTicks to 1200 due to low TPS (" + data.tps + ") in world: " + world.getName());
                }
                // Example: Potentially disable block ticking if TPS is very low (check if safe via API first)
                // if (world.getWorldConfig().isBlockTicking()) { // Check if changeable
                //    world.getWorldConfig().setBlockTicking(false); // Use CommandBuffer if needed
                //    System.out.println("[HyFine] Emergency: Disabled block ticking due to very low TPS (" + data.tps + ") in world: " + world.getName());
                // }
            }

            // Trigger specific optimization routines based on preset and TPS
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
        // Trigger actions based on emergency state
        // For now, this might just mean ensuring aggressiveMode is true and itemDespawnTicks is minimal
        // More complex actions would require deeper API integration discovered later (e.g., interacting with SpawningPlugin)
        System.out.println("[HyFine] Emergency optimizations triggered for world: " + world.getName());
        // Example: Could trigger a garbage collection hint, log aggressive state, etc.
        // Direct ECS manipulation or config changes are not safely demonstrated in the provided code.
    }

    private void applyModerateOptimizations(World world) {
        // Trigger actions based on moderate state
        System.out.println("[HyFine] Moderate optimizations triggered for world: " + world.getName());
        // Example: Log state, adjust internal flags slightly less aggressively
    }

    private void applyAggressiveOptimizations(World world) {
        // Trigger actions based on aggressive state
        System.out.println("[HyFine] Aggressive optimizations triggered for world: " + world.getName());
        // Attempt to reduce view distance (implementation pending API discovery)
        reduceViewDistance(world);
        // Other potential actions based on mod-specific settings
    }

    /**
     * Attempts to reduce the view distance for players in the world based on performance.
     * This function currently acts as a placeholder as the direct API for changing
     * world configuration from a plugin might require further investigation or CommandBuffer usage.
     * @param world The world to optimize.
     */
    private void reduceViewDistance(World world) {
        PerformanceMonitor.PerformanceData data = plugin.getPerformanceMonitor().getData(world.getName());

        if (data.tps < 16 && !world.isPaused()) {
            // Placeholder for view distance optimization logic
            // Requires finding the correct API call to modify world config safely
            System.out.println("[HyFine] Attempting to reduce view distance for world '" + world.getName() + "' due to low TPS (" + data.tps + ").");
            // Example (not implemented): world.getConfiguration().setViewDistance(...);
            // This needs verification against the full HytaleServer API.
        }
    }

    // --- Placeholder Methods (Previously Empty) ---
    // These are kept for potential future expansion or if other strategies are added later.
    // For now, the main logic resides in applyOptimizations via mod-specific settings and potential WorldConfig hints.

    private void optimizeChunkTicking(World world, boolean aggressive) {
        // This method previously tried to optimize ticking frequency.
        // That logic might require ECS interaction or interaction with the SpawningPlugin.
        // Kept as a potential hook for future optimizations.
        System.out.println("[HyFine] optimizeChunkTicking called for " + world.getName() + ", aggressive: " + aggressive + ". Consider ECS or SpawningPlugin interaction.");
    }

    private void optimizeDistantChunks(World world) {
        // This method previously tried to pause distant entities.
        // While powerful, it required deep ECS integration or specific SpawningPlugin interaction.
        // Kept as a potential hook for future ECS-based optimizations.
        System.out.println("[HyFine] optimizeDistantChunks called for " + world.getName() + ". Consider ECS or SpawningPlugin interaction.");
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
