package com.example.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;

import java.util.List;

/**
 * Card prodotto — design leggibile da lontano.
 *
 *  ┌──────────────────────────────┐
 *  │  NOME PRODOTTO               │  22px bold
 *  │  € 12,50                     │  30px bold accent
 *  │  Descrizione breve...        │  15px grigio
 *  │  ⚠ Glutine  Latte            │  chips arancio
 *  └──────────────────────────────┘
 */
public class ProductCard extends VBox {

    public ProductCard(String name, String price, String description, List<String> allergens) {
        setSpacing(10);
        setPadding(new Insets(22, 20, 20, 20));
        setAlignment(Pos.TOP_LEFT);
        getStyleClass().add("prod-card");

        // Nome
        Label nameLbl = new Label(name == null ? "" : name);
        nameLbl.getStyleClass().add("prod-name");
        nameLbl.setWrapText(true);
        nameLbl.setMaxWidth(Double.MAX_VALUE);

        // Prezzo — grande e subito visibile
        Label priceLbl = new Label(price == null || price.isBlank() ? "" : price);
        priceLbl.getStyleClass().add("prod-price");

        // Descrizione
        Label descLbl = new Label(description == null ? "" : description);
        descLbl.getStyleClass().add("prod-desc");
        descLbl.setWrapText(true);
        descLbl.setMaxHeight(60); // max ~3 righe

        // Spacer per spingere allergeni in fondo
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(nameLbl, priceLbl, descLbl, spacer);

        // Feedback visivo built-in: scale su press, NON usa touchFeedback()
        // così il click propagarsi sempre senza interferenze
        addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            setScaleX(0.96); setScaleY(0.96);
        });
        addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
            javafx.animation.ScaleTransition st =
                new javafx.animation.ScaleTransition(
                    javafx.util.Duration.millis(100), this);
            st.setToX(1.0); st.setToY(1.0);
            st.play();
        });
        addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
            setScaleX(1.0); setScaleY(1.0);
        });

        // Allergeni come mini-chips
        if (allergens != null && !allergens.isEmpty()) {
            FlowPane chips = new FlowPane();
            chips.setHgap(6);
            chips.setVgap(6);
            chips.getStyleClass().add("prod-allergen-chips");
            for (String a : allergens) {
                Label chip = new Label(a);
                chip.getStyleClass().add("prod-allergen-chip");
                chips.getChildren().add(chip);
            }
            getChildren().add(chips);
        }
    }
}
