package agents;

import jade.core.Agent;

public class DummyAgent extends Agent {
    protected void setup() {
        System.out.println("Olá, mundo! Eu sou o agente " + getAID().getName());
    }
}