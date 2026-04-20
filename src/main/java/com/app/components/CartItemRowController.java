package com.app.components;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;

import com.app.model.CartItem;
import com.app.model.CartManager;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * ╔════════════════════════════════════════════════════════════════════╗
 * ║                CART ITEM ROW CONTROLLER                            ║
 * ╚════════════════════════════════════════════════════════════════════╝
 *
 * Logica per CartItemRow.fxml
 *
 * Factory: CartItemRowController.create(CartItem) → HBox row
 * 
 * IMPORTANT: Usa FXMLLoader per caricare il file FXML e inizializzare @FXML fields
 */
public class CartItemRowController {

    @FXML private Label itemName;
    @FXML private Label itemPrice;
    @FXML private FlowPane ingredientChips;
    @FXML private Button minusBtn;
    @FXML private Label qtyLabel;
    @FXML private Button plusBtn;
    @FXML private Label rowTotal;

    private CartItem item;

    /**
     * Popola la riga con i dati del CartItem
     */
    public void setItem(CartItem item) {
        this.item = item;
        
        itemName.setText(item.getName());
        itemPrice.setText(item.getPrice());
        qtyLabel.setText(String.valueOf(item.getQty()));
        rowTotal.setText(item.totalFormatted());

        // Assicuriamoci che l'icona + sia visibile tramite FontIcon e togliamo il testo per evitare conflitti
        plusBtn.setText("");
        FontIcon plusIcon = new FontIcon("mdi2p-plus");
        plusIcon.setIconSize(18);
        plusBtn.setGraphic(plusIcon);

        // Aggiorna UI quando qty cambia
        item.qtyProperty().addListener((obs, oldVal, newVal) -> {
            qtyLabel.setText(String.valueOf(newVal));
            rowTotal.setText(item.totalFormatted());
        });

        // Aggiungi ingredienti se disponibili
        if (item.getIngredienti() != null && !item.getIngredienti().isEmpty()) {
            ingredientChips.getChildren().clear();
            item.getIngredienti().forEach(ing ->
                ingredientChips.getChildren().add(ChipFactory.ingredientChip(ing))
            );
            ingredientChips.setVisible(true);
            ingredientChips.setManaged(true);
        }

        // Abilita/disabilita minus button
        updateMinusButton();
        item.qtyProperty().addListener((obs, o, n) -> updateMinusButton());
    }

    private void updateMinusButton() {
        minusBtn.setText("");
        if (item.getQty() <= 1) {
            FontIcon trashIcon = new FontIcon("mdi2t-trash-can");
            trashIcon.setIconSize(18);
            trashIcon.getStyleClass().add("remove-icon");
            minusBtn.setGraphic(trashIcon);
            minusBtn.getStyleClass().add("remove-btn-mode");
        } else {
            FontIcon minusIcon = new FontIcon("mdi2m-minus");
            minusIcon.setIconSize(18);
            minusBtn.setGraphic(minusIcon);
            minusBtn.getStyleClass().remove("remove-btn-mode");
        }
    }

    @FXML
    private void onMinus() {
        if (item.getQty() > 1) {
            item.setQty(item.getQty() - 1);
        } else {
            // Mostra dialogo di conferma per rimuovere l'elemento
            com.app.components.ModalDialog.confirm(
                com.app.App.rootPane,
                "Rimuovere prodotto?",
                "Vuoi davvero rimuovere " + item.getName() + " dal carrello?",
                () -> CartManager.get().removeItem(item)
            );
        }
    }

    @FXML
    private void onPlus() {
        item.setQty(item.getQty() + 1);
    }

    /**
     * Factory method: crea e popola una riga da un CartItem
     * Usa FXMLLoader per caricare il file FXML
     */
    public static HBox create(CartItem item) {
        try {
            FXMLLoader loader = new FXMLLoader(
                CartItemRowController.class.getResource("/com/app/components/CartItemRow.fxml"));
            HBox root = loader.load();
            CartItemRowController controller = loader.getController();
            controller.setItem(item);
            return root;
        } catch (Exception e) {
            throw new RuntimeException("Errore caricamento CartItemRow.fxml", e);
        }
    }
}
