package org.liamwang.yamlconfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.parser.ParserException;
import org.yaml.snakeyaml.scanner.ScannerException;

public class YamlConfigManager implements Runnable {

    private static final Logger logger = Logger.getLogger(YamlConfigManager.class);

    public static final char PATH_SEPARATOR = '/';
    public static final String CONFIG_PATH = "deploy/";
    public static final String CONFIG_CONFIGFILE = "config-meta.yaml";
    public static final String WATCHER_ROOT = "deploy";

    private static YamlConfigManager instance;

    static YamlConfigManager getInstance() {
        if (instance == null) {
            instance = new YamlConfigManager();
        }
        return instance;
    }

    private Map<String, List<String>> configFileMap = new LinkedHashMap<>(); // Maps category names to file path sets, key "" is reserved for primary config paths
    private Map<String, Object> reducedConfigMap = new LinkedHashMap<>(); // Maps config paths to values
    private Map<String, List<Runnable>> listenerMap = new LinkedHashMap<>(); // Maps config paths to listeners

    private YamlConfigManager() {
        parseCategoryFile();
        updateAllFiles();
//        try {
//            Files.walk(Paths.get(ROOT_PATH)).filter(Files::isRegularFile).forEach(filePath -> update(filePath, false)); // Update once at the beginning
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        Thread yConfigThread = new Thread(this);
//        yConfigThread.start();
    }

    private void parseCategoryFile() {
        Yaml yaml = new Yaml();
        try {
            InputStream input = new FileInputStream(new File(CONFIG_PATH + CONFIG_CONFIGFILE));
            Map<String, Object> rawConfigFileMap = yaml.load(input);
            rawConfigFileMap.keySet().stream().filter(key -> !key.equals("primary") && !key.equals("override")).forEach(key -> {
                logger.warn("Config meta file contains undefined key: " + key);
            });
            Object primaryPathsObject = rawConfigFileMap.get("primary");
            if (!(primaryPathsObject instanceof List)) {
                logger.warn("Config meta file override key does not contain a list: " + (primaryPathsObject == null ? "null" : primaryPathsObject.toString()));
            } else {
                List<String> primaryPaths = new ArrayList<>();
                //noinspection unchecked
                ((List<Object>) primaryPathsObject).forEach(path -> {
                    if (path instanceof String) {
                        primaryPaths.add((String) path);
                    } else {
                        logger.warn("Config meta file contains path which is not a string: " + (path == null ? "null" : path.toString()));
                    }
                });
                configFileMap.put("", primaryPaths);
            }
            if (rawConfigFileMap.get("override") instanceof Map) {
                //noinspection unchecked
                Map<String, Object> tempMap = (Map<String, Object>) rawConfigFileMap.get("override");
                for (Entry<String, Object> entry : tempMap.entrySet()) {
                    Object overridePathsObject = entry.getValue();// overrideMap values might be empty lists!
                    if (!(overridePathsObject instanceof List)) {
                        logger.warn("Config meta file override key does not contain a list: " + (overridePathsObject == null ? "null" : overridePathsObject.toString()));
                    } else {
                        List<String> tempOverridePath = new ArrayList<>();
                        //noinspection unchecked
                        ((List<Object>) overridePathsObject).forEach(path -> {
                            if (path instanceof String) {
                                tempOverridePath.add((String) path);
                            } else {
                                logger.warn("Config meta file contains path which is not a string: " + (path == null ? "null" : path.toString()));
                            }
                        });
                        configFileMap.put(entry.getKey(), tempOverridePath);
                    }
                }
            } else {
                logger.debug("No overrides specified in config meta file.");
            }
        } catch (ScannerException | ParserException e) {
            logger.error("Exception when parsing config meta file " + CONFIG_PATH + CONFIG_CONFIGFILE + e.getContextMark());
        } catch (FileNotFoundException e) {
            logger.error("Config meta file not found at path: " + CONFIG_PATH + CONFIG_CONFIGFILE);
        }
    }

    private void updateAllFiles() {
        List<String> primaryPaths = configFileMap.get("");
        if (primaryPaths != null) {
            for (String primaryFile : primaryPaths) {
                update(Paths.get(CONFIG_PATH + primaryFile), true);
            }
        }
        List<String> overridePaths = configFileMap.get(RobotIdentifier.getRobotName());
        if (overridePaths != null) {
            for (String filePath : overridePaths) {
                update(Paths.get(CONFIG_PATH + filePath), true);
            }
        }
        System.out.println("------------------");
        printConfig();
    }

