package com.app.controllers;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import com.app.components.ChipFactory;
import com.app.model.CartItem;
import com.app.model.CartManager;
import com.util.Animations;
import com.util.Navigator;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CartController — refactored.
 *
 * Fix rispetto alla versione precedente:
 * - allergenWarningBox / allergenWarningTitle / allergenWarningChips legati
 * direttamente via @FXML (niente componente esterno che non si agganciava)
 * - Mostra gli ingredienti nella riga carrello
 * - Banner allergeni in fondo visibile/nascosto in modo reattivo
 */
public class CartController extends BaseController {

    @FXML
    private Label titleLabel;
    @FXML
    private Label totalBadge;
    @FXML
    private ScrollPane cartScroll;
    @FXML
    private VBox cartList;
    @FXML
    private Label totalLabel;
    @FXML
    private Label totalAmount;
    @FXML
    private Button proceedBtn;
    @FXML
    private StackPane rootPane;

    // Banner allergeni — collegato direttamente all'FXML
    @FXML
    private HBox allergenWarningBox;
    @FXML
    private Label allergenWarningTitle;
    @FXML
    private FlowPane allergenWarningChips;

    @FXML
    private void initialize() {
        this.rootStack = rootPane;

        t(titleLabel, "cart");
        t(totalLabel, "total");
        if (proceedBtn != null)
            proceedBtn.setText(t("proceed") + " →");

        Animations.inertiaScroll(cartScroll);
        Animations.touchFeedback(proceedBtn);

        // Ascolta le modifiche al carrello per aggiornare il banner in tempo reale
        CartManager.get().getItems().addListener(
                (javafx.collections.ListChangeListener<CartItem>) c -> buildCartList());

        buildCartList();
    }

    // ── Lista carrello ────────────────────────────────────────────────

    private void buildCartList() {
        cartList.getChildren().clear();
        CartManager cart = CartManager.get();

        if (cart.isEmpty()) {
            Label empty = new Label(t("cart_empty"));
            empty.getStyleClass().add("cart-empty-label");
            cartList.getChildren().add(empty);
            setVisible(proceedBtn, false);
            hideAllergenBanner();
        } else {
            cart.getItems().forEach(item -> cartList.getChildren().add(buildRow(item)));
            setVisible(proceedBtn, true);
            updateAllergenBanner(cart.getItems());
        }

        updateTotals();
    }

    // ── Riga carrello ─────────────────────────────────────────────────

