# HyFine

HyFine is a mod for Hytale Server designed to optimize server performance by applying different configuration presets based on current load and user settings.

## Features

*   **Optimization Presets:** Applies global and per-world settings based on predefined presets (e.g., LOW, BALANCED, ULTRA).
*   **TPS Control:** Adjusts the target TPS of worlds to balance performance and simulation.
*   **Simulation Control:** Enables/disables block ticking, NPC spawning, and chunk unloading/saving based on the preset and TPS.
*   **Render Distance Control:** Reduces player view distance individually based on the preset.
*   **Visual Effects Control:** Disables visual effects like Bloom and Sunshafts in more aggressive presets to reduce client load.
*   **Chunk Policies:** Controls whether chunks can be saved or unloaded based on the preset.
*   **Emergency Settings:** Further reduces settings if TPS drops below a critical threshold.

## Installation

1.  Place the HyFine `.jar` file into your Hytale server's `plugins` folder.
2.  Restart the server to load the plugin.

## Usage

Settings are applied automatically based on the selected preset and server configuration. Currently, there are no public commands to change the preset manually, but it can be configured in the plugin's code.

## License

Read the file LICENSE
