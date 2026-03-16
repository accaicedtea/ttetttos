package com.example;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import com.example.model.CartManager;
import com.example.model.I18n;
import com.example.model.OrderQueue;
import com.util.Animations;

public class PaymentController {

    @FXML private Label titleLabel;
    @FXML private Label totalLabel;
    @FXML private Label cashLabel, cashSubLabel;
    @FXML private Label cardLabel, cardSubLabel;
    @FXML private VBox  cashCard;
    @FXML private VBox  cardCard;

    @FXML
    private void initialize() {
        titleLabel.setText(I18n.t("payment_title"));
        totalLabel.setText(CartManager.get().totalPriceFormatted());
        cashLabel.setText(I18n.t("cash"));
        cashSubLabel.setText(I18n.t("cash_sub"));
        cardLabel.setText(I18n.t("card"));
        cardSubLabel.setText(I18n.t("card_sub"));

        if (cashCard != null) Animations.touchFeedback(cashCard);
        if (cardCard != null) Animations.touchFeedback(cardCard);

        animateCards();
    }

    @FXML private void onCash() { proceedWith("cash"); }
    @FXML private void onCard() { proceedWith("card"); }
    @FXML private void onBack() { Navigator.goTo(Navigator.Screen.CART); }

    private void proceedWith(String method) {
        // Evidenzia la card scelta
        VBox chosen = method.equals("cash") ? cashCard : cardCard;
        if (chosen != null) chosen.getStyleClass().add("payment-card-selected");

        // Disabilita entrambe le card per evitare doppio click
        if (cashCard != null) cashCard.setDisable(true);
        if (cardCard != null) cardCard.setDisable(true);

        // Invia ordine al server (o coda offline) poi naviga a CONFIRM
        OrderQueue.submitOrder(CartManager.get(), method, orderNumber -> {
            Navigator.goTo(Navigator.Screen.CONFIRM, orderNumber); // String tipo "2026-000042"
        });
    }

    private void animateCards() {
        javafx.application.Platform.runLater(() -> {
            VBox[] cards = {cashCard, cardCard};
            for (int i = 0; i < cards.length; i++) {
                if (cards[i] == null) continue;
                cards[i].setOpacity(0);
                cards[i].setTranslateY(60);
                TranslateTransition tt = new TranslateTransition(Duration.millis(420), cards[i]);
                tt.setToY(0);
                tt.setDelay(Duration.millis(i * 140));
                tt.setInterpolator(Interpolator.EASE_OUT);
                FadeTransition ft = new FadeTransition(Duration.millis(420), cards[i]);
                ft.setToValue(1);
                ft.setDelay(Duration.millis(i * 140));
                new ParallelTransition(tt, ft).play();
            }
        });
    }
}
