package com.app.components;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import com.app.model.I18n;

import java.util.*;

/**
 * Componente riutilizzabile: banner allergeni "Attenzione allergeni nell'ordine".
 *
 * Era duplicato in CartController e ShopPageController.
 * Adesso è un VBox auto-gestito che si mostra/nasconde da solo.
 *
 * Uso:
 *   AllergenWarning warn = new AllergenWarning();
 *   someParent.getChildren().add(warn);
 *   warn.update(myAllergenList);   // mostra o nasconde automaticamente
 */
public class AllergenWarning extends VBox {

    private final Label    title;
    private final FlowPane chips;

    public AllergenWarning() {
        getStyleClass().add("allergen-warning-box");
        setSpacing(8);
        setPadding(new Insets(12, 16, 12, 16));

        title = new Label(I18n.t("allergen_warning"));
        title.getStyleClass().add("allergen-warning-title");

        chips = new FlowPane();
        chips.setHgap(6); chips.setVgap(6);

        getChildren().addAll(title, chips);
        setVisible(false);
        setManaged(false);
    }

    /**
     * Aggiorna il banner con gli allergeni unici dalla lista di item.
     * Si mostra automaticamente se ci sono allergeni, si nasconde se non ce ne sono.
     *
     * @param allAllergens lista flat di allergeni (stringhe, eventualmente ripetute)
     */
    public void update(List<String> allAllergens) {
        chips.getChildren().clear();
        Set<String> unique = new LinkedHashSet<>(allAllergens);
        if (unique.isEmpty()) {
            setVisible(false);
            setManaged(false);
        } else {
            unique.forEach(a -> chips.getChildren().add(ChipFactory.allergenChip(a)));
            setVisible(true);
            setManaged(true);
        }
    }

    /**
     * Raccoglie gli allergeni da una collezione di oggetti che espongono
     * un metodo getAllergens() — usa via lambda.
     */
    public void updateFromSets(List<? extends List<String>> allergenSets) {
        List<String> flat = new ArrayList<>();
        allergenSets.forEach(flat::addAll);
        update(flat);
    }

    /** Nasconde il banner e svuota le chip. */
    public void clear() {
        chips.getChildren().clear();
        setVisible(false);
        setManaged(false);
    }
}
