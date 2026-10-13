// APPROXIMATE stub for offline syntax/type checking ONLY.
package org.bukkit.configuration.file;

import java.io.File;

public class YamlConfiguration extends FileConfiguration {
    public static YamlConfiguration loadConfiguration(File file) {
        return new YamlConfiguration();
    }
}
