package com.example.components;

import com.example.model.Allergen;
import com.example.model.Ingredient;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;

import java.util.List;

/**
 * Card riutilizzabile per un prodotto.
 *
 * Utilizzo:
 *   ProductCard card = new ProductCard("Coca Cola", "Bibite", "/path/to/image.jpg");
 *   ProductCard card = new ProductCard("Coca Cola", "Bibite", null); // senza immagine
 */
public class ProductCard extends VBox {

    // ── Dimensioni di default ────────────────────────────────────
    public static final double DEFAULT_WIDTH  = 180;
    public static final double DEFAULT_HEIGHT = 230;
    public static final double IMAGE_HEIGHT   = 130;

    // ── Colori palette dark ──────────────────────────────────────
    private static final String BG_COLOR      = "#1f1f28";
    private static final String BORDER_COLOR  = "#2a2a3a";
    private static final String TEXT_PRIMARY   = "#e0e0ea";
    private static final String TEXT_SECONDARY = "#8888a0";
    private static final String ACCENT        = "#5b9cf5";
    private static final String PLACEHOLDER   = "#2c2c3a";

    // ── Dati ─────────────────────────────────────────────────────
    private String productName;
    private String categoryName;
    private String imageUrl;

    // ── Nodi interni ─────────────────────────────────────────────
    private final StackPane imageContainer;
    private final ImageView imageView;
    private final Label     nameLbl;
    private final Label     categoryLbl;
    private final Label     priceLbl;
    private final FlowPane  allergensRow;
    private final VBox      ingredientsBox;

    // ─────────────────────────────────────────────────────────────

    public ProductCard(String productName, String categoryName, String imageUrl) {
        this.productName  = productName;
        this.categoryName = categoryName;
        this.imageUrl     = imageUrl;

        // ── Struttura ────────────────────────────────────────────
        setAlignment(Pos.TOP_CENTER);
        setSpacing(0);
        setPrefWidth(DEFAULT_WIDTH);
        setPrefHeight(DEFAULT_HEIGHT);
        setMaxWidth(DEFAULT_WIDTH);

        applyCardStyle(false);

        // ── Immagine ─────────────────────────────────────────────
        imageView = new ImageView();
        imageView.setFitWidth(DEFAULT_WIDTH);
        imageView.setFitHeight(IMAGE_HEIGHT);
        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);

        // clip arrotondato in cima
        Rectangle clip = new Rectangle(DEFAULT_WIDTH, IMAGE_HEIGHT);
        clip.setArcWidth(14);
        clip.setArcHeight(14);
        imageView.setClip(clip);

        // placeholder con emoji
        Label placeholderLbl = new Label("🛒");
        placeholderLbl.setStyle("-fx-font-size: 40px;");

        imageContainer = new StackPane(imageView, placeholderLbl);
        imageContainer.setPrefSize(DEFAULT_WIDTH, IMAGE_HEIGHT);
        imageContainer.setMaxWidth(Double.MAX_VALUE);
        imageContainer.setStyle("-fx-background-color: " + PLACEHOLDER + ";"
                + "-fx-background-radius: 12 12 0 0;");

        loadImage(imageUrl);

        // ── Badge categoria ──────────────────────────────────────
        categoryLbl = new Label(categoryName != null ? categoryName.toUpperCase() : "");
        categoryLbl.setStyle(
                "-fx-font-size: 10px;"
                + "-fx-font-weight: bold;"
                + "-fx-text-fill: " + ACCENT + ";"
                + "-fx-background-color: rgba(91,156,245,0.12);"
                + "-fx-background-radius: 6;"
                + "-fx-padding: 2 8 2 8;"
        );

        HBox categoryRow = new HBox(categoryLbl);
        categoryRow.setAlignment(Pos.CENTER_LEFT);
        categoryRow.setPadding(new Insets(10, 12, 4, 12));

        // ── Nome prodotto ────────────────────────────────────────
        nameLbl = new Label(productName);
        nameLbl.setWrapText(true);
        nameLbl.setMaxWidth(DEFAULT_WIDTH - 24);
        nameLbl.setStyle(
                "-fx-font-size: 13px;"
                + "-fx-font-weight: bold;"
                + "-fx-text-fill: " + TEXT_PRIMARY + ";"
        );

        // ── Prezzo (opzionale, visibile solo se impostato) ───────
        priceLbl = new Label();
        priceLbl.setStyle(
                "-fx-font-size: 13px;"
                + "-fx-font-weight: bold;"
                + "-fx-text-fill: #ffffff;"
        );
        priceLbl.setVisible(false);
        priceLbl.setManaged(false);

        // ── Allergeni ─────────────────────────────────────────
        allergensRow = new FlowPane(4, 3);
        allergensRow.setVisible(false);
        allergensRow.setManaged(false);

        // ── Ingredienti ──────────────────────────────────────────
        ingredientsBox = new VBox(2);
        ingredientsBox.setVisible(false);
        ingredientsBox.setManaged(false);

