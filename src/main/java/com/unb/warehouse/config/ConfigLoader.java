package com.unb.warehouse.config;

import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Paths;

public class ConfigLoader {
    public static JSONObject loadConfig() {
        try {
            String path = "resources/warehouses.json"; // relative when running from project root
            if (!Files.exists(Paths.get(path))) { // fallback to classpath
                path = Thread.currentThread().getContextClassLoader().getResource("warehouses.json").getPath();
            }
            String content = new String(Files.readAllBytes(Paths.get(path)));
            return new JSONObject(content);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config: " + e.getMessage(), e);
        }
    }
}