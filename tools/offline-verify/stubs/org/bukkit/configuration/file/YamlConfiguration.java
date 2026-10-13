// APPROXIMATE stub for offline syntax/type checking ONLY.
// Backed by SnakeYAML (test-only lib in tools/offline-verify/lib) so the
// offline server-boot harness exercises REAL persistence round-trips.
// Excluded from the Gradle build; the real Bukkit class differs.
package org.bukkit.configuration.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class YamlConfiguration extends FileConfiguration {
    final Map<String, Object> root = new LinkedHashMap<>();

    public static YamlConfiguration loadConfiguration(File file) {
        YamlConfiguration c = new YamlConfiguration();
        if (file != null && file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                Object o = new Yaml().load(in);
                if (o instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        c.root.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return c;
    }

    @SuppressWarnings("unchecked")
    Object at(String path) {
        Object cur = root;
        for (String part : path.split("\\.")) {
            if (!(cur instanceof Map<?, ?> m)) {
                return null;
            }
            cur = ((Map<String, Object>) m).get(part);
        }
        return cur;
    }

    @Override
    public String getString(String path, String def) {
        Object o = at(path);
        return o == null ? def : String.valueOf(o);
    }

    @Override
    public int getInt(String path, int def) {
        Object o = at(path);
        return o instanceof Number n ? n.intValue() : def;
    }

    @Override
    public long getLong(String path, long def) {
        Object o = at(path);
        return o instanceof Number n ? n.longValue() : def;
    }

    @Override
    public double getDouble(String path, double def) {
        Object o = at(path);
        return o instanceof Number n ? n.doubleValue() : def;
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        Object o = at(path);
        return o instanceof Boolean b ? b : def;
    }

    @Override
    public List<String> getStringList(String path) {
        Object o = at(path);
        if (!(o instanceof List<?> l)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object e : l) {
            out.add(String.valueOf(e));
        }
        return out;
    }

    @Override
    public boolean isConfigurationSection(String path) {
        return at(path) instanceof Map;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ConfigurationSection getConfigurationSection(String path) {
        Object o = at(path);
        if (!(o instanceof Map<?, ?> m)) {
            return null;
        }
        YamlConfiguration sub = new YamlConfiguration();
        sub.root.putAll((Map<String, Object>) m);
        return sub;
    }

    @Override
    public List<Map<?, ?>> getMapList(String path) {
        Object o = at(path);
        if (!(o instanceof List<?> l)) {
            return List.of();
        }
        List<Map<?, ?>> out = new ArrayList<>();
        for (Object e : l) {
            if (e instanceof Map<?, ?> m) {
                out.add(m);
            }
        }
        return out;
    }

    @Override
    public Set<String> getKeys(boolean deep) {
        return root.keySet();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void set(String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object o = cur.get(parts[i]);
            if (!(o instanceof Map<?, ?>)) {
                Map<String, Object> fresh = new LinkedHashMap<>();
                cur.put(parts[i], fresh);
                cur = fresh;
            } else {
                cur = (Map<String, Object>) o;
            }
        }
        cur.put(parts[parts.length - 1], value);
    }

    @Override
    public void save(File file) {
        try {
            DumperOptions opts = new DumperOptions();
            opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            file.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(file, StandardCharsets.UTF_8)) {
                new Yaml(opts).dump(root, w);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
