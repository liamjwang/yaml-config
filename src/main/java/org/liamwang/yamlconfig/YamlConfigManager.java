package org.liamwang.yamlconfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

    private static final String PRIMARY_KEY = "primary";
    private static final String OVERRIDE_KEY = "override";

    static final char PATH_SEPARATOR = '/';

    private static final String CONFIG_ROOT_FOLDER = "deploy";
    private static final String CONFIG_META_FILE = "deploy/config-meta.yaml";
    public static final int UPDATE_FREQUENCY_TIMEOUT = 500;

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
        parseMetaFile();
        updateAllFiles();
        Thread yConfigThread = new Thread(this);
        yConfigThread.start();
    }

    private synchronized void parseMetaFile() {
        configFileMap = new LinkedHashMap<>();
        logger.debug("Parsing configuration meta file " + CONFIG_META_FILE);
        Yaml yaml = new Yaml();
        InputStream input = null;
        try {
            File file = new File(CONFIG_META_FILE);
            input = new FileInputStream(file);
            String yamlString = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            input.close();
            System.out.println(yamlString);
            Map<String, Object> rawConfigFileMap = yaml.load(yamlString);
            if (rawConfigFileMap == null) {
                logger.error("Config meta file is null!");
                return;
            }
            rawConfigFileMap.keySet().stream().filter(key -> !key.equals(PRIMARY_KEY) && !key.equals(OVERRIDE_KEY)).forEach(key -> {
                logger.warn("Config meta file contains undefined key: " + key);
            });

            configFileMap.put("", extractPathsFromPossibleList(rawConfigFileMap.get(PRIMARY_KEY)));

            Object overrideValue = rawConfigFileMap.get(OVERRIDE_KEY);
            if (overrideValue instanceof Map) {
                //noinspection unchecked
                ((Map<String, Object>) overrideValue).forEach((overrideKey, possiblePathList) -> configFileMap.put(overrideKey, extractPathsFromPossibleList(possiblePathList)));
            } else {
                logger.debug("No overrides specified in config meta file.");
            }

        } catch (ScannerException | ParserException e) {
            logger.error("Exception when parsing config meta file " + CONFIG_META_FILE + e.getContextMark());
        } catch (FileNotFoundException e) {
            logger.error("Config meta file not found at path: " + CONFIG_META_FILE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (input != null) {
            try {
                input.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private List<String> extractPathsFromPossibleList(Object pathList) {
        List<String> primaryPaths = new ArrayList<>();
        if (!(pathList instanceof List)) {
            logger.warn("Config meta file key does not contain a list: " + (pathList == null ? "null" : pathList.toString()));
        } else {
            //noinspection unchecked
            ((List<Object>) pathList).forEach(path -> {
                if (path instanceof String) {
                    primaryPaths.add((String) path);
                } else {
                    logger.warn("Config meta file contains path which is not a string: " + (path == null ? "null" : path.toString()));
                }
            });
        }
        return primaryPaths;
    }

    private void updateAllFiles() {
        updateFilesInKey("");
        updateFilesInKey(RobotIdentifier.getRobotName());
        System.out.println("------------------");
        printConfig();
    }

    private void updateFilesInKey(String key) {
        List<String> overridePaths = configFileMap.get(key);
        updateFilesInList(overridePaths);
    }

    private void updateFilesInList(List<String> overridePaths) {
        if (overridePaths != null) {
            for (String path : overridePaths) {
                String fullPath = CONFIG_ROOT_FOLDER + PATH_SEPARATOR + path;
                logger.debug("Parsing file " + fullPath);
                update(Paths.get(fullPath), true);
            }
        }
    }

    public void printConfig() {
        reducedConfigMap.forEach((key, value) -> {
            System.out.println(key + ": " + value);
        });
    }

    private long lastUpdateMillis = 0;

    @Override
    public void run() {
        try {
            logger.debug("Configuration listener started!");
            new WatchDir(Paths.get(CONFIG_ROOT_FOLDER), true, filePath -> {
//                if (System.currentTimeMillis() - lastUpdateMillis < UPDATE_FREQUENCY_TIMEOUT) {
//                    return;
//                }
                lastUpdateMillis = System.currentTimeMillis();
                if (filePath.toString().endsWith("yaml")) {
                    logger.debug("Update received: " + filePath);
                    parseMetaFile();
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
        InputStream input = null;
        try {
            input = new FileInputStream(new File(path.toUri()));
            @SuppressWarnings("unchecked")
            Map<String, Object> map = yaml.load(input);
            input.close();
            if (traverseKeyMap("", map, updateListeners) && updateListeners) {
                if (listenerMap.containsKey("")) {
                    listenerMap.get("").forEach(Runnable::run);
                }
            }
        } catch (Exception e) { // TODO: Don't do this
            logger.error("Unable to parse YAML file: " + e.toString());
        }
        if (input != null) {
            try {
                input.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
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
