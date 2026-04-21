package com.app.controllers;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.event.EventHandler;

import com.app.components.ChipFactory;
import com.app.components.CartItemRowController;
import com.app.model.CartItem;
import com.app.model.CartManager;
import com.app.model.OrderQueue;
import com.util.Navigator;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * CartController (Refactored)
 * ============================================================================
 *
 * Gestisce la logica del carrello. Alimenta i propri dati tramite CartManager.
 * Utilizza utilità di BaseController per animazioni e avvisi UI.
 *
 * +-----------------------------------------------------------+
 * |                     Cart Screen                           |
 * |-----------------------------------------------------------|
 * | [ Header (System Info) ] <-- fx:include ShopHeader        |
 * | [ Top Bar (Titolo, Totale, Back Btn) ]                    |
 * |                                                           |
 * | [ Prodotto 1 (nome, qty, ingredienti, prezzo, bottoni) ]  |
 * | [ Prodotto 2 ... ]                                        |
 * |                                                           |
 * | [ Allergen Warning Banner ]                               |
 * | [ Totale: € XX,XX ]                                       |
 * | [ Btn Pagamento ]                                         |
 * | [ Footer (brand/info) ]                                   |
 * +-----------------------------------------------------------+
 * ============================================================================
 */
public class CartController extends BaseController implements Navigator.ScreenReturnable {

    // Inactivity monitoring
    private EventHandler<MouseEvent> inactivityResetMouseHandler;
    private EventHandler<KeyEvent> inactivityResetKeyHandler;

    @FXML
    private Label titleLabel;
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

        makeInertiaScrollable(cartScroll);
        applyTouchFeedback(proceedBtn);

        // Ascolta le modifiche al carrello per aggiornare il banner in tempo reale
        CartManager.get().getItems().addListener(
                (javafx.collections.ListChangeListener<CartItem>) c -> {
                    if (CartManager.get().isEmpty()) {
                        Navigator.goTo(Navigator.Screen.MENU);
                    } else {
                        buildCartList();
                    }
                });

        buildCartList();

        // Setup inactivity monitoring
        setupInactivityMonitoring();
    }

    @Override
    public void onReturn() {
        // Avvia il monitoraggio dell'inattività quando si ritorna a questo schermo
        com.util.InactivityManager.startMonitoring(Navigator.Screen.CART, rootStack);
    }

    private void setupInactivityMonitoring() {
        if (rootStack == null) return;

        // Handler per mouse e tastiera che resetta il timer
        inactivityResetMouseHandler = event -> com.util.InactivityManager.resetTimer();
        inactivityResetKeyHandler = event -> com.util.InactivityManager.resetTimer();

        // Aggiungi event filter al rootStack per catturare tutte le interazioni
        rootStack.addEventFilter(MouseEvent.ANY, inactivityResetMouseHandler);
        rootStack.addEventFilter(KeyEvent.ANY, inactivityResetKeyHandler);

        // Avvia il monitoraggio con rootStack per il dialogo di conferma
        com.util.InactivityManager.startMonitoring(Navigator.Screen.CART, rootStack);
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
            cart.getItems().forEach(item -> 
                cartList.getChildren().add(CartItemRowController.create(item))
            );
            setVisible(proceedBtn, true);
            updateAllergenBanner(cart.getItems());
        }

        updateTotals();
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