package org.liamwang.yamlconfig; /**
 * Copyright (c) 2008, http://www.snakeyaml.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing permissions and limitations under the License.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.io.FilenameUtils;
import org.yaml.snakeyaml.Yaml;

public class YamlConfigManager implements Runnable {

    public static final char PATH_SEPARATOR = '/';

    private static YamlConfigManager instance;

    static YamlConfigManager getInstance() {
        if (instance == null) {
            instance = new YamlConfigManager();
        }
        return instance;
    }

    private Map<String, Object> configMap = new LinkedHashMap<>();
    private Map<String, List<Runnable>> listenerMap = new LinkedHashMap<>();

    private YamlConfigManager() {
        Thread yConfigThread = new Thread(this);
        yConfigThread.start();
    }

    @Override
    public void run() {
        String path = "yaml";
        try {
            Files.walk(Paths.get(path)).filter(Files::isRegularFile).forEach(this::update); // Update once at the beginning
            new WatchDir(Paths.get(path), true, this::update).processEvents(); // Start watching for updates
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void update(Path path) {
        if (!FilenameUtils.isExtension(path.toString(), "yaml")) {
            return;
        }
        Yaml yaml = new Yaml();
        try {
            InputStream input = new FileInputStream(new File(path.toUri()));
            @SuppressWarnings("unchecked")
            Map<String, Object> map = yaml.load(input);
            if (traverseKeyMap("", map)) {
                if (listenerMap.containsKey("")) {
                    listenerMap.get("").forEach(Runnable::run);
                }
            }
        } catch (Exception e) {
            System.out.println("[Error] Unable to parse YAML file: " + e.toString());
        }
    }

    private boolean traverseKeyMap(String baseString, Map<String, Object> map) {
        boolean didChange = false;
        try {
            for (Entry<String, Object> entry : map.entrySet()) {
                boolean subDidChange = false;
                String str = entry.getKey();
                Object obj = entry.getValue();
                if (obj instanceof Map) {
                    subDidChange |= traverseKeyMap(baseString + PATH_SEPARATOR + str, (Map<String, Object>) obj);
                } else if (obj instanceof Number) {
                    String normalizedKey = normalizePathStandard(baseString + PATH_SEPARATOR + str);
                    subDidChange |= !obj.equals(configMap.put(normalizedKey, obj));
                } else {
                    System.out.println("[Warning] YAML contains object of unknown type: " + obj.toString());
                }
                String normalizedBase = normalizePathStandard(baseString + PATH_SEPARATOR + str);
                if (subDidChange && listenerMap.containsKey(normalizedBase)) {
                    listenerMap.get(normalizedBase).forEach(Runnable::run);
                }
                didChange |= subDidChange;
            }
        } catch (NullPointerException ignored) {
        }
        return didChange;
    }

    synchronized Double getDouble(String key) {
        Object val = configMap.get(normalizePathStandard(key));
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
