package com.example.controllers;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import com.util.Navigator;
import com.util.ThemeManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ShopHeaderController {

    @FXML private Label dateLabel, clockLabel, internetLabel;
    @FXML private FontIcon internetIcon;
    @FXML private Button themeBtn;
    @FXML private FontIcon themeIcon;

    @FXML private Label menuTitleLabel;
    @FXML private Label categoryTitleLabel;
    @FXML private Button cartBtn;
    @FXML private Label cartBadge;

    private final DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");
    private Timeline clock;

    @FXML
    private void initialize() {
        startClock();
        setOnline(false);
    }

    private void startClock() {
        if (clock != null) return;
        clock = new Timeline(
            new KeyFrame(Duration.ZERO, e -> updateDateTime()),
            new KeyFrame(Duration.seconds(1)));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
    }

    private void updateDateTime() {
        LocalDateTime now = LocalDateTime.now();
        if (dateLabel != null) dateLabel.setText(now.format(dateFmt));
        if (clockLabel != null) clockLabel.setText(now.format(timeFmt));
    }

    public void setOnline(boolean online) {
        if (internetLabel != null) internetLabel.setText(online ? "Online" : "Offline");
        if (internetIcon != null) {
            internetIcon.getStyleClass().removeAll("status-icon-offline", "status-icon-online");
            internetIcon.getStyleClass().add(online ? "status-icon-online" : "status-icon-offline");
        }
    }

    public void setCategory(String category) {
        if (categoryTitleLabel != null) categoryTitleLabel.setText(category);
    }

    public void setMenuTitle(String title) {
        if (menuTitleLabel != null) menuTitleLabel.setText(title);
    }

    public void setCartCount(int count) {
        if (cartBadge == null) return;
        if (count > 0) {
            cartBadge.setText(String.valueOf(count));
            cartBadge.setVisible(true);
            cartBadge.setManaged(true);
            if (cartBtn != null) {
                cartBtn.setDisable(false);
                cartBtn.setOpacity(1.0);
            }
        } else {
            cartBadge.setVisible(false);
            cartBadge.setManaged(false);
            if (cartBtn != null) {
                cartBtn.setDisable(true);
                cartBtn.setOpacity(0.35);
            }
        }
    }

    public void bounceCart() {
        if (cartBtn == null) return;
        Timeline bounce = new Timeline(
            new KeyFrame(Duration.millis(0),   new KeyValue(cartBtn.scaleXProperty(), 1.0), new KeyValue(cartBtn.scaleYProperty(), 1.0)),
            new KeyFrame(Duration.millis(60),  new KeyValue(cartBtn.scaleXProperty(), 1.35), new KeyValue(cartBtn.scaleYProperty(), 1.35)),
            new KeyFrame(Duration.millis(120), new KeyValue(cartBtn.scaleXProperty(), 0.88), new KeyValue(cartBtn.scaleYProperty(), 0.88)),
            new KeyFrame(Duration.millis(160), new KeyValue(cartBtn.scaleXProperty(), 1.10), new KeyValue(cartBtn.scaleYProperty(), 1.10)),
            new KeyFrame(Duration.millis(200), new KeyValue(cartBtn.scaleXProperty(), 1.0),  new KeyValue(cartBtn.scaleYProperty(), 1.0))
        );
        bounce.play();
    }

    @FXML
    private void toggleTheme() {
        ThemeManager.toggle();
    }

    @FXML
    private void onCartClick() {
        Navigator.goTo(Navigator.Screen.CART);
    }
}
