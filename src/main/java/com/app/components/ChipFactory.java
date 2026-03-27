package com.app.components;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;

import java.util.List;

/**
 * Factory per chip UI (allergeni, ingredienti, tag generici).
 *
 * Il codice per creare queste chip era duplicato in:
 * - CartController.buildRow()
 * - CartController.updateAllergenWarning()
 * - ShopPageController.showModal()
 * - ProductCard.buildFooter()
 *
 * Ora c'è un unico punto.
 */
public final class ChipFactory {

    private ChipFactory() {
    }

    /** Crea un chip allergene (stile CSS "allergen-chip"). */
    public static Label allergenChip(String text) {
        Label chip = new Label(text);
        chip.getStyleClass().add("allergen-chip");
        return chip;
    }

    /** Crea un chip ingrediente (stile CSS "ingredient-chip"). */
    public static Label ingredientChip(String text) {
        Label chip = new Label(text);
        chip.getStyleClass().add("ingredient-chip");
        return chip;
    }

    /**
     * Crea chip piccoli per la riga del carrello (stile "cart-item-allergen-chip").
     */
    public static Label cartAllergenChip(String text) {
        Label chip = new Label(text);
        chip.getStyleClass().add("cart-item-allergen-chip");
        return chip;
    }

    /**
     * Crea chip da prodotto piccolo per le card nel negozio ("prod-allergen-chip").
     */
    public static Label prodAllergenChip(String text) {
        Label chip = new Label(text);
        chip.getStyleClass().add("prod-allergen-chip");
        return chip;
    }

    /**
     * Riempie un FlowPane esistente con chip allergene.
     * Sostituisce tutti i figli presenti.
     *
     * @param pane      pane da popolare (cleared prima di riempire)
     * @param allergens lista allergeni
     * @param chipType  tipo di chip ("allergen", "cart", "prod")
     */
    public static void fillAllergens(FlowPane pane, List<String> allergens, ChipType chipType) {
        pane.getChildren().clear();
        if (allergens == null)
            return;
        for (String a : allergens) {
            pane.getChildren().add(switch (chipType) {
                case CART -> cartAllergenChip(a);
                case PROD -> prodAllergenChip(a);
                default -> allergenChip(a);
            });
        }
    }

    /** Crea un FlowPane già popolato con chip allergene. */
    public static FlowPane allergenPane(List<String> allergens) {
        FlowPane fp = new FlowPane();
        fp.setHgap(5);
        fp.setVgap(5);
        fp.setAlignment(Pos.CENTER_LEFT);
        fp.getStyleClass().add("prod-allergen-chips");
        if (allergens != null)
            allergens.forEach(a -> fp.getChildren().add(allergenChip(a)));
        return fp;
    }

    /** Crea un FlowPane già popolato con chip ingredienti. */
    public static FlowPane ingredientPane(List<String> ingredients) {
        FlowPane fp = new FlowPane();
        fp.setHgap(5);
        fp.setVgap(5);
        fp.setAlignment(Pos.CENTER_LEFT);
        if (ingredients != null)
            ingredients.forEach(i -> fp.getChildren().add(ingredientChip(i)));
        return fp;
    }

    public enum ChipType {
        MODAL, CART, PROD
    }
}
