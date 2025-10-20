package mas.logic;

import java.util.HashMap;
import java.util.Map;

import mas.models.Bid;
import mas.models.NegotiationIssue;

/**
 * Contém a lógica de negócio para avaliar lances (Bids).
 * Carrega TFNs do ConfigLoader e avalia da perspectiva do 'buyer' ou 'seller'.
 * Implementa as equações de avaliação de utilidade do artigo [cite: 307-308].
 */
public class EvaluationService {

    // Mapas para armazenar TFNs carregados do arquivo de configuração
    private final Map<String, double[]> tfnMapBuyer;
    private final Map<String, double[]> tfnMapSeller;

    /**
     * Construtor que carrega os TFNs do ConfigLoader.
     */
    public EvaluationService() {
        this.tfnMapBuyer = new HashMap<>();
        this.tfnMapSeller = new HashMap<>();
        ConfigLoader config = ConfigLoader.getInstance();
        loadTfnsFromConfig(config, "buyer", this.tfnMapBuyer);
        loadTfnsFromConfig(config, "seller", this.tfnMapSeller);
    }

    /**
     * Backwards-compatible overload: mantém a assinatura antiga usada por testes/consumidores
     * que não informam o agentType. Por padrão assume 'buyer'.
     * @deprecated Preferir a versão com agentType explícito.
     */
    @Deprecated
    public double calculateUtility(Bid bid, Map<String, Double> weights,
                                   Map<String, IssueParameters> issueParams, double riskBeta) {
        return calculateUtility("buyer", bid, weights, issueParams, riskBeta);
    }

    /**
     * Método principal: Calcula a utilidade agregada de um Bid para um tipo específico de agente.
     * Implementa a Equação 4 [cite: 384-386].
     * @param agentType "buyer" ou "seller", para selecionar os TFNs corretos.
     * @param bid O lance a ser avaliado.
     * @param weights Mapa com os pesos (ωk) para cada issue (perspectiva do agente).
     * @param issueParams Mapa com os parâmetros (min, max, tipo) para cada issue (perspectiva do agente).
     * @param riskBeta O fator de preferência de risco (β) do agente [cite: 332-335].
     * @return A utilidade total do lance para o agente especificado (valor entre 0 e 1).
     */
    public double calculateUtility(String agentType, Bid bid, Map<String, Double> weights,
                                   Map<String, IssueParameters> issueParams, double riskBeta) {
        double totalUtility = 0.0;

        if (bid == null || bid.getIssues() == null) {
            System.err.println("EvaluationService: Cannot calculate utility for null bid or bid with null issues.");
            return 0.0;
        }
        if (weights == null || issueParams == null) {
            System.err.println("EvaluationService: Cannot calculate utility with null weights or issueParams.");
            return 0.0;
        }

        for (NegotiationIssue issue : bid.getIssues()) {
            if (issue == null || issue.getName() == null) continue;

            String issueName = issue.getName().toLowerCase();
            double weight = weights.getOrDefault(issueName, 0.0);

            if (Math.abs(weight) < 1e-9) continue;

            IssueParameters params = issueParams.get(issueName);
            if (params == null) {
                continue;
            }

            double normalizedUtility = normalizeIssueUtility(agentType, issue, params, riskBeta);
            totalUtility += weight * normalizedUtility;
        }
        return Math.max(0.0, Math.min(1.0, totalUtility));
    }

    /** Normaliza a utilidade de um único issue, considerando o tipo de agente. */
    private double normalizeIssueUtility(String agentType, NegotiationIssue issue, IssueParameters params, double riskBeta) {
        Object value = issue.getValue();
        if (value == null) {
            System.err.println("EvaluationService Warning: Issue '" + issue.getName() + "' has null value. Returning utility 0.");
            return 0.0;
        }

        if (params.getType() == IssueType.QUALITATIVE) {
            if (value instanceof String) {
                return normalizeQualitativeUtility(agentType, (String) value);
            } else {
                System.err.println("EvaluationService Error: Expected String value for qualitative issue '" + issue.getName() + "', but got " + value.getClass().getName());
                return 0.0;
            }
        } else {
            if (value instanceof Number) {
                return normalizeQuantitativeUtility(((Number) value).doubleValue(), params, riskBeta);
            } else {
                System.err.println("EvaluationService Error: Expected Number value for quantitative issue '" + issue.getName() + "', but got " + value.getClass().getName());
                return 0.0;
            }
        }
    }

