package com.app.components;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import com.app.pojo.Product;

import java.util.List;

/**
 * ProductCard — card prodotto con immagine opzionale.
 *
 * Refactoring rispetto all'originale:
 *  - Aggiunto costruttore da {@link Product} POJO
 *  - I chip allergeni usano {@link ChipFactory} invece di label inline
 *  - Costanti di layout come static final
 */
public class ProductCard extends VBox {

    private static final double IMAGE_HEIGHT  = 180;
    private static final double CORNER_RADIUS = 16;
    private static final double CONTENT_PAD   = 18;

    // ── Factory da POJO ──────────────────────────────────────────────

    /** Costruisce la card direttamente da un Product POJO. */
    public static ProductCard from(Product p) {
        return new ProductCard(p.nome, p.prezzoFmt, p.descrizione, p.allergeni,
                               p.hasImage() ? p.imageUrl : null, null);
    }

    // ── Costruttori pubblici ─────────────────────────────────────────

    public ProductCard(String name, String price, String description, List<String> allergens) {
        this(name, price, description, allergens, null, null);
    }

    public ProductCard(String name, String price, String description, List<String> allergens, String imageUrl) {
        this(name, price, description, allergens, imageUrl, null);
    }

    public ProductCard(String name, String price, String description, List<String> allergens,
                       String imageUrl, Image preloadedImage) {
        super(0);
        getStyleClass().add("prod-card");
        setAlignment(Pos.TOP_LEFT);
        setMaxWidth(Double.MAX_VALUE);

        getChildren().add(buildImageSection(imageUrl, preloadedImage, name));
        getChildren().add(buildContent(name, price, description, allergens));
        addPressAnimation();
    }

    // ── Sezione immagine ─────────────────────────────────────────────

    private Region buildImageSection(String imageUrl, Image preloaded, String name) {
        StackPane container = new StackPane();
        container.setPrefHeight(IMAGE_HEIGHT);
        container.setMinHeight(IMAGE_HEIGHT);
        container.setMaxHeight(IMAGE_HEIGHT);
        container.setMaxWidth(Double.MAX_VALUE);

        Rectangle clip = new Rectangle();
        clip.setArcWidth(CORNER_RADIUS * 2);
        clip.setArcHeight(CORNER_RADIUS * 2);
        clip.widthProperty().bind(container.widthProperty());
        clip.heightProperty().bind(container.heightProperty().add(CORNER_RADIUS));
        container.setClip(clip);

        if (preloaded != null || (imageUrl != null && !imageUrl.isBlank())) {
            ImageView iv = new ImageView();
            iv.setPreserveRatio(false);
            iv.setFitHeight(IMAGE_HEIGHT);
            iv.fitWidthProperty().bind(container.widthProperty());

            if (preloaded != null) {
                iv.setImage(preloaded);
            } else {
                Image img = new Image(imageUrl, true);
                iv.setImage(img);
                img.progressProperty().addListener((obs, o, n) -> {
                    if (n.doubleValue() >= 1.0 && img.isError())
                        container.getChildren().setAll(buildPlaceholder(name));
                });
            }

            Region overlay = new Region();
            overlay.getStyleClass().add("prod-card-img-overlay");
            overlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            container.getChildren().addAll(iv, overlay);
        } else {
            container.getChildren().add(buildPlaceholder(name));
        }
        return container;
    }

    private Region buildPlaceholder(String name) {
        StackPane ph = new StackPane();
        ph.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        ph.getStyleClass().add("prod-card-placeholder");
        String initial = (name != null && !name.isBlank()) ? String.valueOf(name.charAt(0)).toUpperCase() : "?";
        Label lbl = new Label(initial);
        lbl.getStyleClass().add("prod-card-placeholder-letter");
        ph.getChildren().add(lbl);
        return ph;
    }

    // ── Sezione contenuto ────────────────────────────────────────────

    private VBox buildContent(String name, String price, String description, List<String> allergens) {
        VBox content = new VBox(10);
        content.setPadding(new Insets(CONTENT_PAD));
        content.setAlignment(Pos.TOP_LEFT);
        content.setMaxWidth(Double.MAX_VALUE);

        Label nameLbl = new Label(name == null ? "" : name);
        nameLbl.getStyleClass().add("prod-name");
        nameLbl.setWrapText(true);
        nameLbl.setMaxWidth(Double.MAX_VALUE);

        Label descLbl = new Label(description == null ? "" : description);
        descLbl.getStyleClass().add("prod-desc");
        descLbl.setWrapText(true);
        descLbl.setMaxHeight(58);

        Region divider = new Region();
        divider.getStyleClass().add("prod-card-divider");
        divider.setPrefHeight(1);
        divider.setMaxWidth(Double.MAX_VALUE);

        content.getChildren().addAll(nameLbl, descLbl, divider, buildFooter(price, allergens));
        return content;
    }

    private HBox buildFooter(String price, List<String> allergens) {
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setMaxWidth(Double.MAX_VALUE);

        Label priceLbl = new Label(price == null ? "" : price);
        priceLbl.getStyleClass().add("prod-price");
        priceLbl.setMinWidth(Region.USE_PREF_SIZE);
        priceLbl.setMaxWidth(Region.USE_PREF_SIZE);
        priceLbl.setEllipsisString("");
        HBox.setHgrow(priceLbl, Priority.NEVER);
        footer.getChildren().add(priceLbl);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        footer.getChildren().add(spacer);

        // Chip allergeni via ChipFactory invece di Label inline
        if (allergens != null && !allergens.isEmpty()) {
            footer.getChildren().add(ChipFactory.allergenPane(allergens));
        }
        return footer;
    }

    // ── Animazione press ─────────────────────────────────────────────

    private void addPressAnimation() {
        addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED,  e -> { setScaleX(0.97); setScaleY(0.97); });
        addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(120), this);
            st.setToX(1); st.setToY(1);
            st.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
            st.play();
        });
        addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> { setScaleX(1); setScaleY(1); });
    }
}
