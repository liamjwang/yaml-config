package org.liamwang.yamlconfig;

import java.util.function.Consumer;

public class YamlConfigEntry {

    private final String path;

    public YamlConfigEntry(String path) {
        this.path = path;
    }

    public Double getDouble(double defaultValue) {
        Double num = getDoubleOrNull();
        return num == null ? defaultValue : num;
    }

    public Double getDoubleOrNull() {
        return YamlConfigManager.getInstance().getDouble(path);
    }

    /**
     * @param onChange Method to call when the value of relativeKey is changed is changed
     */
    public void registerListener(Consumer<YamlConfigEntry> onChange) {
        registerListener(true, onChange);
    }

    public void registerListener(boolean runOnce, Consumer<YamlConfigEntry> onChange) {
        if (runOnce) {
            onChange.accept(this);
        }
        YamlConfigManager.getInstance().registerPathListener(path, () -> onChange.accept(this));
    }
}
