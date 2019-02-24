package org.liamwang.yamlconfig;

public class YamlConfig {

    public static YamlConfigPrefix instance = new YamlConfigPrefix();

    public static String getPrefix() {
        return instance.getPrefix();
    }

    public static Double getDouble(String key, double defaultValue) {
        return instance.getDouble(key, defaultValue);
    }

    public static Double getDoubleOrNull(String key) {
        return instance.getDoubleOrNull(key);
    }

    public static void registerPrefixListener(Runnable onChange) {
        instance.registerPrefixListener(onChange);
    }

    public static void registerKeyListener(String relativeKey, Runnable onChange) {
        instance.registerKeyListener(relativeKey, onChange);
    }

    public static void manualUpdateListeners() {
        instance.manualUpdateListeners();
    }
}
