// APPROXIMATE stub for offline syntax/type checking ONLY.
package org.bukkit.configuration.file;

import java.io.File;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public class FileConfiguration extends ConfigurationSection {
    public String getString(String path, String def) {
        return def;
    }

    public int getInt(String path, int def) {
        return def;
    }

    public long getLong(String path, long def) {
        return def;
    }

    public double getDouble(String path, double def) {
        return def;
    }

    public boolean getBoolean(String path, boolean def) {
        return def;
    }

    public List<String> getStringList(String path) {
        return null;
    }

    public boolean isConfigurationSection(String path) {
        return false;
    }

    public ConfigurationSection getConfigurationSection(String path) {
        return null;
    }

    public List<Map<?, ?>> getMapList(String path) {
        return null;
    }

    public void set(String path, Object value) {
    }

    public void save(File file) throws java.io.IOException {
    }
}
