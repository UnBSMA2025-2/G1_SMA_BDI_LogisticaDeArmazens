package com.unb.warehouse;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.core.Runtime;

public class App {
    public static void main(String[] args) throws Exception {
        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.GUI, "true");
        p.setParameter(Profile.MAIN_PORT, "1100");
        ContainerController cc = rt.createMainContainer(p);

        // cria 3 agentes com lat/lon diferentes
        AgentController a1 = cc.createNewAgent("wh1", "com.unb.warehouse.agents.WarehouseAgent", new Object[]{"wh1", "-23.55", "-46.63"});
        AgentController a2 = cc.createNewAgent("wh2", "com.unb.warehouse.agents.WarehouseAgent", new Object[]{"wh2", "-22.90", "-43.20"});
        AgentController a3 = cc.createNewAgent("wh3", "com.unb.warehouse.agents.WarehouseAgent", new Object[]{"wh3", "-19.92", "-43.94"});

        a1.start();
        a2.start();
        a3.start();
    }
}