    /** Normaliza um issue qualitativo usando TFN (Eq. 3)[cite: 376], selecionando o mapa correto (buyer/seller). */
    private double normalizeQualitativeUtility(String agentType, String linguisticValue) {
        Map<String, double[]> tfnMap = agentType.equalsIgnoreCase("seller") ? this.tfnMapSeller : this.tfnMapBuyer;

        // Normaliza o valor linguístico recebido: converte underscores para espaços, trim e lowercase
        String lookupKey = linguisticValue.replace("_", " ").trim().toLowerCase();

        double[] tfn = tfnMap.get(lookupKey);
        // Fallbacks: tenta outras formas se necessário
        if (tfn == null) {
            // tenta com underscore
            String altUnderscore = lookupKey.replace(" ", "_");
            tfn = tfnMap.get(altUnderscore);
        }
        if (tfn == null) {
            // tenta sem espaços/underscores (apenas por segurança)
            String compact = lookupKey.replace(" ", "");
            tfn = tfnMap.get(compact);
        }
        if (tfn == null) {
            System.err.println("EvaluationService Warning: Unknown linguistic term '" + linguisticValue + "' for agent type '" + agentType + "'. Returning utility 0.");
            return 0.0;
        }
        return (tfn[0] + 4 * tfn[1] + tfn[2]) / 6.0;
    }

    /** Normaliza um issue quantitativo usando as Equações 1 e 2[cite: 319, 321]. */
    private double normalizeQuantitativeUtility(double value, IssueParameters params, double riskBeta) {
        double min = params.getMin();
        double max = params.getMax();
        double range = max - min;

        if (Math.abs(range) < 1e-9) {
            double v_min_boundary = 0.1;
            if (params.getType() == IssueType.COST && value <= min) return 1.0;
            if (params.getType() == IssueType.BENEFIT && value >= min) return 1.0;
            return v_min_boundary;
        }

        double v_min = 0.1;

        value = Math.max(min, Math.min(max, value));

        double ratio;
        if (params.getType() == IssueType.COST) {
            ratio = (max - value) / range;
        } else {
            ratio = (value - min) / range;
        }
        ratio = Math.max(0.0, Math.min(1.0, ratio));

        if (riskBeta <= 0) {
            System.err.println("EvaluationService Warning: Invalid riskBeta (" + riskBeta + "). Using risk neutral (beta=1.0).");
            riskBeta = 1.0;
        }
        v_min = Math.max(0.001, Math.min(0.999, v_min));

        if (riskBeta == 1.0) {
            return v_min + (1 - v_min) * ratio;
        } else if (riskBeta < 1.0) {
            if (ratio == 0.0) return v_min;
            return v_min + (1 - v_min) * Math.pow(ratio, 1.0 / riskBeta);
        } else {
            if (ratio == 1.0) return 1.0;
            return Math.exp(Math.pow(1 - ratio, riskBeta) * Math.log(v_min));
        }
    }

    // --- Classes Internas (IssueParameters, IssueType) ---
    /** Classe auxiliar para armazenar os parâmetros de um issue de negociação. */
    public static class IssueParameters {
        private final double min, max;
        private final IssueType type;

        public IssueParameters(double min, double max, IssueType type) {
            if (type != IssueType.QUALITATIVE && min > max) {
                System.err.println("Warning: IssueParameters created with min (" + min + ") > max (" + max + "). Swapping them.");
                this.min = max;
                this.max = min;
            } else {
                this.min = min;
                this.max = max;
            }
            this.type = type;
        }
        public double getMin() { return min; }
        public double getMax() { return max; }
        public IssueType getType() { return type; }
    }

    /** Enumeração para os tipos de critério de negociação. */
    public enum IssueType {
        COST,
        BENEFIT,
        QUALITATIVE
    }

    /**
     * Método auxiliar para carregar TFNs do ConfigLoader para um mapa específico.
     */
    private void loadTfnsFromConfig(ConfigLoader config, String prefix, Map<String, double[]> map) {
        String[] terms = {"very_poor", "poor", "medium", "good", "very_good"};
        for (String term : terms) {
            String key = "tfn." + prefix + "." + term;
            String value = config.getString(key);

            if (value != null && !value.isEmpty()) {
                String[] parts = value.split(",");
                if (parts.length == 3) {
                    try {
                        double m1 = Double.parseDouble(parts[0].trim());
                        double m2 = Double.parseDouble(parts[1].trim());
                        double m3 = Double.parseDouble(parts[2].trim());
                        // Armazena várias formas da chave: com espaço e com underscore, todas em lowercase
                        String withSpace = term.replace("_", " ").toLowerCase(); // "very poor"
                        String withUnderscore = term.toLowerCase(); // "very_poor"
                        String compact = withSpace.replace(" ", ""); // "verypoor" (fallback)
                        double[] arr = new double[]{m1, m2, m3};
                        map.put(withSpace, arr);
                        map.put(withUnderscore, arr);
                        map.put(compact, arr);
                    } catch (NumberFormatException e) {
                        System.err.println("EvaluationService: Error parsing TFN from config for key '" + key + "', value: '" + value + "'");
                    }
                } else {
                    System.err.println("EvaluationService: Invalid TFN format in config for key '" + key + "', expected 3 parts separated by comma, got: '" + value + "'");
                }
            } else {
                System.err.println("EvaluationService: Missing TFN configuration for key '" + key + "'");
            }
        }
    }
}