    public void printConfig() {
        reducedConfigMap.forEach((key, value) -> {
            System.out.println(key + ": " + value);
        });
    }

    @Override
    public void run() {
        try {
            new WatchDir(Paths.get(WATCHER_ROOT), true, filePath -> {
                if (filePath.endsWith("yaml")) {
                    updateAllFiles();
                }
            }).processEvents(); // Start watching for updates
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void update(Path path, boolean updateListeners) {
        if (!FilenameUtils.isExtension(path.toString(), "yaml")) {
            logger.warn("Provided file " + path.toString() + " is not of type yaml");
            return;
        }
        Yaml yaml = new Yaml();
        try {
            InputStream input = new FileInputStream(new File(path.toUri()));
            @SuppressWarnings("unchecked")
            Map<String, Object> map = yaml.load(input);
            if (traverseKeyMap("", map, updateListeners) && updateListeners) {
                if (listenerMap.containsKey("")) {
                    listenerMap.get("").forEach(Runnable::run);
                }
            }
        } catch (Exception e) {
            logger.error("Unable to parse YAML file: " + e.toString());
        }
    }

    private boolean traverseKeyMap(String baseString, Map<String, Object> map, boolean updateListeners) {
        boolean didChange = false;
        try {
            for (Entry<String, Object> entry : map.entrySet()) {
                boolean subDidChange = false;
                String str = entry.getKey();
                Object obj = entry.getValue();
                if (obj instanceof Map) {
                    subDidChange |= traverseKeyMap(baseString + PATH_SEPARATOR + str, (Map<String, Object>) obj, true);
                } else if (obj instanceof Number) {
                    String normalizedKey = normalizePathStandard(baseString + PATH_SEPARATOR + str);
                    subDidChange |= !obj.equals(reducedConfigMap.put(normalizedKey, obj));
                } else {
                    logger.warn("YAML contains object of unknown type: " + (obj == null ? "null" : obj.toString()));
                }
                String normalizedBase = normalizePathStandard(baseString + PATH_SEPARATOR + str);
                if (updateListeners && subDidChange && listenerMap.containsKey(normalizedBase)) {
                    listenerMap.get(normalizedBase).forEach(Runnable::run);
                }
                didChange |= subDidChange;
            }
        } catch (NullPointerException ignored) {
        }
        return didChange;
    }

    synchronized Double getDouble(String key) {
        Object val = reducedConfigMap.get(normalizePathStandard(key));
        if (val == null) {
            return null;
        }
        if (!(val instanceof Number)) {
            throw new IllegalArgumentException("Key does not correspond to value of type Number");
        }
        return ((Number) val).doubleValue();
    }


    synchronized void registerPrefixListener(String key, Runnable onChange) {
        String normalizedKey = normalizePathStandard(key);
        if (listenerMap.containsKey(normalizedKey)) {
            listenerMap.get(normalizedKey).add(onChange);
        } else {
            ArrayList<Runnable> newListenerList = new ArrayList<>();
            newListenerList.add(onChange);
            listenerMap.put(normalizedKey, newListenerList);
        }
    }

    /**
     * @param path the path to normalize
     * @param withLeadingSlash whether or not the normalized key should begin with a leading slash
     * @return normalized key
     */
    public static String normalizePath(String path, boolean withLeadingSlash, boolean removeTrailingSlash) {
        if (path.equals("")) {
            return "";
        }

        String normalized;
        if (withLeadingSlash) {
            normalized = PATH_SEPARATOR + path;
        } else {
            normalized = path;
        }
        normalized = normalized.replaceAll(PATH_SEPARATOR + "{2,}", String.valueOf(PATH_SEPARATOR));

        if (!withLeadingSlash && normalized.charAt(0) == PATH_SEPARATOR) {
            normalized = normalized.substring(1);
        }

        if (removeTrailingSlash && normalized.charAt(normalized.length() - 1) == PATH_SEPARATOR) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public static String normalizePathStandard(String path) {
        return normalizePath(path, false, true);
    }
}
