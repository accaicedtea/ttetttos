package com.example.model;

import javafx.beans.property.*;
import java.util.Collections;
import java.util.List;

/**
 * Singola voce del carrello.
 *
 * Contiene tutti i campi necessari per costruire il payload
 * dell'ordine nel formato atteso da OrdiniController::create().
 */
public class CartItem {

    private final StringProperty  name      = new SimpleStringProperty();
    private final StringProperty  price     = new SimpleStringProperty();
    private final DoubleProperty  priceVal  = new SimpleDoubleProperty();
    private final IntegerProperty qty       = new SimpleIntegerProperty(1);
    private final List<String>    allergens;

    // Campi richiesti dal server
    private final int    productId; // id prodotto dal DB (0 se non disponibile)
    private final int    iva;       // aliquota IVA: 4, 10, 22 (default 10)
    private final String sku;       // codice prodotto (null se non disponibile)

    // Costruttore completo (con id, iva, sku dal menu)
    public CartItem(int productId, String name, String priceFormatted,
                    double priceVal, int iva, String sku, List<String> allergens) {
        this.productId = productId;
        this.iva       = iva;
        this.sku       = sku;
        this.allergens = allergens != null ? allergens : Collections.emptyList();
        this.name.set(name);
        this.price.set(priceFormatted);
        this.priceVal.set(priceVal);
    }

    // Costruttore compatibilita (senza id/iva/sku)
    public CartItem(String name, String priceFormatted,
                    double priceVal, List<String> allergens) {
        this(0, name, priceFormatted, priceVal, 10, null, allergens);
    }

    // ── Getters ───────────────────────────────────────────────────────

    public int          getProductId() { return productId;     }
    public String       getName()      { return name.get();    }
    public String       getPrice()     { return price.get();   }
    public double       getPriceVal()  { return priceVal.get();}
    public int          getQty()       { return qty.get();     }
    public void         setQty(int q)  { qty.set(q);           }
    public int          getIva()       { return iva;           }
    public String       getSku()       { return sku;           }
    public List<String> getAllergens()  { return allergens;     }

    public StringProperty  nameProperty()  { return name;  }
    public StringProperty  priceProperty() { return price; }
    public IntegerProperty qtyProperty()   { return qty;   }

    public double total()           { return priceVal.get() * qty.get(); }
    public String totalFormatted()  {
        return String.format("€ %.2f", total()).replace('.', ',');
    }
}
