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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
    public static final int UPDATE_FREQUENCY_TIMEOUT = 300;

    private static YamlConfigManager instance;

    static YamlConfigManager getInstance() {
        if (instance == null) {
            instance = new YamlConfigManager();
        }
        return instance;
    }

    private Map<String, List<String>> metaFileConfig = new LinkedHashMap<>(); // Maps category names to file path sets, key "" is reserved for primary config paths
    private Map<String, Object> reducedConfigMap = new LinkedHashMap<>(); // Maps config paths to values
    private Map<String, List<Runnable>> listenerMap = new LinkedHashMap<>(); // Maps config paths to listeners

    private YamlConfigManager() {
        parseMetaFile();
        updateAllFiles();
        Thread yConfigThread = new Thread(this);
        yConfigThread.start();
    }

    private boolean aboutToUpdate = false;

    @Override
    public void run() {
        try {
            logger.debug("Configuration listener started!");
            new WatchDir(Paths.get(CONFIG_ROOT_FOLDER), true, filePath -> {
                if (filePath.toString().endsWith("yaml")) {
                    logger.debug("Update received: " + filePath);
                    if (!aboutToUpdate) {
                        aboutToUpdate = true;
                        new Thread(() -> {
                            try {
                                Thread.sleep(UPDATE_FREQUENCY_TIMEOUT);
                                parseMetaFile();
                                updateAllFiles();
                            } catch (InterruptedException e) {
                                logger.error("Update timeout thread interrupted!");
                            }
                            aboutToUpdate = false;
                        }).start();
                    } else {
                        logger.debug("Update canceled because timeout already running!");
                    }
                }
            }).processEvents(); // Start watching for updates
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void parseMetaFile() { // TODO: static
        metaFileConfig = new HashMap<>();
        logger.debug("Parsing configuration meta file " + CONFIG_META_FILE);
        Yaml yaml = new Yaml();
        InputStream input = null;
        try {
            File file = new File(CONFIG_META_FILE);
            input = new FileInputStream(file);
            String yamlString = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            input.close();
            Map<String, Object> rawConfigFileMap = yaml.load(yamlString);
            if (rawConfigFileMap == null) {
                logger.error("Config meta file is null!");
                return;
            }
            rawConfigFileMap.keySet().stream().filter(key -> !key.equals(PRIMARY_KEY) && !key.equals(OVERRIDE_KEY)).forEach(key -> {
                logger.warn("Config meta file contains undefined key: " + key);
            });

            metaFileConfig.put("", extractPathsFromPossibleList(rawConfigFileMap.get(PRIMARY_KEY)));

            Object overrideValue = rawConfigFileMap.get(OVERRIDE_KEY);
            if (overrideValue instanceof Map) {
                //noinspection unchecked
                ((Map<String, Object>) overrideValue).forEach((overrideKey, possiblePathList) -> metaFileConfig.put(overrideKey, extractPathsFromPossibleList(possiblePathList)));
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

    private List<String> extractPathsFromPossibleList(Object pathList) { // TODO: static
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

    private Map<String, Object> newReducedConfigMap = new HashMap<>();

    private synchronized void updateAllFiles() {
        updateFilesInKey("");
        updateFilesInKey(RobotIdentifier.getRobotName());
        executeUpdates();
        logger.debug("---------Config Update Ended---------");
        printConfig();
    }

    private void executeUpdates() {
        Set<Runnable> nextUpdateSet = new HashSet<>();
        newReducedConfigMap.forEach((key, value) -> {

        });
        nextUpdateSet.forEach(Runnable::run);
    }

    private void updateFilesInKey(String key) {
        List<String> overridePaths = metaFileConfig.get(key);
        updateFilesInList(overridePaths);
    }

    private void updateFilesInList(List<String> overridePaths) {
        if (overridePaths != null) {
            for (String path : overridePaths) {
                String fullPath = CONFIG_ROOT_FOLDER + PATH_SEPARATOR + path;
                logger.debug("Parsing file " + fullPath);
                updateConfigFile(Paths.get(fullPath));
            }
        }
    }

    public void printConfig() {
        reducedConfigMap.forEach((key, value) -> {
            logger.debug(key + ": " + value);
        });
    }

    private void updateConfigFile(Path path) {
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
            traverseConfigMap("", map);
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

    private void traverseConfigMap(String globalPath, Map<String, Object> rawYamlMap) {
        for (Entry<String, Object> entry : rawYamlMap.entrySet()) {
            String localPath = entry.getKey();
            Object configValue = entry.getValue();
            processConfigValue(globalPath + PATH_SEPARATOR + localPath, configValue);
        }
    }

    /**
     * @param path Full path to the location in the config tree
     * @param value Object that is either a map of more config values or a config value itself
     * @return If the value changed or a value in the path changed
     */
    private void processConfigValue(String path, Object value) {
        if (value instanceof Map) { // if value is a map of more config values
            traverseConfigMap(path, (Map<String, Object>) value);
        } else if (isSupportedType(value)) { // if value is a config entry
            newReducedConfigMap.put(path, value);
        } else {
            logger.warn("YAML contains object of unknown type: " + (value == null ? "null" : value.toString()));
        }
    }

    private static boolean isSupportedType(Object value) {
        return value instanceof Number;
    }

    // public begin

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


    synchronized void registerPathListener(String key, Runnable onChange) {
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
