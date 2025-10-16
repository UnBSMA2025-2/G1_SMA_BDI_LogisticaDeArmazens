# Fase 0

- Configuração do JADE: colocando o jade.jar na lib/.
- Criando um agente de teste: DummyAgent com código "Hello world"
- Configuração do .vscode/launch.json para execução no VS code.

# Fase 1

- Construir o vocabulario NegotiationIssue -> ProductBundle -> Bid -> Proposal
- ```NegotiationIssue.java```: critérios (issue) de negociação.
- ```ProductBundle.java```: pacote de produtos. 
- ```Bid.java```: um lance (Bid) para um pacote de produtos específico.
- ```Proposal.java```: proposta (ou contraproposta) com um conjunto de Bids.
- Teste com uma classe temporaria Main.java instanciando um bid

# Fase 2

- Instalação e configuração do Maven
- EvaluationService.java: Implementação das eq. 1-4 do artigo
    - Teoria da Utilidade Multi-Atributo (MAUT)
    - calculateUtility(): Função de Utilidade do Bid (Equação 4)
    - normalizeQualitativeUtility(): Normalização com Números Fuzzy Triangulares (Equação 3)
    - normalizeQuantitativeUtility(): Funções de Utilidade Não-Lineares (Equações 1 e 2)
- Teste unitário usando Junit

# Fase 3

- BuyerAgent e um SellerAgent trocarem mensagens e realizarem uma negociação rudimentar
- SA: O SellerAgent inicia enviando uma mensagem contendo um objeto Proposal
- BA: O BuyerAgent responde com uma contraproposta (ACLMessage.PROPOSE) ou com um ACLMessage.ACCEPT_PROPOSAL.

<center>

![image](assets/sniffer_fase_3.png)

</center>

# Fase 4

