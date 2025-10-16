package mas.logic;

import java.util.HashMap;
import java.util.Map;

import mas.models.Bid;
import mas.models.NegotiationIssue;

/**
 * Contém a lógica de negócio para avaliar lances (Bids).
 * Esta classe é "pura", não tem dependências do framework JADE.
 * Implementa as equações de avaliação de utilidade do artigo.
 */
public class EvaluationService {

    // Mapeia os termos linguísticos para seus Números Fuzzy Triangulares (TFNs)
    // Conforme Tabela 5 do artigo (visão da empresa compradora)[cite: 458, 460].
    private static final Map<String, double[]> TFN_MAP = new HashMap<>();
    static {
        TFN_MAP.put("very poor", new double[]{0, 0, 0.25});
        TFN_MAP.put("poor", new double[]{0, 0.25, 0.5});
        TFN_MAP.put("medium", new double[]{0.25, 0.5, 0.75});
        TFN_MAP.put("good", new double[]{0.5, 0.75, 1});
        TFN_MAP.put("very good", new double[]{0.75, 1, 1});
    }

    /**
     * Calcula a utilidade agregada de um Bid.
     * Implementa a Equação 4: U(Bid) = Σ (ωk * V(Ik)).
     *
     * @param bid O lance a ser avaliado.
     * @param weights Mapa com os pesos (ωk) para cada issue.
     * @param issueParams Mapa com os parâmetros (min, max, tipo) para cada issue.
     * @param riskBeta O fator de preferência de risco (β) do agente.
     * @return A utilidade total do lance, um valor entre 0 e 1.
     */
    public double calculateUtility(Bid bid, Map<String, Double> weights,
                                   Map<String, IssueParameters> issueParams, double riskBeta) {
        double totalUtility = 0.0;
        
        for (NegotiationIssue issue : bid.getIssues()) {
            String issueName = issue.getName().toLowerCase();
            double weight = weights.getOrDefault(issueName, 0.0);
            
            IssueParameters params = issueParams.get(issueName);
            if (params == null) continue; // Ignora issues sem parâmetros definidos

            double normalizedUtility = normalizeIssueUtility(issue, params, riskBeta);
            totalUtility += weight * normalizedUtility;
        }

        return totalUtility;
    }

    /**
     * Normaliza a utilidade de um único issue para uma escala de 0-1.
     */
    private double normalizeIssueUtility(NegotiationIssue issue, IssueParameters params, double riskBeta) {
        if (params.getType() == IssueType.QUALITATIVE) {
            return normalizeQualitativeUtility((String) issue.getValue());
        } else {
            double value = ((Number) issue.getValue()).doubleValue();
            return normalizeQuantitativeUtility(value, params, riskBeta);
        }
    }

    /**
     * Normaliza um issue qualitativo usando TFN e a Equação 3.
     * V(Ik) = (m1 + 4*m2 + m3) / 6.
     */
    private double normalizeQualitativeUtility(String linguisticValue) {
        double[] tfn = TFN_MAP.get(linguisticValue.toLowerCase());
        if (tfn == null) {
            return 0.0; // Valor padrão se o termo linguístico for desconhecido
        }
        return (tfn[0] + 4 * tfn[1] + tfn[2]) / 6.0;
    }

    /**
     * Normaliza um issue quantitativo usando as Equações 1 e 2.
     * A escolha da equação depende do fator de risco β[cite: 333, 335].
     */
    private double normalizeQuantitativeUtility(double value, IssueParameters params, double riskBeta) {
        double min = params.getMin();
        double max = params.getMax();
        double v_min = 0.1; // Utilidade mínima, conforme exemplos do artigo.

        // Garante que o valor está dentro dos limites para evitar erros de cálculo
        value = Math.max(min, Math.min(max, value));

        if (riskBeta <= 1.0) { // Polinomial - Equação 1 
            double ratio = (params.getType() == IssueType.COST)
                ? (max - value) / (max - min)
                : (value - min) / (max - min);
            return v_min + (1 - v_min) * Math.pow(ratio, 1.0 / riskBeta);
        } else { // Exponencial - Equação 2 
            double ratio = (params.getType() == IssueType.COST)
                ? (max - value) / (max - min)
                : (value - min) / (max - min);
            return Math.exp(Math.pow(1 - ratio, riskBeta) * Math.log(v_min));
        }
    }

    /**
     * Classe auxiliar para armazenar os parâmetros de um issue de negociação.
     */
    public static class IssueParameters {
        private double min, max;
        private IssueType type;

        public IssueParameters(double min, double max, IssueType type) {
            this.min = min;
            this.max = max;
            this.type = type;
        }
        public double getMin() { return min; }
        public double getMax() { return max; }
        public IssueType getType() { return type; }
    }

    public enum IssueType {
        COST, // Menor é melhor (ex: Preço, Entrega)
        BENEFIT, // Maior é melhor
        QUALITATIVE
    }
}