package com.app.components;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.List;

/**
 * ProductCard — card prodotto con supporto immagine opzionale.
 *
 * Layout CON immagine:
 * ┌─────────────────────────────────────┐
 * │ [ IMAGE 16:9 con overlay gradiente ] │
 * ├─────────────────────────────────────┤
 * │ NOME PRODOTTO │ bold grande
 * │ Descrizione breve... │ grigio
 * │ ───────────────────────────────── │
 * │ € 12,50 ⚠ Glutine Latte │ prezzo + chips
 * └─────────────────────────────────────┘
 *
 * Layout SENZA immagine:
 * ┌─────────────────────────────────────┐
 * │ ● (icona placeholder colorata) │
 * │ NOME PRODOTTO │
 * │ Descrizione breve... │
 * │ ───────────────────────────────── │
 * │ € 12,50 ⚠ Glutine Latte │
 * └─────────────────────────────────────┘
 */
public class ProductCard extends VBox {

    // ── Costanti layout ──────────────────────────────────────────────────────
    private static final double IMAGE_HEIGHT = 180;
    private static final double CORNER_RADIUS = 16;
    private static final double CONTENT_PAD = 18;

    // ── Costruttore senza immagine ───────────────────────────────────────────
    public ProductCard(String name, String price,
            String description, List<String> allergens) {
        this(name, price, description, allergens, null, null);
    }

    // ── Costruttore con URL immagine ─────────────────────────────────────────
    public ProductCard(String name, String price,
            String description, List<String> allergens,
            String imageUrl) {
        this(name, price, description, allergens, imageUrl, null);
    }

    // ── Costruttore con oggetto Image già caricato ───────────────────────────
    public ProductCard(String name, String price,
            String description, List<String> allergens,
            String imageUrl, Image preloadedImage) {
        super(0);
        getStyleClass().add("prod-card");
        setAlignment(Pos.TOP_LEFT);
        setMaxWidth(Double.MAX_VALUE);

        // ── Sezione immagine (o placeholder) ─────────────────────────────────
        Region imageSection = buildImageSection(imageUrl, preloadedImage, name);
        getChildren().add(imageSection);

        // ── Sezione contenuto ─────────────────────────────────────────────────
        VBox content = buildContent(name, price, description, allergens);
        getChildren().add(content);

        // ── Animazione press ──────────────────────────────────────────────────
        addPressAnimation();
    }

    // =========================================================================
    // Sezione immagine
    // =========================================================================
    private Region buildImageSection(String imageUrl, Image preloadedImage, String name) {
        StackPane container = new StackPane();
        container.setPrefHeight(IMAGE_HEIGHT);
        container.setMinHeight(IMAGE_HEIGHT);
        container.setMaxHeight(IMAGE_HEIGHT);
        container.setMaxWidth(Double.MAX_VALUE);

        // Clip arrotondato in alto (angoli inferiori squadrati per raccordarsi col
        // body)
        Rectangle clip = new Rectangle();
        clip.setArcWidth(CORNER_RADIUS * 2);
        clip.setArcHeight(CORNER_RADIUS * 2);
        clip.widthProperty().bind(container.widthProperty());
        clip.heightProperty().bind(container.heightProperty()
                .add(CORNER_RADIUS)); // nasconde angoli arrotondati in basso
        container.setClip(clip);

        if (preloadedImage != null || (imageUrl != null && !imageUrl.isBlank())) {
            // ── Immagine reale ────────────────────────────────────────────────
            ImageView iv = new ImageView();
            iv.setPreserveRatio(false);
            iv.setFitWidth(Double.MAX_VALUE);
            iv.setFitHeight(IMAGE_HEIGHT);
            iv.fitWidthProperty().bind(container.widthProperty());

            if (preloadedImage != null) {
                iv.setImage(preloadedImage);
            } else {
                // Caricamento asincrono — mostra placeholder mentre carica
                Image img = new Image(imageUrl, true);
                iv.setImage(img);
                img.progressProperty().addListener((obs, o, n) -> {
                    if (n.doubleValue() >= 1.0 && img.isError()) {
                        // Fallback se immagine non caricabile
                        container.getChildren().setAll(buildPlaceholder(name));
                    }
                });
            }

            // Overlay sfumato (bottom) per leggibilità se in futuro si vuole
            // sovrapporre testo sull'immagine
            Region overlay = new Region();
            overlay.getStyleClass().add("prod-card-img-overlay");
            overlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

            container.getChildren().addAll(iv, overlay);

        } else {
            // ── Placeholder colorato ──────────────────────────────────────────
            container.getChildren().add(buildPlaceholder(name));
        }

        return container;
    }

