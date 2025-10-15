package com.unb.warehouse.behaviours;

import jade.core.behaviours.TickerBehaviour;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import org.json.JSONObject;

import com.unb.warehouse.agents.WarehouseAgent;

public class StockMonitorBehaviour extends TickerBehaviour {
    public StockMonitorBehaviour(WarehouseAgent a, long period) {
        super(a, period);
    }

    @Override
    protected void onTick() {
        WarehouseAgent wa = (WarehouseAgent) myAgent;
        String sku = "SKU-123";
        int available = wa.getAvailable(sku);
        if (available < 50 || getTickCount() == 1) {
            // cria CFP e broadcast para todos (usamos "*" via Directory in a real system)
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            cfp.setConversationId("supply-negotiation-" + System.currentTimeMillis());
            JSONObject content = new JSONObject();
            content.put("productId", sku);
            content.put("qty", 200);
            content.put("requester", wa.getWarehouseId());
            cfp.setContent(content.toString());

             // broadcast: em simulação, enviamos para todos agentes conhecidos via AMS naming (simplificado)
            for (String wh : new String[]{"wh1", "wh2", "wh3"}) {
                if (!wh.equals(wa.getWarehouseId())) {
                    cfp.addReceiver(new AID(wh, AID.ISLOCALNAME));
                }
            }
            myAgent.send(cfp);

            System.out.println(wa.getLocalName() + " sent CFP: needs=" + 200);
        }
    }
}