package com.example;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;


import com.example.model.CartItem;
import com.example.model.CartManager;
import com.example.model.I18n;
import com.util.Animations;

import java.util.*;

public class CartController {

    @FXML private Label      titleLabel;
    @FXML private Label      totalBadge;
    @FXML private ScrollPane cartScroll;
    @FXML private VBox       cartList;
    @FXML private Label      totalLabel;
    @FXML private Label      totalAmount;
    @FXML private Button     proceedBtn;

    // Allergen warning
    @FXML private HBox       allergenWarningBox;
    @FXML private Label      allergenWarningTitle;
    @FXML private FlowPane   allergenWarningChips;

    @FXML
    private void initialize() {
        titleLabel.setText(I18n.t("cart"));
        totalLabel.setText(I18n.t("total"));
        proceedBtn.setText(I18n.t("proceed") + " →");
        allergenWarningTitle.setText(I18n.t("allergen_warning"));

        Animations.inertiaScroll(cartScroll);
        Animations.touchFeedback(proceedBtn);
        buildCartList();
    }

    private void buildCartList() {
        cartList.getChildren().clear();
        CartManager cart = CartManager.get();

        if (cart.isEmpty()) {
            Label empty = new Label(I18n.t("cart_empty"));
            empty.getStyleClass().add("cart-empty-label");
            cartList.getChildren().add(empty);
            proceedBtn.setDisable(true);
            allergenWarningBox.setVisible(false);
            allergenWarningBox.setManaged(false);
        } else {
            for (CartItem item : cart.getItems()) {
                cartList.getChildren().add(buildRow(item));
            }
            proceedBtn.setDisable(false);
            updateAllergenWarning(cart.getItems());
        }

        updateTotals();
    }

    /** Raccoglie tutti gli allergeni unici dall'ordine e li mostra. */
    private void updateAllergenWarning(List<CartItem> items) {
        Set<String> allAllergens = new LinkedHashSet<>();
        for (CartItem item : items) {
            allAllergens.addAll(item.getAllergens());
        }

        allergenWarningChips.getChildren().clear();

        if (allAllergens.isEmpty()) {
            allergenWarningBox.setVisible(false);
            allergenWarningBox.setManaged(false);
        } else {
            for (String a : allAllergens) {
                Label chip = new Label(a);
                chip.getStyleClass().add("allergen-chip");
                allergenWarningChips.getChildren().add(chip);
            }
            allergenWarningBox.setVisible(true);
            allergenWarningBox.setManaged(true);
        }
    }

    private HBox buildRow(CartItem item) {
        VBox info = new VBox(6);
        Label name = new Label(item.getName());
        name.getStyleClass().add("cart-item-name");
        name.setWrapText(true);
        Label price = new Label(item.getPrice());
        price.getStyleClass().add("cart-item-price");
        info.getChildren().addAll(name, price);

        // Allergeni della singola voce (mini chips)
        if (!item.getAllergens().isEmpty()) {
            FlowPane chips = new FlowPane();
            chips.setHgap(5); chips.setVgap(4);
            for (String a : item.getAllergens()) {
                Label c = new Label(a);
                c.getStyleClass().add("cart-item-allergen-chip");
                chips.getChildren().add(c);
            }
            info.getChildren().add(chips);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Quantità +/− (cambia l'icona a cestino quando si arriva a 1)
        Button minus = new Button();
        Label  qty   = new Label(String.valueOf(item.getQty()));
        Button plus  = new Button("+");
        minus.getStyleClass().add("qty-btn");
        qty.getStyleClass().add("qty-label");
        plus.getStyleClass().add("qty-btn");
        Animations.touchFeedback(minus);
        Animations.touchFeedback(plus);

        // Aggiorna l'icona del pulsante meno in base alla quantità
        Runnable updateMinusIcon = () -> minus.setText(item.getQty() <= 1 ? "🗑" : "−");
        updateMinusIcon.run();

        minus.setOnAction(e -> {
            if (item.getQty() > 1) {
                item.setQty(item.getQty() - 1);
                updateTotals();
                return;
            }

            // Conferma rimozione se l'ultima unità viene eliminata
            ConfirmModal.show(App.rootPane,
                I18n.t("remove_item"),
                I18n.t("remove_item_confirm"),
                () -> {
                    CartManager.get().removeItem(item);
                    buildCartList();
                }
            );
        });

        plus.setOnAction(e -> {
            item.setQty(item.getQty() + 1);
            updateTotals();
        });

        Label rowTotal = new Label(item.totalFormatted());
        rowTotal.getStyleClass().add("cart-item-total");
        item.qtyProperty().addListener((obs, o, n) -> {
            qty.setText(String.valueOf(n));
            rowTotal.setText(item.totalFormatted());
            updateMinusIcon.run();
        });

        HBox row = new HBox(14, info, spacer, minus, qty, plus, rowTotal);
        row.getStyleClass().add("cart-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void updateTotals() {
        String total = CartManager.get().totalPriceFormatted();
        totalAmount.setText(total);
        totalBadge.setText(total);
    }

    @FXML private void onBack()    { Navigator.goTo(Navigator.Screen.MENU); }
    @FXML private void onProceed() { Navigator.goTo(Navigator.Screen.PAYMENT); }
}
