import java.util.HashMap;
import java.util.Map;

public class ProductDB {
    private HashMap<String, Integer> products;
    
    public ProductDB() {
        products = new HashMap<>();
    }
    
    public void addProduct(String product, int quantity) {
        products.put(product, quantity);
    }
    
    public boolean hasProduct(String product) {
        return products.containsKey(product) && products.get(product) > 0;
    }
    
    public int getQuantity(String product) {
        return products.getOrDefault(product, 0);
    }
    
    public void decreaseQuantity(String product) {
        if (hasProduct(product)) {
            products.put(product, products.get(product) - 1);
        }
    }
    
    public String toJSON() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : products.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        json.append("}");
        return json.toString();
    }
    
    public String findNeededProduct(ProductDB otherDB) {
        for (String product : products.keySet()) {
            if (products.get(product) < 2 && otherDB.getQuantity(product) > 1) {
                return product;
            }
        }
        return null;
    }
}