package org.liamwang.yamlconfig;

public final class YamlConfigPrefix {

    private final String prefix;
    private YamlConfigManager instance;

    public YamlConfigPrefix(String prefix) {
        this.prefix = prefix;
        instance = YamlConfigManager.getInstance();
    }

    public YamlConfigPrefix() {
        this("");
    }

    public String getPrefix() {
        return prefix;
    }

    public Double getDouble(String key, double defaultValue) {
        Double num = YamlConfigManager.getInstance().getDouble(prefix + YamlConfigManager.PATH_SEPARATOR + key);
        return num == null ? defaultValue : num;
    }

    public Double getDoubleOrNull(String key) {
        return instance.getDouble(prefix + YamlConfigManager.PATH_SEPARATOR + key);
    }

    /**
     * @param onChange Method to call when any value in the prefix is changed
     */
    public void registerPrefixListener(Runnable onChange) {
        instance.registerPrefixListener(prefix, onChange);
        onChange.run();
    }

    /**
     * @param relativeKey Relative path to key
     * @param onChange Method to call when the value of relativeKey is changed is changed
     */
    public void registerKeyListener(String relativeKey, Runnable onChange) {
        instance.registerPrefixListener(prefix + YamlConfigManager.PATH_SEPARATOR + relativeKey, onChange);
        onChange.run();
    }
}