package com.unb.warehouse;

import com.unb.warehouse.config.ConfigLoader;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Application entry point for the Warehouse multi-agent system.
 *
 * <p>This class initializes the JADE runtime, loads the JSON configuration,
 * and creates one {@code WarehouseAgent} per warehouse entry in the config.
 * Each agent receives its warehouse-specific configuration and the global
 * decision weights as string arguments.</p>
 *
 * @author AlefMemTav
 */
public class App {
    /**
     * Main method.
     *
     * <p>Starts the JADE main container with GUI enabled on the configured port,
     * loads the application configuration, and spawns agents for each warehouse
     * defined in the configuration.</p>
     *
     * @param args command-line arguments (not used)
     * @throws Exception if the JADE runtime or agents fail to start
     */
    public static void main(String[] args) throws Exception {
        // Obtain the JADE runtime singleton
        Runtime rt = Runtime.instance();

        // Create and configure a JADE profile for the main container
        Profile p = new ProfileImpl();
        p.setParameter(Profile.GUI, "true");        // enable JADE GUI
        p.setParameter(Profile.MAIN_PORT, "1100"); // set main port

        // Create the main container using the configured profile
        ContainerController cc = rt.createMainContainer(p);

        // Load application configuration (expects JSON with "warehouses" and "decisionWeights")
        JSONObject config = ConfigLoader.loadConfig();
        JSONArray warehousesConfig = config.getJSONArray("warehouses");
        JSONObject decisionWeights = config.getJSONObject("decisionWeights");

        // Create and start a WarehouseAgent for each warehouse entry
        for (int i = 0; i < warehousesConfig.length(); i++) {
            JSONObject whConfig = warehousesConfig.getJSONObject(i);
            String id = whConfig.getString("id"); // agent name / warehouse identifier

            // Pass the warehouse configuration and global decision weights as arguments
            Object[] agentArgs = new Object[]{whConfig.toString(), decisionWeights.toString()};
            AgentController ac = cc.createNewAgent(id, "com.unb.warehouse.agents.WarehouseAgent", agentArgs);
            ac.start(); // start the agent
        }
    }
}