package mas.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mas.models.NegotiationResult;

public class WinnerDeterminationService {

    private List<NegotiationResult> bestCombination;
    private double maxUtility;
    private int[] productDemand;

    /**
     * Resolve o Problema de Determinação do Vencedor (WDP) usando Branch-and-Bound.
     * Encontra a combinação de lances que maximiza a utilidade total, sujeita às restrições.
     * @param results A lista de todos os lances finais bem-sucedidos.
     * @param productDemand Um array indicando os produtos requeridos (ex: [1, 1, 0, 1]).
     * @return A lista de lances que compõem a solução ótima.
     */
    public List<NegotiationResult> solveWDPWithBranchAndBound(List<NegotiationResult> results, int[] productDemand) {
        this.bestCombination = new ArrayList<>();
        this.maxUtility = 0.0;
        this.productDemand = productDemand;

        // Pré-processamento: Ordenar os lances por utilidade decrescente.
        results.sort(Comparator.comparingDouble(NegotiationResult::getUtility).reversed());

        // Inicia a busca recursiva a partir do primeiro lance (índice 0)
        branchAndBoundRecursive(results, 0, new ArrayList<>(), 0.0, new HashSet<>());

        return this.bestCombination;
    }

    /**
     * Função recursiva que implementa a lógica de Branch-and-Bound.
     * @param allResults Lista de todos os resultados (ordenados).
     * @param index O índice do resultado que estamos considerando atualmente.
     * @param currentCombination A combinação parcial de lances neste nó da árvore.
     * @param currentUtility A utilidade da combinação parcial.
     * @param usedSuppliers Um conjunto para rastrear fornecedores já incluídos na combinação.
     */
    private void branchAndBoundRecursive(List<NegotiationResult> allResults, int index,
                                         List<NegotiationResult> currentCombination, double currentUtility,
                                         Set<String> usedSuppliers) {

        // --- PODA (PRUNING) ---
        // Calcula o limite superior (upper bound) para este caminho.
        double potentialUtility = currentUtility;
        for (int i = index; i < allResults.size(); i++) {
            potentialUtility += allResults.get(i).getUtility();
        }
        
        // Se o melhor resultado possível deste caminho não supera a melhor solução já encontrada, pode.
        if (potentialUtility <= maxUtility) {
            return;
        }

        // --- CASO BASE (FOLHA DA ÁRVORE) ---
        // Se já consideramos todos os lances.
        if (index == allResults.size()) {
            // Verifica se a solução atual é completa (satisfaz a demanda) e melhor que a global.
            if (satisfiesDemand(currentCombination) && currentUtility > maxUtility) {
                this.maxUtility = currentUtility;
                this.bestCombination = new ArrayList<>(currentCombination);
            }
            return;
        }

        // --- RAMIFICAÇÃO (BRANCHING) ---

        NegotiationResult currentResult = allResults.get(index);

        // RAMO 1: INCLUIR o lance atual (se as restrições permitirem).
        // Restrição: O fornecedor não pode ter sido usado ainda.
        if (!usedSuppliers.contains(currentResult.getSupplierName())) {
            // Cria um novo estado para o ramo de inclusão
            currentCombination.add(currentResult);
            usedSuppliers.add(currentResult.getSupplierName());

            // Chamada recursiva para o próximo nível da árvore
            branchAndBoundRecursive(allResults, index + 1, currentCombination,
                                    currentUtility + currentResult.getUtility(), usedSuppliers);

            // Backtracking: desfaz as alterações para explorar outros caminhos
            usedSuppliers.remove(currentResult.getSupplierName());
            currentCombination.remove(currentCombination.size() - 1);
        }

        // RAMO 2: EXCLUIR o lance atual.
        // Simplesmente passa para o próximo lance sem adicionar o atual.
        branchAndBoundRecursive(allResults, index + 1, currentCombination, currentUtility, usedSuppliers);
    }

    /**
     * Verifica se uma combinação de lances satisfaz a demanda de todos os produtos requeridos.
     * Implementa a restrição da Equação 9 do artigo.
     */
    private boolean satisfiesDemand(List<NegotiationResult> combination) {
        int[] coveredDemand = new int[this.productDemand.length];
        for (NegotiationResult result : combination) {
            int[] productsInBundle = result.getFinalBid().getProductBundle().getProducts();
            for (int i = 0; i < productsInBundle.length; i++) {
                if (productsInBundle[i] == 1) {
                    coveredDemand[i] = 1;
                }
            }
        }

        for (int i = 0; i < this.productDemand.length; i++) {
            if (this.productDemand[i] == 1 && coveredDemand[i] == 0) {
                return false; // Se um produto requerido não foi coberto, a demanda não é satisfeita.
            }
        }
        return true;
    }
}