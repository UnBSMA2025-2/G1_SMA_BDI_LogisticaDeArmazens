package models;

import java.io.Serializable;

/**
 * Representa um único critério (issue) de negociação, como preço ou qualidade. 
 */
public class NegotiationIssue implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private Object value; // Pode ser um Integer/Double para preço/entrega ou String para qualidade/serviço

    public NegotiationIssue(String name, Object value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        return name + ": " + value;
    }
}