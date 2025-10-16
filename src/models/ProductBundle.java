package models;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Representa um pacote de produtos (Product Bundle). 
 * É um vetor onde cada posição representa um produto. O valor 1 indica
 * que o produto está no pacote, e 0 caso contrário. 
 */
public class ProductBundle implements Serializable {
    // Garante a compatibilidade entre diferentes versões da classe durante a serialização.
    private static final long serialVersionUID = 1L;

    private int[] products;

    public ProductBundle(int[] products) {
        this.products = products;
    }

    public int[] getProducts() {
        return products;
    }

    @Override
    public String toString() {
        return "Bundle" + Arrays.toString(products);
    }
}