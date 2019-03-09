package org.liamwang.yamlconfig;

import static org.liamwang.yamlconfig.YamlConfigManager.PATH_SEPARATOR;

import java.util.function.Consumer;

public final class YamlConfigPrefix {

    private final String prefix;
    private YamlConfigManager instance;

    YamlConfigPrefix(String prefix, YamlConfigManager instance) {
        this.prefix = prefix;
        this.instance = instance;
    }

    public String getPrefixString() {
        return prefix;
    }

    public YamlConfigEntry getEntry(String key) {
        return new YamlConfigEntry(prefix + PATH_SEPARATOR + key);
    }

    public void registerPrefixListener(Consumer<YamlConfigPrefix> onChange) {
        registerPrefixListener(true, onChange);
    }

    public void registerPrefixListener(boolean runOnce, Consumer<YamlConfigPrefix> onChange) {
        if (runOnce) {
            onChange.accept(this);
        }
        instance.registerPathListener(prefix, () -> onChange.accept(this));
    }
}
