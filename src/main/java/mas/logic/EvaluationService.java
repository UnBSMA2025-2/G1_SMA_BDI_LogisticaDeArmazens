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
     * Método auxiliar para carregar TFNs do ConfigLoader para um mapa específico.
     */
    private void loadTfnsFromConfig(ConfigLoader config, String prefix, Map<String, double[]> map) {
        // Termos linguísticos definidos no config.properties (e Tabela 5 [cite: 457-460])
        String[] terms = {"very_poor", "poor", "medium", "good", "very_good"};
        for (String term : terms) {
            // Constrói a chave esperada no arquivo .properties (ex: tfn.buyer.very_poor)
            String key = "tfn." + prefix + "." + term;
            String value = config.getString(key); // Usa o ConfigLoader corrigido que remove comentários/espaços

            if (value != null && !value.isEmpty()) {
                String[] parts = value.split(",");
                if (parts.length == 3) {
                    try {
                        double m1 = Double.parseDouble(parts[0].trim());
                        double m2 = Double.parseDouble(parts[1].trim());
                        double m3 = Double.parseDouble(parts[2].trim());
                        // Armazena no mapa usando o termo sem underscore como chave (ex: "very poor")
                        map.put(term.replace("_", " "), new double[]{m1, m2, m3});
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


    /**
     * Calcula a utilidade agregada de um Bid para um tipo específico de agente.
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
            if (issue == null || issue.getName() == null) continue; // Pula issues inválidos

            String issueName = issue.getName().toLowerCase();
            double weight = weights.getOrDefault(issueName, 0.0);

            // Se o peso for zero, não adianta calcular a utilidade normalizada
            if (Math.abs(weight) < 1e-9) continue;

            IssueParameters params = issueParams.get(issueName);
            if (params == null) {
                //System.err.println("EvaluationService Warning: No parameters found for issue '" + issueName + "' for agent type '" + agentType + "'. Skipping issue.");
                continue;
            }

            double normalizedUtility = normalizeIssueUtility(agentType, issue, params, riskBeta);
            totalUtility += weight * normalizedUtility;
        }
         // Garante que a utilidade final esteja estritamente entre 0 e 1
         return Math.max(0.0, Math.min(1.0, totalUtility));
    }

    /** Normaliza a utilidade de um único issue, considerando o tipo de agente. */
    private double normalizeIssueUtility(String agentType, NegotiationIssue issue, IssueParameters params, double riskBeta) {
        Object value = issue.getValue();
        if (value == null) {
             System.err.println("EvaluationService Warning: Issue '" + issue.getName() + "' has null value. Returning utility 0.");
             return 0.0; // Valor nulo não tem utilidade
         }

        if (params.getType() == IssueType.QUALITATIVE) {
            if (value instanceof String) {
                // Passa o agentType para selecionar o mapa TFN correto
                return normalizeQualitativeUtility(agentType, (String) value);
            } else {
                 System.err.println("EvaluationService Error: Expected String value for qualitative issue '" + issue.getName() + "', but got " + value.getClass().getName());
                 return 0.0;
             }
        } else { // COST or BENEFIT
            if (value instanceof Number) {
                // A normalização quantitativa usa os params (min/max) que já são específicos do agente
                return normalizeQuantitativeUtility(((Number) value).doubleValue(), params, riskBeta);
            } else {
                 System.err.println("EvaluationService Error: Expected Number value for quantitative issue '" + issue.getName() + "', but got " + value.getClass().getName());
                 return 0.0;
             }
        }
    }

    /** Normaliza um issue qualitativo usando TFN (Eq. 3)[cite: 376], selecionando o mapa correto (buyer/seller). */
    private double normalizeQualitativeUtility(String agentType, String linguisticValue) {
        // Seleciona o mapa TFN correto com base no tipo de agente
        Map<String, double[]> tfnMap = agentType.equalsIgnoreCase("seller") ? this.tfnMapSeller : this.tfnMapBuyer;

        // Busca o TFN no mapa apropriado (convertendo para minúsculas para consistência)
        double[] tfn = tfnMap.get(linguisticValue.toLowerCase());

        if (tfn == null) {
            System.err.println("EvaluationService Warning: Unknown linguistic term '" + linguisticValue + "' for agent type '" + agentType + "'. Returning utility 0.");
            // Retorna 0 se o termo não for encontrado (pode indicar erro de digitação no config ou na proposta)
            return 0.0;
        }
        // Aplica a fórmula da Média de Integração Gradual (Eq. 3)
        return (tfn[0] + 4 * tfn[1] + tfn[2]) / 6.0;
    }


    /** Normaliza um issue quantitativo usando as Equações 1 e 2[cite: 319, 321]. */
    private double normalizeQuantitativeUtility(double value, IssueParameters params, double riskBeta) {
        double min = params.getMin();
        double max = params.getMax();
        double range = max - min;

        // Lida com o caso onde min == max para evitar divisão por zero
        if (Math.abs(range) < 1e-9) {
            // Se min=max, a utilidade é 1 se o valor for igual/maior(benefit) ou igual/menor(cost), 0 caso contrário.
            // Simplificando: se o valor estiver no "ponto" aceitável, utilidade máxima (1.0), senão mínima (v_min).
             // A utilidade mínima definida
             double v_min_boundary = 0.1;
             // Verifica se o valor está "no ponto" ou além dele na direção preferida
             if (params.getType() == IssueType.COST && value <= min) return 1.0;
             if (params.getType() == IssueType.BENEFIT && value >= min) return 1.0;
             return v_min_boundary; // Se estiver fora do ponto aceitável
        }

        double v_min = 0.1; // Utilidade mínima padrão

        // Garante que o valor está dentro dos limites [min, max]
        value = Math.max(min, Math.min(max, value));

        // Calcula a ratio (0 a 1) - Proporção de quão "bom" o valor é dentro do range
        double ratio;
        if (params.getType() == IssueType.COST) { // Menor é melhor
            ratio = (max - value) / range;
        } else { // BENEFIT - Maior é melhor
            ratio = (value - min) / range;
        }
        // Garante ratio estritamente entre 0 e 1
        ratio = Math.max(0.0, Math.min(1.0, ratio));

        // Garante que beta seja válido (positivo)
        if (riskBeta <= 0) {
             System.err.println("EvaluationService Warning: Invalid riskBeta (" + riskBeta + "). Using risk neutral (beta=1.0).");
             riskBeta = 1.0;
        }
         // Garante que v_min esteja em (0, 1) para a exponencial
         v_min = Math.max(0.001, Math.min(0.999, v_min));


        // Aplica a função de utilidade baseada no risco (Eq. 1 ou 2)
        if (riskBeta == 1.0) { // Caso especial: Neutro a risco (Linear)
             return v_min + (1 - v_min) * ratio;
        } else if (riskBeta < 1.0) { // Polinomial - Risco Propenso (Eq. 1) [cite: 319, 333]
             // Evita Math.pow(0, x) com x > 1 (resulta 0)
             if (ratio == 0.0) return v_min;
             return v_min + (1 - v_min) * Math.pow(ratio, 1.0 / riskBeta);
        } else { // Exponencial - Risco Averso (Eq. 2) [cite: 321, 335]
             // Evita Math.pow(0, beta) se ratio for 1
             if (ratio == 1.0) return 1.0;
             return Math.exp(Math.pow(1 - ratio, riskBeta) * Math.log(v_min));
        }
    }


    // --- Classes Internas (IssueParameters, IssueType) ---
    /** Classe auxiliar para armazenar os parâmetros de um issue de negociação. */
    public static class IssueParameters {
        private final double min, max; // Tornados final para imutabilidade
        private final IssueType type; // Tornado final

        public IssueParameters(double min, double max, IssueType type) {
            // Adiciona validação básica
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
        COST, // Menor valor é melhor (ex: Preço, Tempo de Entrega)
        BENEFIT, // Maior valor é melhor (ex: Desconto Percentual)
        QUALITATIVE // Valor é linguístico (ex: Qualidade, Serviço)
    }
}