package mas.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import mas.logic.EvaluationService.IssueParameters;
import mas.logic.EvaluationService.IssueType;
import mas.models.Bid;
import mas.models.NegotiationIssue;

public class ConcessionService {

    /**
     * Gera um contra-lance (Bid) para a próxima rodada de negociação.
     */
    public Bid generateCounterBid(Bid referenceBid, int currentRound, int maxRounds, double gamma,
                                  double discountRate, Map<String, IssueParameters> issueParams, String agentType) {

        List<NegotiationIssue> counterIssues = new ArrayList<>();

        for (NegotiationIssue issue : referenceBid.getIssues()) {
            String issueName = issue.getName().toLowerCase();
            IssueParameters params = issueParams.get(issueName);
            if (params == null) {
                //System.err.println("Warning: No parameters found for issue '" + issueName + "' during concession.");
                // O que fazer? Manter o valor antigo? Pular? Adicionar valor padrão?
                // Por ora, vamos manter o valor antigo se não houver params.
                 counterIssues.add(new NegotiationIssue(issue.getName(), issue.getValue()));
                continue;
            }

            double concessionRate = calculateConcessionRate(currentRound, maxRounds, gamma, discountRate);

            Object newValue;
            if (params.getType() == IssueType.QUALITATIVE) {
                newValue = mapConcessionToQualitative(concessionRate, agentType);
            } else {
                // CORREÇÃO: Passa o agentType para calculateNewQuantitativeValue
                newValue = calculateNewQuantitativeValue(concessionRate, params.getMin(), params.getMax(), params.getType(), agentType);
            }

            counterIssues.add(new NegotiationIssue(issue.getName(), newValue));
        }

        return new Bid(referenceBid.getProductBundle(), counterIssues, referenceBid.getQuantities());
    }

    /**
     * Calcula a taxa de concessão α(t) usando a Equação 5.
     */
    private double calculateConcessionRate(int t, int t_max, double gamma, double b_k) {
        if (t > t_max) t = t_max;
        if (t <= 0) t = 1;
        // Evita divisão por zero se t_max for 1 ou menos
         double timeRatio = (t_max <= 1) ? 1.0 : (double)(t - 1) / (t_max - 1);


        // Garante b_k (0, 1)
        b_k = Math.max(0.001, Math.min(0.999, b_k));
         // Garante gamma > 0
         gamma = Math.max(0.001, gamma);


        if (gamma <= 1.0) { // Polinomial (Eq. 5, parte 1) [cite: 407]
             return b_k + (1 - b_k) * Math.pow(timeRatio, 1.0 / gamma);
        } else { // Exponencial (Eq. 5, parte 2) [cite: 407]
            if (timeRatio == 1.0) return 1.0; // Evita Math.pow(0, gamma)
            return Math.exp(Math.pow(1.0 - timeRatio, gamma) * Math.log(b_k));
        }
        // Não precisamos de fallback linear, pois garantimos gamma > 0
    }

    /**
     * Calcula o novo valor para um issue quantitativo usando a Equação 6,
     * considerando o tipo de agente e o tipo de issue (cost/benefit).
     */
    private double calculateNewQuantitativeValue(double concessionRate, double min_k, double max_k, IssueType type, String agentType) {
        // Equação 6 base: NovoValor = LimiteInicial +/- Concessão * (RangeTotal) [cite: 410-411]
        double range = max_k - min_k;

        // Se min == max, apenas retorna o valor (evita NaN se range for 0)
        if (Math.abs(range) < 1e-9) {
            return min_k;
        }


        double newValue;
        if (agentType.equalsIgnoreCase("buyer")) {
            // Comprador concede em direção a valores MENOS preferíveis para ele
            if (type == IssueType.BENEFIT) { // Cede diminuindo o benefício (vai do max para o min)
                newValue = max_k - concessionRate * range;
            } else { // COST - Cede aumentando o custo (vai do min para o max)
                newValue = min_k + concessionRate * range;
            }
        } else { // "seller"
            // Vendedor concede em direção a valores MAIS preferíveis para o comprador
            if (type == IssueType.BENEFIT) { // Cede aumentando o benefício (vai do min para o max)
                newValue = min_k + concessionRate * range;
            } else { // COST - Cede diminuindo o custo (vai do max para o min)
                newValue = max_k - concessionRate * range;
            }
        }
         // Garante que o novo valor permaneça dentro dos limites min/max
         return Math.max(min_k, Math.min(max_k, newValue));
    }


    /** Mapeia a taxa de concessão (0..1) para um valor linguístico. */
     private String mapConcessionToQualitative(double concessionRate, String agentType) {
         // Concessão 0 = Ponto de partida (Melhor para si)
         // Concessão 1 = Concessão máxima (Pior para si / Melhor para o outro)

         double targetValue; // Valor alvo na escala 0..1 (0=VP, 1=VG)
         if (agentType.equalsIgnoreCase("buyer")) {
             // Comprador começa querendo VG (valor ~1) e cede para VP (valor ~0)
             targetValue = 1.0 - concessionRate;
         } else { // seller
             // Vendedor começa oferecendo VP (valor ~0) e cede para VG (valor ~1)
             targetValue = concessionRate;
         }

         // Mapeia targetValue para termos
         if (targetValue < 0.1) return "very poor";     // 0.0 - 0.1
         else if (targetValue < 0.3) return "poor";    // 0.1 - 0.3
         else if (targetValue < 0.7) return "medium";  // 0.3 - 0.7 (Faixa maior para médio)
         else if (targetValue < 0.9) return "good";    // 0.7 - 0.9
         else return "very good"; // 0.9 - 1.0
     }
}