package com.app.controllers;

import javafx.animation.*;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import com.app.model.CartManager;
import com.app.model.OrderQueue;
import com.util.Animations;
import com.util.Navigator;

/**
 * PaymentController — refactored.
 *
 * Rispetto all'originale:
 * - Estende BaseController (t(), setVisible())
 * - Nessun'altra modifica funzionale
 */
public class PaymentController extends BaseController implements Navigator.ScreenReturnable {

    // Inactivity monitoring
    private EventHandler<MouseEvent> inactivityResetMouseHandler;
    private EventHandler<KeyEvent> inactivityResetKeyHandler;

    @FXML
    private Label titleLabel;
    @FXML
    private Label totalLabel;
    @FXML
    private Label cashLabel, cashSubLabel;
    @FXML
    private Label cardLabel, cardSubLabel;
    @FXML
    private VBox cashCard;
    @FXML
    private VBox cardCard;
    @FXML
    private StackPane rootPane;

    private final javafx.beans.value.ChangeListener<Boolean> networkListener = (obs, oldVal, newVal) -> {
        updateCardStatus(newVal);
    };

    @FXML
    private void initialize() {
        this.rootStack = rootPane;

        t(titleLabel, "payment_title");
        if (totalLabel != null)
            totalLabel.setText(CartManager.get().totalPriceFormatted());
        t(cashLabel, "cash");
        t(cashSubLabel, "cash_sub");
        t(cardLabel, "card");
        t(cardSubLabel, "card_sub");

        Animations.touchFeedback(cashCard);
        Animations.touchFeedback(cardCard);
        animateCards();

        // Controlla lo stato iniziale
        updateCardStatus(com.util.NetworkWatchdog.isOnline());
        // Aggiungi un listener alla property globale per cambiarlo in tempo reale
        com.util.NetworkWatchdog.onlineProperty.addListener(networkListener);

        // Setup inactivity monitoring
        setupInactivityMonitoring();
    }

    @Override
    public void onReturn() {
        // Avvia il monitoraggio dell'inattività quando si ritorna a questo schermo
        com.util.InactivityManager.startMonitoring(Navigator.Screen.PAYMENT, rootStack);
    }

    private void setupInactivityMonitoring() {
        if (rootStack == null) return;

        // Handler per mouse e tastiera che resetta il timer
        inactivityResetMouseHandler = event -> com.util.InactivityManager.resetTimer("PaymentScreen MouseEvent");
        inactivityResetKeyHandler = event -> com.util.InactivityManager.resetTimer("PaymentScreen KeyEvent");

        // Aggiungi event filter al rootStack per catturare tutte le interazioni
        rootStack.addEventFilter(MouseEvent.ANY, inactivityResetMouseHandler);
        rootStack.addEventFilter(KeyEvent.ANY, inactivityResetKeyHandler);

        // Avvia il monitoraggio con rootStack per il dialogo di conferma
        com.util.InactivityManager.startMonitoring(Navigator.Screen.PAYMENT, rootStack);
    }

    private void updateCardStatus(boolean online) {
        if (cardCard != null) {
            cardCard.setDisable(!online);
            if (!online) {
                cardSubLabel.setText("NON DISPONIBILE OFFLINE");
            } else {
                t(cardSubLabel, "card_sub");
            }
        }
    }

    @FXML
    private void onCash() {
        proceedWith("cash");
    }

    @FXML
    private void onCard() {
        proceedWith("card");
    }

    @FXML
    private void onBack() {
        Navigator.goTo(Navigator.Screen.CART);
    }

    private void proceedWith(String method) {
        VBox chosen = "cash".equals(method) ? cashCard : cardCard;
        if (chosen != null)
            chosen.getStyleClass().add("payment-card-selected");
        setVisible(cashCard, true);
        cashCard.setDisable(true);
        setVisible(cardCard, true);
        cardCard.setDisable(true);

        com.app.model.OrderStateManager.get().setPaymentMethod(method);

        com.app.components.ModalDialog saving = com.app.components.ModalDialog.loading(rootPane, "Elaborazione...");
        
        com.app.model.OrderQueue.createOrderAsync(
            CartManager.get(),
            method,
            () -> {
                saving.dismiss();
                int orderId = com.app.model.OrderStateManager.get().getCurrentOrderId();
                Navigator.goTo(Navigator.Screen.CONFIRM, String.valueOf(orderId));
            },
            err -> {
                saving.dismiss();
                System.err.println("[PaymentController] Errore creazione ordine: " + err);
                cashCard.setDisable(false);
                cardCard.setDisable(false);
                if (chosen != null) chosen.getStyleClass().remove("payment-card-selected");
                com.app.components.ModalDialog.error(rootPane, "Errore", "Impossibile inviare l'ordine");
            }
        );
    }

    private void animateCards() {
        javafx.application.Platform.runLater(() -> {
            VBox[] cards = { cashCard, cardCard };
            for (int i = 0; i < cards.length; i++) {
                if (cards[i] == null)
                    continue;
                cards[i].setOpacity(0);
                cards[i].setTranslateY(60);
                int delay = i * 140;
                TranslateTransition translate = new TranslateTransition(Duration.millis(420), cards[i]);
                translate.setToY(0);
                translate.setDelay(Duration.millis(delay));
                translate.setInterpolator(Interpolator.EASE_OUT);

                FadeTransition fade = new FadeTransition(Duration.millis(420), cards[i]);
                fade.setToValue(1);
                fade.setDelay(Duration.millis(delay));

                ParallelTransition pt = new ParallelTransition(translate, fade);
                pt.play();
            }
        });
    }
}
