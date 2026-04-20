package com.app.components;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import com.app.pojo.Product;

/**
 * ╔════════════════════════════════════════════════════════════════════╗
 * ║                  PRODUCT CARD CONTROLLER                           ║
 * ╚════════════════════════════════════════════════════════════════════╝
 *
 * Logica per ProductCard.fxml
 *
 * Factory: ProductCardController.create(Product) → VBox card
 * 
 * IMPORTANT: Usa FXMLLoader per caricare il file FXML e inizializzare @FXML fields
 */
public class ProductCardController {

    @FXML private ImageView productImage;
    @FXML private Label productName;
    @FXML private Label productPrice;
    @FXML private Label productDesc;
    @FXML private FlowPane allergenChips;

    /**
     * Popola la card con i dati del prodotto
     */
    public void setProduct(Product product) {
        productName.setText(product.nome);
        productPrice.setText(product.prezzoFmt);
        productDesc.setText(product.descrizione);

        // Carica immagine se disponibile
        if (product.hasImage() && product.imageUrl != null) {
            try {
                productImage.setImage(new Image(product.imageUrl, 240, 180, false, true));
            } catch (Exception e) {
                // Immagine non caricata, mostra placeholder
            }
        }

        // Aggiungi allergeni
        if (!product.allergeni.isEmpty()) {
            allergenChips.getChildren().clear();
            product.allergeni.forEach(allergen -> 
                allergenChips.getChildren().add(ChipFactory.allergenChip(allergen))
            );
            allergenChips.setVisible(true);
            allergenChips.setManaged(true);
        } else {
            allergenChips.setVisible(false);
            allergenChips.setManaged(false);
        }
    }

    /**
     * Factory method: crea e popola una card da un Product POJO
     * Usa FXMLLoader per caricare il file FXML
     */
    public static VBox create(Product product) {
        try {
            FXMLLoader loader = new FXMLLoader(
                ProductCardController.class.getResource("/com/app/components/ProductCard.fxml"));
            VBox root = loader.load();
            ProductCardController controller = loader.getController();
            controller.setProduct(product);
            return root;
        } catch (Exception e) {
            throw new RuntimeException("Errore caricamento ProductCard.fxml", e);
        }
    }
}