    private HBox buildRow(CartItem item) {
        VBox info = new VBox(8);
        HBox.setHgrow(info, Priority.ALWAYS);

        // Nome prodotto
        Label name = new Label(item.getName());
        name.getStyleClass().add("cart-item-name");
        name.setWrapText(true);
        info.getChildren().add(name);
        
        // Prezzo unitario
        Label price = new Label(item.getPrice());
        price.getStyleClass().add("cart-item-price");
        info.getChildren().add(price);

        // Ingredienti (se presenti) resi più eleganti
        List<String> ingredienti = item.getIngredienti(); 
        if (ingredienti != null && !ingredienti.isEmpty()) {
            FlowPane ingPane = new FlowPane();
            ingPane.setHgap(6);
            ingPane.setVgap(6);
            for (String ing : ingredienti) {
                Label lbl = new Label(ing);
                lbl.getStyleClass().add("cart-ingredient-chip");
                ingPane.getChildren().add(lbl);
            }
            info.getChildren().add(ingPane);
        }

        // Chip allergeni per singola voce
        if (item.getAllergens() != null && !item.getAllergens().isEmpty()) {
            FlowPane chips = new FlowPane();
            chips.setHgap(6);
            chips.setVgap(6);
            ChipFactory.fillAllergens(chips, item.getAllergens(), ChipFactory.ChipType.CART);
            info.getChildren().add(chips);
        }

        // Controlli quantità
        Button minus = new Button();
        Label qty = new Label(String.valueOf(item.getQty()));
        Button plus = new Button("+");
        minus.getStyleClass().add("qty-btn");
        qty.getStyleClass().add("qty-label");
        plus.getStyleClass().add("qty-btn");
        Animations.touchFeedback(minus);
        Animations.touchFeedback(plus);

        Runnable syncMinusIcon = () -> {
            boolean isTrash = item.getQty() <= 1;
            minus.setText(isTrash ? "🗑" : "−");
            if (isTrash) {
                if (!minus.getStyleClass().contains("trash-btn")) {
                    minus.getStyleClass().add("trash-btn");
                }
            } else {
                minus.getStyleClass().remove("trash-btn");
            }
        };
        syncMinusIcon.run();

        Label rowTotal = new Label(item.totalFormatted());
        rowTotal.getStyleClass().add("cart-item-total");

        item.qtyProperty().addListener((obs, o, n) -> {
            qty.setText(String.valueOf(n));
            rowTotal.setText(item.totalFormatted());
            syncMinusIcon.run();
        });

        minus.setOnAction(e -> {
            if (item.getQty() > 1) {
                item.setQty(item.getQty() - 1);
                updateTotals();
                return;
            }
            com.app.components.ModalDialog.builder(rootPane)
                    .type(com.app.components.ModalDialog.Type.WARNING)
                    .title(t("remove_item"))
                    .message(t("remove_item_confirm"))
                    .width(700)
                    .button(com.app.components.ModalButton.cancel(t("cancel")))
                    .button(com.app.components.ModalButton.danger(t("confirm"), () -> {
                        CartManager.get().removeItem(item);
                        buildCartList();
                    }))
                    .show();
        });

        plus.setOnAction(e -> {
            item.setQty(item.getQty() + 1);
            updateTotals();
        });

        HBox row = new HBox(14, info, minus, qty, plus, rowTotal);
        row.getStyleClass().add("cart-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ── Banner allergeni in fondo ─────────────────────────────────────

    /**
     * Raccoglie tutti gli allergeni unici dai prodotti nel carrello,
     * li mostra come chip nel banner. Se non ci sono allergeni, nasconde il banner.
     */
    private void updateAllergenBanner(List<CartItem> items) {
        if (allergenWarningBox == null)
            return;

        // Raccoglie allergeni unici (case-insensitive)
        List<String> allAllergens = items.stream()
                .filter(i -> i.getAllergens() != null)
                .flatMap(i -> i.getAllergens().stream())
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        if (allAllergens.isEmpty()) {
            hideAllergenBanner();
            return;
        }

        // Chip nel banner
        if (allergenWarningChips != null) {
            allergenWarningChips.getChildren().clear();
            ChipFactory.fillAllergens(allergenWarningChips, allAllergens, ChipFactory.ChipType.MODAL);
        }

        if (allergenWarningTitle != null) {
            allergenWarningTitle.setText(
                    allAllergens.size() == 1
                            ? "Attenzione: 1 allergene nel tuo ordine"
                            : "Attenzione: " + allAllergens.size() + " allergeni nel tuo ordine");
        }

        setVisible(allergenWarningBox, true);
    }

    private void hideAllergenBanner() {
        setVisible(allergenWarningBox, false);
        if (allergenWarningChips != null)
            allergenWarningChips.getChildren().clear();
    }

    // ── Totali ────────────────────────────────────────────────────────

    private void updateTotals() {
        String total = CartManager.get().totalPriceFormatted();
        if (totalAmount != null)
            totalAmount.setText(total);
        if (totalBadge != null)
            totalBadge.setText(total);
    }

    // ── Navigazione ───────────────────────────────────────────────────

    @FXML
    private void onBack() {
        Navigator.goTo(Navigator.Screen.MENU);
    }

    @FXML
    private void onProceed() {
        if (!CartManager.get().isEmpty()) {
            Navigator.goTo(Navigator.Screen.PAYMENT);
        }
    }
}

/*
 * NOTA su CartItem.getIngredienti():
 * Se CartItem non ha ancora un campo List<String> ingredienti, aggiungilo:
 *
 * private List<String> ingredienti = List.of();
 * public List<String> getIngredienti() { return ingredienti; }
 *
 * e nel builder:
 * public Builder ingredienti(List<String> v) { this.ingredienti = v; return
 * this; }
 *
 * Poi in CartItem.fromProduct(Product p):
 * .ingredienti(p.ingredienti)
 */