    /** Placeholder grafico quando non c'è immagine */
    private Region buildPlaceholder(String name) {
        StackPane ph = new StackPane();
        ph.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        ph.getStyleClass().add("prod-card-placeholder");

        // Iniziale del nome come icona decorativa
        String initial = (name != null && !name.isBlank())
                ? String.valueOf(name.charAt(0)).toUpperCase()
                : "?";

        Label lbl = new Label(initial);
        lbl.getStyleClass().add("prod-card-placeholder-letter");

        ph.getChildren().add(lbl);
        return ph;
    }

    // =========================================================================
    // Sezione contenuto (nome, desc, prezzo, allergeni)
    // =========================================================================
    private VBox buildContent(String name, String price,
            String description, List<String> allergens) {
        VBox content = new VBox(10);
        content.setPadding(new Insets(CONTENT_PAD));
        content.setAlignment(Pos.TOP_LEFT);
        content.setMaxWidth(Double.MAX_VALUE);

        // ── Nome ──────────────────────────────────────────────────────────────
        Label nameLbl = new Label(name == null ? "" : name);
        nameLbl.getStyleClass().add("prod-name");
        nameLbl.setWrapText(true);
        nameLbl.setMaxWidth(Double.MAX_VALUE);

        // ── Descrizione ───────────────────────────────────────────────────────
        Label descLbl = new Label(description == null ? "" : description);
        descLbl.getStyleClass().add("prod-desc");
        descLbl.setWrapText(true);
        descLbl.setMaxHeight(58);

        content.getChildren().addAll(nameLbl, descLbl);

        // ── Separatore ────────────────────────────────────────────────────────
        Region divider = new Region();
        divider.getStyleClass().add("prod-card-divider");
        divider.setPrefHeight(1);
        divider.setMaxWidth(Double.MAX_VALUE);
        content.getChildren().add(divider);

        // ── Riga inferiore: prezzo | allergeni ────────────────────────────────
        HBox footer = buildFooter(price, allergens);
        VBox.setVgrow(footer, Priority.ALWAYS);
        content.getChildren().add(footer);

        return content;
    }

    /** Riga prezzo + chips allergeni */
    private HBox buildFooter(String price, List<String> allergens) {
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setMaxWidth(Double.MAX_VALUE);

        // Prezzo
        Label priceLbl = new Label(
                (price == null || price.isBlank()) ? "" : price);
        priceLbl.getStyleClass().add("prod-price");
        priceLbl.setMinWidth(Region.USE_PREF_SIZE); // ← non comprimere mai
        priceLbl.setMaxWidth(Region.USE_PREF_SIZE); // ← dimensione fissa
        priceLbl.setEllipsisString(""); // ← niente "..."
        HBox.setHgrow(priceLbl, Priority.NEVER);
        footer.getChildren().add(priceLbl);

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        footer.getChildren().add(spacer);

        // Chips allergeni
        if (allergens != null && !allergens.isEmpty()) {
            javafx.scene.layout.FlowPane chips = new javafx.scene.layout.FlowPane();
            chips.setHgap(5);
            chips.setVgap(5);
            chips.setAlignment(Pos.CENTER_RIGHT);
            chips.getStyleClass().add("prod-allergen-chips");

            for (String a : allergens) {
                Label chip = new Label(a);
                chip.getStyleClass().add("prod-allergen-chip");
                chips.getChildren().add(chip);
            }
            footer.getChildren().add(chips);
        }

        return footer;
    }

    // =========================================================================
    // Animazione press
    // =========================================================================
    private void addPressAnimation() {
        addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            setScaleX(0.97);
            setScaleY(0.97);
        });

        addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(120), this);
            st.setToX(1.0);
            st.setToY(1.0);
            st.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
            st.play();
        });

        addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
            setScaleX(1.0);
            setScaleY(1.0);
        });
    }
}