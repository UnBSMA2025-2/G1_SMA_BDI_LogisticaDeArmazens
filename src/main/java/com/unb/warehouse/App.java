package com.unb.warehouse;

import com.unb.warehouse.config.ConfigLoader;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import org.json.JSONArray;
import org.json.JSONObject;

public class App {
    public static void main(String[] args) throws Exception {
        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.GUI, "true");
        p.setParameter(Profile.MAIN_PORT, "1100");
        ContainerController cc = rt.createMainContainer(p);

        JSONObject config = ConfigLoader.loadConfig();
        JSONArray warehousesConfig = config.getJSONArray("warehouses");
        JSONObject decisionWeights = config.getJSONObject("decisionWeights");

        for (int i = 0; i < warehousesConfig.length(); i++) {
            JSONObject whConfig = warehousesConfig.getJSONObject(i);
            String id = whConfig.getString("id");

            // Pass the specific config for the warehouse and the decision weights
            Object[] agentArgs = new Object[]{whConfig.toString(), decisionWeights.toString()};
            AgentController ac = cc.createNewAgent(id, "com.unb.warehouse.agents.WarehouseAgent", agentArgs);
            ac.start();
        }
    }
}