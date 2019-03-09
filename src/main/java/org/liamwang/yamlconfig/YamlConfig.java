package org.liamwang.yamlconfig;

public class YamlConfig {

    public static YamlConfigPrefix instance;

    public static YamlConfigEntry getEntry(String prefix) {
        if (instance == null) {
            instance = new YamlConfigPrefix("", YamlConfigManager.getInstance());
        }
        return instance.getEntry(prefix);
    }

    public static YamlConfigPrefix getPrefix(String prefix) {
        return new YamlConfigPrefix(prefix, YamlConfigManager.getInstance());
    }
}