        VBox textBox = new VBox(4, categoryRow, nameLbl, priceLbl, allergensRow, ingredientsBox);
        textBox.setPadding(new Insets(0, 12, 12, 12));
        VBox.setVgrow(textBox, Priority.ALWAYS);

        getChildren().addAll(imageContainer, textBox);
    }

    // ─────────────────────────────────────────────────────────────
    // Stili
    // ─────────────────────────────────────────────────────────────

    private void applyCardStyle(boolean hovered) {
        setStyle(
                "-fx-background-color: " + BG_COLOR + ";"
                + "-fx-background-radius: 14;"
                + "-fx-border-color: " + BORDER_COLOR + ";"
                + "-fx-border-radius: 14;"
                + "-fx-border-width: 1;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 8, 0, 0, 2);"
        );
    }

    // ─────────────────────────────────────────────────────────────
    // Immagine
    // ─────────────────────────────────────────────────────────────

    private void loadImage(String url) {
        if (url == null || url.isBlank()) {
            imageView.setImage(null);
            imageView.setVisible(false);
            return;
        }
        try {
            Image img = new Image(url, DEFAULT_WIDTH, IMAGE_HEIGHT, false, true, true);
            img.errorProperty().addListener((obs, old, err) -> {
                if (err) { imageView.setVisible(false); }
            });
            imageView.setImage(img);
            imageView.setVisible(true);
        } catch (Exception e) {
            imageView.setVisible(false);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Setters fluenti
    // ─────────────────────────────────────────────────────────────

    public ProductCard withName(String name) {
        this.productName = name;
        nameLbl.setText(name);
        return this;
    }

    public ProductCard withCategory(String category) {
        this.categoryName = category;
        categoryLbl.setText(category != null ? category.toUpperCase() : "");
        return this;
    }

    public ProductCard withImage(String url) {
        this.imageUrl = url;
        imageView.setVisible(true);
        loadImage(url);
        return this;
    }

    public ProductCard withPrice(String price) {
        if (price != null && !price.isBlank()) {
            priceLbl.setText(price);
            priceLbl.setVisible(true);
            priceLbl.setManaged(true);
        }
        return this;
    }

    public ProductCard withAllergens(List<Allergen> list) {
        if (list == null || list.isEmpty()) return this;
        allergensRow.getChildren().clear();
        for (Allergen a : list) {
            Label badge = new Label(a.name);
            badge.setStyle(
                    "-fx-font-size: 11px;"
                    + "-fx-text-fill: #e67e22;"
                    + "-fx-background-color: rgba(230,126,34,0.15);"
                    + "-fx-background-radius: 4;"
                    + "-fx-padding: 2 6 2 6;"
            );
            allergensRow.getChildren().add(badge);
        }
        allergensRow.setVisible(true);
        allergensRow.setManaged(true);
        setPrefHeight(USE_COMPUTED_SIZE);
        return this;
    }

    public ProductCard withIngredients(List<Ingredient> list) {
        if (list == null || list.isEmpty()) return this;
        ingredientsBox.getChildren().clear();

        StringBuilder sb = new StringBuilder();
        for (int idx = 0; idx < list.size(); idx++) {
            if (idx > 0) sb.append(", ");
            sb.append(list.get(idx).name);
        }

        Label title = new Label("Ingredienti");
        title.setStyle("-fx-font-size: 10px; -fx-text-fill: #8888a0; -fx-font-weight: bold;");

        Label ingrLbl = new Label(sb.toString());
        ingrLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #c0c0d0;");
        ingrLbl.setWrapText(true);
        ingrLbl.setMaxWidth(DEFAULT_WIDTH - 24);

        ingredientsBox.getChildren().addAll(title, ingrLbl);
        ingredientsBox.setVisible(true);
        ingredientsBox.setManaged(true);
        setPrefHeight(USE_COMPUTED_SIZE);
        return this;
    }

    /** Sostituisce l'emoji del placeholder (default 🛒). */
    public ProductCard withPlaceholderEmoji(String emoji) {
        // il primo figlio di imageContainer è imageView, il secondo è la label
        if (imageContainer.getChildren().size() > 1
                && imageContainer.getChildren().get(1) instanceof Label lbl) {
            lbl.setText(emoji);
        }
        return this;
    }

    public ProductCard withSize(double width, double height) {
        setPrefWidth(width);
        setPrefHeight(height);
        setMaxWidth(width);
        imageView.setFitWidth(width);
        nameLbl.setMaxWidth(width - 24);
        imageContainer.setPrefSize(width, height - 100);
        imageView.setFitHeight(height - 100);
        return this;
    }

    // ─────────────────────────────────────────────────────────────
    // Getters
    // ─────────────────────────────────────────────────────────────

    public String getProductName()  { return productName; }
    public String getCategoryName() { return categoryName; }
    public String getImageUrl()     { return imageUrl; }
}
