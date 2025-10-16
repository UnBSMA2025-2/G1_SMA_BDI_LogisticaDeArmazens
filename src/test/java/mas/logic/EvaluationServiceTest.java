package mas.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;     
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mas.logic.EvaluationService.IssueParameters;
import mas.logic.EvaluationService.IssueType;
import mas.models.Bid;
import mas.models.NegotiationIssue;
import mas.models.ProductBundle;

public class EvaluationServiceTest {

    private EvaluationService evaluationService;
    private Bid testBid;
    private Map<String, Double> weights;
    private Map<String, IssueParameters> issueParams;

    @BeforeEach
    public void setUp() {
        evaluationService = new EvaluationService();

        // 1. Criar um Bid fixo para os testes
        ProductBundle pb = new ProductBundle(new int[]{1, 0, 0, 0});
        List<NegotiationIssue> issues = new ArrayList<>();
        issues.add(new NegotiationIssue("Price", 55.0)); // Valor a ser avaliado
        issues.add(new NegotiationIssue("Quality", "good"));
        issues.add(new NegotiationIssue("Delivery", 8.0));
        issues.add(new NegotiationIssue("Service", "medium"));
        testBid = new Bid(pb, issues, new int[]{1000, 0, 0, 0});

        // 2. Definir os pesos (ωk) - devem somar 1.0
        weights = new HashMap<>();
        weights.put("price", 0.4);
        weights.put("quality", 0.3);
        weights.put("delivery", 0.15);
        weights.put("service", 0.15);

        // 3. Definir os parâmetros [min, max] para cada issue quantitativo
        // Valores baseados na Tabela 3 do artigo para o pacote "1000" [cite: 442]
        issueParams = new HashMap<>();
        issueParams.put("price", new IssueParameters(50.0, 60.0, IssueType.COST));
        issueParams.put("delivery", new IssueParameters(1.0, 10.0, IssueType.COST));
        issueParams.put("quality", new IssueParameters(0, 0, IssueType.QUALITATIVE));
        issueParams.put("service", new IssueParameters(0, 0, IssueType.QUALITATIVE));
    }

    @Test
    void testCalculateUtility_RiskProne() {
        double beta = 0.5; // Agente propenso a risco
        double utility = evaluationService.calculateUtility(testBid, weights, issueParams, beta);
        
        // Cálculo manual para verificação:
        // Price (β=0.5, cost): V = 0.1 + (1-0.1) * ((60-55)/(60-50))^(1/0.5) = 0.325
        // Quality ("good"): V = (0.5 + 4*0.75 + 1)/6 = 0.75
        // Delivery (β=0.5, cost): V = 0.1 + (1-0.1) * ((10-8)/(10-1))^(1/0.5) = 0.1444...
        // Service ("medium"): V = (0.25 + 4*0.5 + 0.75)/6 = 0.5
        // Total: U = 0.4*0.325 + 0.3*0.75 + 0.15*0.1444 + 0.15*0.5 = 0.45166
        assertEquals(0.45166, utility, 0.0001);
    }

    @Test
    void testCalculateUtility_RiskNeutral() {
        double beta = 1.0; // Agente neutro a risco
        double utility = evaluationService.calculateUtility(testBid, weights, issueParams, beta);

        // Cálculo manual para verificação:
        // Price (β=1.0, cost): V = 0.1 + (1-0.1) * ((60-55)/(60-50))^(1/1) = 0.55
        // Quality e Service não mudam.
        // Delivery (β=1.0, cost): V = 0.1 + (1-0.1) * ((10-8)/(10-1))^(1/1) = 0.3
        // Total: U = 0.4*0.55 + 0.3*0.75 + 0.15*0.3 + 0.15*0.5 = 0.565
        assertEquals(0.565, utility, 0.0001);
    }

    @Test
    void testCalculateUtility_RiskAverse() {
        double beta = 2.0; // Agente avesso a risco
        double utility = evaluationService.calculateUtility(testBid, weights, issueParams, beta);
        
        // Cálculo manual para verificação:
        // Price (β=2.0, cost, exp): V = exp( ((1-(60-55)/(60-50))^2) * ln(0.1) ) = 0.5623...
        // Quality e Service não mudam.
        // Delivery (β=2.0, cost, exp): V = exp( ((1-(10-8)/(10-1))^2) * ln(0.1) ) = 0.254...
        // Total: U = 0.4*0.5623 + 0.3*0.75 + 0.15*0.254 + 0.15*0.5 = 0.56302
        assertEquals(0.56218, utility, 0.0001);
    }
}