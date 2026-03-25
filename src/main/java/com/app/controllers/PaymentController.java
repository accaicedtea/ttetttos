package com.app.controllers;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
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
 *  - Estende BaseController (t(), setVisible())
 *  - Nessun'altra modifica funzionale
 */
public class PaymentController extends BaseController {

    @FXML private Label    titleLabel;
    @FXML private Label    totalLabel;
    @FXML private Label    cashLabel, cashSubLabel;
    @FXML private Label    cardLabel, cardSubLabel;
    @FXML private VBox     cashCard;
    @FXML private VBox     cardCard;
    @FXML private StackPane rootPane;

    @FXML
    private void initialize() {
        this.rootStack = rootPane;

        t(titleLabel,  "payment_title");
        if (totalLabel != null) totalLabel.setText(CartManager.get().totalPriceFormatted());
        t(cashLabel,    "cash");
        t(cashSubLabel, "cash_sub");
        t(cardLabel,    "card");
        t(cardSubLabel, "card_sub");

        Animations.touchFeedback(cashCard);
        Animations.touchFeedback(cardCard);
        animateCards();
    }

    @FXML private void onCash() { proceedWith("cash"); }
    @FXML private void onCard() { proceedWith("card"); }
    @FXML private void onBack() { Navigator.goTo(Navigator.Screen.CART); }

    private void proceedWith(String method) {
        VBox chosen = "cash".equals(method) ? cashCard : cardCard;
        if (chosen != null) chosen.getStyleClass().add("payment-card-selected");
        setVisible(cashCard, true); cashCard.setDisable(true);
        setVisible(cardCard, true); cardCard.setDisable(true);

        OrderQueue.submitOrder(CartManager.get(), method,
            orderNumber -> Navigator.goTo(Navigator.Screen.CONFIRM, orderNumber));
    }

    private void animateCards() {
        javafx.application.Platform.runLater(() -> {
            VBox[] cards = {cashCard, cardCard};
            for (int i = 0; i < cards.length; i++) {
                if (cards[i] == null) continue;
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
