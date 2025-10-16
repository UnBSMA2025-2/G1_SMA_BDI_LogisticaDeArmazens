package com.unb.warehouse.config;

import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Utility class responsible for loading application configuration from a JSON file.
 *
 * <p>Loading strategy:
 * <ol>
 *   <li>Attempt to read a file at the relative path {@code resources/warehouses.json} when running
 *       from the project root.</li>
 *   <li>If the relative file does not exist, attempt to load {@code warehouses.json} from the
 *       application's classpath.</li>
 * </ol>
 *
 * <p>On success this class returns an {@link JSONObject} representing the parsed configuration.
 * Any error encountered while locating or reading the file is wrapped in a {@link RuntimeException}.
 *
 * @author AlefMemTav
 */
public class ConfigLoader {

    /**
     * Loads the warehouses configuration JSON.
     *
     * @return a {@link JSONObject} containing the parsed configuration
     * @throws RuntimeException if the configuration file cannot be found, read or parsed
     */
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