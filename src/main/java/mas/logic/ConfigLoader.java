package mas.logic;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Classe utilitária para carregar configurações do arquivo config.properties.
 * Usa o padrão Singleton para garantir que o arquivo seja lido apenas uma vez.
 */
public class ConfigLoader {

    private static ConfigLoader instance = null;
    private Properties properties;

    private ConfigLoader() {
        properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.out.println("Sorry, unable to find config.properties");
                return;
            }
            properties.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static ConfigLoader getInstance() {
        if (instance == null) {
            instance = new ConfigLoader();
        }
        return instance;
    }

    public String getString(String key) {
        return properties.getProperty(key);
    }

    public double getDouble(String key) {
        return Double.parseDouble(properties.getProperty(key));
    }

    public int getInt(String key) {
        return Integer.parseInt(properties.getProperty(key));
    }
}