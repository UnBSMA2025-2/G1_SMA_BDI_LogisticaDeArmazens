package com.unb.warehouse.behaviours;

import com.unb.warehouse.agents.WarehouseAgent;
import jade.core.AID;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import org.json.JSONObject;


public class StockMonitorBehaviour extends TickerBehaviour {
    private int tickCount = 0;

    public StockMonitorBehaviour(WarehouseAgent a, long period) {
        super(a, period);
    }

    @Override
    protected void onTick() {
        tickCount++;
        WarehouseAgent wa = (WarehouseAgent) myAgent;
        String sku = "SKU-123";
        int available = wa.getModel().getAvailable(sku); // trigger when low OR always on first tick to bootstrap
        if (available < 50 || tickCount == 1) {
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            cfp.setConversationId("supply-negotiation-" + System.currentTimeMillis());
            JSONObject content = new JSONObject();
            content.put("productId", sku);
            content.put("qty", 200);
            content.put("requester", wa.getModel().getId());
            cfp.setContent(content.toString());

            // dynamic broadcast: hardcoded list for demo
            for (String wh : new String[]{"wh1", "wh2", "wh3"}) {
                if (!wh.equals(wa.getModel().getId())) cfp.addReceiver(new AID(wh, AID.ISLOCALNAME));
            }
            myAgent.send(cfp);
            System.out.println(wa.getLocalName() + " sent CFP: needs=" + 200 + " avail=" + available);
        }

        // optional stop after N ticks for controlled tests
        if (tickCount >= 20) stop();
    }
}