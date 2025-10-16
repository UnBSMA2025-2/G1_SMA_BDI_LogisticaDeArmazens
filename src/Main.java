import java.util.ArrayList;
import java.util.List;
import models.Bid;
import models.NegotiationIssue;
import models.ProductBundle;

/**
 * Classe temporária para simular um lance, passo a passo, pelo terminal e testar
 * a criação dos objetos conforme a definição do artigo.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("--- Iniciando Teste da Fase 1 ---");

        // Cenário: Uma negociação para 3 produtos (M=3).
        // O fornecedor faz um lance para um pacote contendo P1 e P2. 

        // 1. Criar o ProductBundle: (1, 1, 0) -> P1 e P2 estão inclusos, P3 não. 
        ProductBundle pb = new ProductBundle(new int[]{1, 1, 0});

        // 2. Definir as quantidades: 1000 unidades de P1 e 800 de P2. 
        int[] quantities = new int[]{1000, 800, 0};

        // 3. Criar a lista de Issues (critérios da negociação).
        List<NegotiationIssue> issues = new ArrayList<>();
        issues.add(new NegotiationIssue("Price", 500)); // Preço do pacote = 500 
        issues.add(new NegotiationIssue("Quality", "very good")); // Qualidade = muito boa 
        issues.add(new NegotiationIssue("Delivery", 4)); // Entrega = 4 dias 
        issues.add(new NegotiationIssue("Service", "medium")); // Serviço = médio 

        // 4. Montar o Bid final com todas as partes.
        Bid exampleBid = new Bid(pb, issues, quantities);

        // 5. Imprimir o resultado no console para verificação.
        System.out.println("\nObjeto Bid criado com sucesso:");
        System.out.println(exampleBid);

        System.out.println("\n--- Teste da Fase 1 Concluído ---");
    }
}