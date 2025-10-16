package com.unb.warehouse.agents;

import com.unb.warehouse.behaviours.*;
import com.unb.warehouse.model.WarehouseModel;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agent that represents a warehouse in the multi-agent system.
 *
 * <p>This agent:
 * <ul>
 *   <li>Initializes a {@link WarehouseModel} from a JSON configuration passed as an argument.</li>
 *   <li>Registers a "warehouse-service" with the Directory Facilitator (DF).</li>
 *   <li>Adds behaviours that monitor stock, respond to offers, make decisions, accept offers, and finalize transactions.</li>
 * </ul>
 *
 * <p>Expected agent arguments (in this order):
 * <ol>
 *   <li>String: JSON configuration for the warehouse (id, lat, lon, baseCost, initialStock, reorderPolicy).</li>
 *   <li>String: JSON configuration for decision weights used by {@code HandleDecisionBehaviour}.</li>
 * </ol>
 *
 * @author AlefMemTav
 */
public class WarehouseAgent extends Agent {
    private static final Logger log = LoggerFactory.getLogger(WarehouseAgent.class);

    /**
     * Domain model that holds warehouse state such as id, coordinates, stock levels and base cost.
     */
    private WarehouseModel model;

    /**
     * Decision weights parsed from the second agent argument. Used by decision-making behaviours.
     */
    private JSONObject decisionWeights;

    /**
     * Agent setup lifecycle method.
     *
     * <p>The method expects two arguments provided to the agent on startup:
     * the warehouse JSON configuration and the decision weights JSON string.
     * If arguments are missing or invalid the agent deregisters itself.
     *
     * <p>After initializing the {@link WarehouseModel} it registers its service with the DF and
     * attaches the following behaviours:
     * <ul>
     *   <li>{@link StockMonitorBehaviour}</li>
     *   <li>{@link OfferResponderBehaviour}</li>
     *   <li>{@link HandleDecisionBehaviour}</li>
     *   <li>{@link HandleAcceptanceBehaviour}</li>
     *   <li>{@link HandleTransactionFinalizationBehaviour}</li>
     * </ul>
     */
    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args == null || args.length < 2) {
            log.error("Agent {} requires two arguments: its JSON config and the decision weights JSON config.", getLocalName());
            doDelete();
            return;
        }

        JSONObject whConfig = new JSONObject((String) args[0]);
        this.decisionWeights = new JSONObject((String) args[1]);

        // Initialize model from configuration
        this.model = new WarehouseModel(
                whConfig.getString("id"),
                whConfig.getDouble("lat"),
                whConfig.getDouble("lon")
        );
        model.setBaseCost(whConfig.getDouble("baseCost"));
        JSONObject stock = whConfig.getJSONObject("initialStock");
        for (String sku : stock.keySet()) {
            model.setStock(sku, stock.getInt(sku));
        }

        registerService();

        JSONObject reorderPolicy = whConfig.getJSONObject("reorderPolicy");
        addBehaviour(new StockMonitorBehaviour(this, 5000, model, reorderPolicy));
        addBehaviour(new OfferResponderBehaviour(this, model, reorderPolicy));
        addBehaviour(new HandleDecisionBehaviour(this, model, decisionWeights));
        addBehaviour(new HandleAcceptanceBehaviour(this, model));
        addBehaviour(new HandleTransactionFinalizationBehaviour(this, model));

        log.info("{} ready. coords={},{} stock(SKU-123)={} baseCost={}",
                model.getId(), model.getLat(), model.getLon(), model.getStock("SKU-123"), model.getBaseCost());
    }

    /**
     * Registers this agent's service with the Directory Facilitator (DF).
     *
     * <p>The service type is \"warehouse-service\" and the service name is formed by
     * appending \"-warehouse-service\" to the agent local name.
     *
     * <p>Logs success or failure to the configured logger.
     */
    private void registerService() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("warehouse-service");
        sd.setName(getLocalName() + "-warehouse-service");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            log.info("Agent {} registered service 'warehouse-service'", getLocalName());
        } catch (FIPAException fe) {
            log.error("Failed to register service for agent {}: {}", getLocalName(), fe.getMessage());
        }
    }

    /**
     * Agent takedown lifecycle method.
     *
     * <p>Attempts to deregister the agent from the DF and logs the outcome.
     */
    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
            log.info("Agent {} deregistered from DF.", getLocalName());
        } catch (FIPAException fe) {
            log.error("Failed to deregister agent {}: {}", getLocalName(), fe.getMessage());
        }
    }
}