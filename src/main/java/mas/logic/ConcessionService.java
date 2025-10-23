package mas.logic;

import mas.logic.EvaluationService.IssueParameters;
import mas.logic.EvaluationService.IssueType;
import mas.models.Bid;
import mas.models.NegotiationIssue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Contém a lógica de negócio para concessão (barganha).
 * Gera novos lances (Bids) com base nas táticas de concessão do artigo.
 * Implementa as equações de geração de lance (Eq. 5 e 6).
 */
public class ConcessionService {

    /**
     * Gera um contra-lance (Bid) para a próxima rodada de negociação.
     *
     * @param referenceBid O lance anterior (para saber o ProductBundle e os issues).
     * @param currentRound A rodada atual (t).
     * @param maxRounds    O deadline (t_max).
     * @param gamma        O fator de concessão (γ).
     * @param discountRate O fator de desconto (b_k).
     * @param issueParams  Os parâmetros (min, max) para os issues.
     * @param agentType    "buyer" ou "seller", para a direção da concessão.
     * @return Um novo Bid com valores de issues recalculados.
     */
    public Bid generateCounterBid(Bid referenceBid, int currentRound, int maxRounds, double gamma,
                                  double discountRate, Map<String, IssueParameters> issueParams, String agentType) {

        List<NegotiationIssue> counterIssues = new ArrayList<>();

        // TODO (SINERGIA): O 'issueParams' recebido aqui é genérico.
        // A implementação correta exige que o AGENTE (Buyer/Seller)
        // passe um 'issueParams' específico para o 'referenceBid.getProductBundle()'.
        // O ConcessionService está cego para a sinergia, pois seus [min, max]
        // são os mesmos para todos os pacotes.

        for (NegotiationIssue issue : referenceBid.getIssues()) {
            String issueName = issue.getName().toLowerCase();
            IssueParameters params = issueParams.get(issueName); // Pega params genéricos
            if (params == null) {
                counterIssues.add(new NegotiationIssue(issue.getName(), issue.getValue()));
                continue;
            }

            // Calcula a taxa de concessão (Eq. 5)
            double concessionRate = calculateConcessionRate(currentRound, maxRounds, gamma, discountRate);

            Object newValue;
            if (params.getType() == IssueType.QUALITATIVE) {
                // Mapeia a taxa de concessão (0..1) para um valor linguístico
                newValue = mapConcessionToQualitative(concessionRate, agentType);
            } else {
                // Calcula o novo valor quantitativo (Eq. 6)
                newValue = calculateNewQuantitativeValue(concessionRate, params.getMin(), params.getMax(), params.getType(), agentType);
            }

            counterIssues.add(new NegotiationIssue(issue.getName(), newValue));
        }

        // Retorna um novo lance para o MESMO ProductBundle e Quantidades
        return new Bid(referenceBid.getProductBundle(), counterIssues, referenceBid.getQuantities());
    }

    /**
     * Calcula a taxa de concessão α(t) usando a Equação 5.
     * Esta implementação está CORRETA.
     */
    private double calculateConcessionRate(int t, int t_max, double gamma, double b_k) {
        if (t > t_max) t = t_max;
        if (t <= 0) t = 1;

        double timeRatio = (t_max <= 1) ? 1.0 : (double) (t - 1) / (t_max - 1);

        b_k = Math.max(0.001, Math.min(0.999, b_k));
        gamma = Math.max(0.001, gamma);

        if (gamma <= 1.0) { // Polinomial (Eq. 5, parte 1)
            return b_k + (1 - b_k) * Math.pow(timeRatio, 1.0 / gamma);
        } else { // Exponencial (Eq. 5, parte 2)
            if (timeRatio == 1.0) return 1.0;
            return Math.exp(Math.pow(1.0 - timeRatio, gamma) * Math.log(b_k));
        }
    }

    /**
     * Calcula o novo valor para um issue quantitativo (Eq. 6).
     * A lógica de direção (buyer/seller, cost/benefit) está CORRETA.
     */
    private double calculateNewQuantitativeValue(double concessionRate, double min_k, double max_k, IssueType type, String agentType) {
        // TODO (SINERGIA): 'min_k' e 'max_k' são genéricos.
        // Eles deveriam ser os limites específicos do ProductBundle
        // que está sendo negociado.
        double range = max_k - min_k;

        if (Math.abs(range) < 1e-9) {
            return min_k;
        }

        double newValue;
        if (agentType.equalsIgnoreCase("buyer")) {
            // Comprador: Cede do seu 'melhor' (min custo) para o 'pior' (max custo)
            if (type == IssueType.BENEFIT) { // max -> min
                newValue = max_k - concessionRate * range;
            } else { // COST - min -> max
                newValue = min_k + concessionRate * range;
            }
        } else { // "seller"
            // Vendedor: Cede do seu 'melhor' (max preço) para o 'pior' (min preço)
            if (type == IssueType.BENEFIT) { // min -> max
                newValue = min_k + concessionRate * range;
            } else { // COST - max -> min
                newValue = max_k - concessionRate * range;
            }
        }
        return Math.max(min_k, Math.min(max_k, newValue));
    }


    /**
     * Mapeia a taxa de concessão (0..1) para um valor linguístico.
     * Esta implementação está CORRETA.
     */
    private String mapConcessionToQualitative(double concessionRate, String agentType) {
        double targetValue;
        if (agentType.equalsIgnoreCase("buyer")) {
            // Comprador cede de VG (1.0) para VP (0.0)
            targetValue = 1.0 - concessionRate;
        } else { // seller
            // Vendedor cede de VP (0.0) para VG (1.0)
            targetValue = concessionRate;
        }

        if (targetValue < 0.1) return "very poor";
        else if (targetValue < 0.3) return "poor";
        else if (targetValue < 0.7) return "medium";
        else if (targetValue < 0.9) return "good";
        else return "very good";
    }
}