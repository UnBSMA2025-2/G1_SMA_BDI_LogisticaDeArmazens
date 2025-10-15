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

        JSONObject cfg = ConfigLoader.loadConfig();
        JSONArray whs = cfg.getJSONArray("warehouses");

        for (int i = 0; i < whs.length(); i++) {
            JSONObject w = whs.getJSONObject(i);
            String id = w.getString("id");
            String lat = String.valueOf(w.getDouble("lat"));
            String lon = String.valueOf(w.getDouble("lon"));
            AgentController ac = cc.createNewAgent(id, "com.unb.warehouse.agents.WarehouseAgent", new Object[]{id, lat, lon});
            ac.start();
        }
    }
}