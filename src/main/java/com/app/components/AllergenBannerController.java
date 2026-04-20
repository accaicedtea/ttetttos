package com.app.components;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;

import java.util.List;

/**
 * ╔════════════════════════════════════════════════════════════════════╗
 * ║              ALLERGEN BANNER CONTROLLER                            ║
 * ╚════════════════════════════════════════════════════════════════════╝
 *
 * Logica per AllergenBanner.fxml
 * Mostra avviso allergie quando il carrello ne contiene
 * 
 * IMPORTANT: Usa FXMLLoader per caricare il file FXML e inizializzare @FXML fields
 */
public class AllergenBannerController {

    @FXML private Label titleLabel;
    @FXML private FlowPane allergenChips;

    /**
     * Mostra/nascondi banner con allergeni specifici
     */
    public void setAllergens(List<String> allergens) {
        if (allergens == null || allergens.isEmpty()) {
            // Don't show
            return;
        }

        allergenChips.getChildren().clear();
        allergens.forEach(allergen ->
            allergenChips.getChildren().add(ChipFactory.allergenChip(allergen))
        );
    }

    /**
     * Nascondi il banner
     */
    public void clear() {
        allergenChips.getChildren().clear();
    }

    /**
     * Factory method: crea un banner
     * Usa FXMLLoader per caricare il file FXML
     */
    public static HBox create() {
        try {
            FXMLLoader loader = new FXMLLoader(
                AllergenBannerController.class.getResource("/com/app/components/AllergenBanner.fxml"));
            HBox root = loader.load();
            return root;
        } catch (Exception e) {
            throw new RuntimeException("Errore caricamento AllergenBanner.fxml", e);
        }
    }
}
