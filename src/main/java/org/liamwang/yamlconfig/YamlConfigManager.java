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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.io.FilenameUtils;
import org.yaml.snakeyaml.Yaml;

public class YamlConfigManager {

    private static YamlConfigManager instance = new YamlConfigManager();

    private final Object lock = new Object();
    private Map<String, Object> configMap = new LinkedHashMap<>();

    private YamlConfigManager() {
        String path = "yaml";
        try {
            Files.walk(Paths.get(path))
                .filter(Files::isRegularFile)
                .forEach(this::update);
            new WatchDir(Paths.get(path), true, this::update).processEvents();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static YamlConfigManager getInstance() {
        return instance;
    }

    public void update(Path path) {
        synchronized (lock) {
            if (!FilenameUtils.isExtension(path.toString(), "yaml")) {
                return;
            }
            System.out.println("Yaml file updated, updating config: " + path.toString());
            Yaml yaml = new Yaml();
            try {
                InputStream input = new FileInputStream(new File(path.toUri()));
                @SuppressWarnings("unchecked")
                Map<String, Object> map = yaml.load(input);
                traverseKeyMap("", map);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void traverseKeyMap(String baseString, Map<String, Object> map) {
        try {
            map.forEach((String str, Object obj) -> {
                if (obj instanceof Map) {
                    traverseKeyMap(baseString + "/" + str, (Map<String, Object>) obj);
                } else if (obj instanceof Double || obj instanceof Float || obj instanceof Integer) {
                    System.out.println(baseString + "/" + str + " " + obj);
                }
            });
        } catch (NullPointerException e) {
        }
    }
}
