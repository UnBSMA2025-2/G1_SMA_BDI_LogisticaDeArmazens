package mas.models;

import java.io.Serializable;

public class NegotiationResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private Bid finalBid;
    private double utility;
    private String supplierName;

    public NegotiationResult(Bid finalBid, double utility, String supplierName) {
        this.finalBid = finalBid;
        this.utility = utility;
        this.supplierName = supplierName;
    }

    public Bid getFinalBid() { return finalBid; }
    public double getUtility() { return utility; }
    public String getSupplierName() { return supplierName; }

    @Override
    public String toString() {
        return String.format("Result from %s (Utility: %.3f)", supplierName, utility);
    }
}