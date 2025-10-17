package mas.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mas.models.Bid;
import mas.models.NegotiationResult;

public class WinnerDeterminationService {

    /**
     * Encontra a combinação ótima de lances usando uma busca exaustiva (força bruta).
     * @param results A lista de todos os resultados da negociação (lances finais).
     * @param productDemand Um array representando a demanda para cada produto (ex: [1, 1, 1, 1]).
     * @return Uma lista de NegotiationResult representando a solução ótima.
     */
    public List<NegotiationResult> findOptimalCombination(List<NegotiationResult> results, int[] productDemand) {
        List<NegotiationResult> bestCombination = new ArrayList<>();
        double maxUtility = 0.0;

        int n = results.size();
        // Itera por todas as 2^n combinações possíveis de resultados
        for (int i = 0; i < (1 << n); i++) {
            List<NegotiationResult> currentCombination = new ArrayList<>();
            double currentUtility = 0.0;
            Map<String, Boolean> supplierUsed = new HashMap<>();

            for (int j = 0; j < n; j++) {
                // Verifica se o j-ésimo resultado está nesta combinação
                if ((i & (1 << j)) > 0) {
                    NegotiationResult result = results.get(j);
                    String supplierName = result.getSupplierName();

                    // Restrição: Não mais que um lance por fornecedor
                    if (supplierUsed.getOrDefault(supplierName, false)) {
                        currentCombination.clear(); // Invalida esta combinação
                        break;
                    }
                    supplierUsed.put(supplierName, true);
                    currentCombination.add(result);
                    currentUtility += result.getUtility();
                }
            }

            // Se a combinação é válida e satisfaz a demanda, compara com a melhor encontrada
            if (!currentCombination.isEmpty() && satisfiesDemand(currentCombination, productDemand) && currentUtility > maxUtility) {
                maxUtility = currentUtility;
                bestCombination = currentCombination;
            }
        }
        return bestCombination;
    }

    /**
     * Verifica se uma combinação de lances satisfaz a demanda de todos os produtos.
     */
    private boolean satisfiesDemand(List<NegotiationResult> combination, int[] productDemand) {
        int[] coveredDemand = new int[productDemand.length];
        for (NegotiationResult result : combination) {
            Bid bid = result.getFinalBid();
            int[] productsInBundle = bid.getProductBundle().getProducts();
            for (int i = 0; i < productsInBundle.length; i++) {
                if (productsInBundle[i] == 1) {
                    coveredDemand[i] = 1;
                }
            }
        }

        for (int i = 0; i < productDemand.length; i++) {
            if (productDemand[i] == 1 && coveredDemand[i] == 0) {
                return false; // Se um produto requerido não foi coberto, falha
            }
        }
        return true;
    }
}