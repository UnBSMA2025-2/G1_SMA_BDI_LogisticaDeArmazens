package mas.models;

import java.io.Serializable;
import java.util.List;

/**
 * Representa uma proposta (ou contraproposta), que é um conjunto de múltiplos Bids. [cite: 146, 288]
 * Permite que um agente faça ofertas para diferentes pacotes de produtos de uma só vez.
 */
public class Proposal implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<Bid> bids;

    public Proposal(List<Bid> bids) {
        this.bids = bids;
    }

    public List<Bid> getBids() {
        return bids;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Proposal containing ").append(bids.size()).append(" bid(s):\n");
        for (Bid bid : bids) {
            sb.append("---\n");
            sb.append(bid.toString()).append("\n");
        }
        sb.append("---");
        return sb.toString();
    }
}