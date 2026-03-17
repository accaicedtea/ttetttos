package com.example;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.util.Duration;

import com.api.services.ViewsService;
import com.example.NetworkWatchdog;
import com.example.RemoteLogger;
import com.example.model.MenuCache;
import com.example.components.ProductCard;
import com.example.model.CartManager;
import com.example.model.I18n;
import com.google.gson.*;
import com.util.Animations;

import java.util.*;

public class ShopPageController implements Navigator.DataReceiver, Navigator.ScreenReturnable {

    // ── Header (componente riutilizzabile) ──────────────────────────────
    @FXML private VBox header;
    @FXML private ShopHeaderController headerController;
    @FXML private StackPane rootStack;
    @FXML private Button addToCartBtn;

    // ── Prodotti ──────────────────────────────────────────────────────
    @FXML private ScrollPane productsScroll;
    @FXML private FlowPane   productsPane;

      // ── Categorie ─────────────────────────────────────────────────────
    @FXML private ScrollPane categoriesScroll;
    @FXML private VBox       categoriesPane;

    // ── Modal ─────────────────────────────────────────────────────────
    @FXML private StackPane modalOverlay;
    @FXML private VBox      modalCard;
    @FXML private Label     modalTitle, modalPrice, modalDesc;
    @FXML private VBox      modalAllergenSection, modalIngredientSection;
    @FXML private FlowPane  modalAllergenChips, modalIngredients;
    @FXML private Button    modalClose;
    @FXML private javafx.scene.layout.HBox toastBox;
    @FXML private Label     toastLabel;

    // Prodotto attualmente aperto nel modal
    private String       currentModalName;
    private String       currentModalPrice;
    private double       currentModalPriceVal;
    private int          currentModalProductId;
    private int          currentModalIva;
    private String       currentModalSku;
    private java.util.List<String> currentModalAllergens;

    private Label   activeCategory = null;
    private boolean syncStarted     = false; // evita sync multipli

    // Cache menu ricevuto da WelcomeController
    private JsonObject cachedMenu = null;

    // ─────────────────────────────────────────────────────────────────
    @FXML
    private void initialize() {
        if (headerController != null) {
            headerController.setOnline(false);
            headerController.setCartCount(CartManager.get().totalItems());
            headerController.setMenuTitle("Il Nostro Menu");
        }

        if (productsScroll   != null) Animations.inertiaScroll(productsScroll);
        if (categoriesScroll != null) Animations.inertiaScroll(categoriesScroll);

        if (productsScroll != null && productsPane != null) {
            productsScroll.viewportBoundsProperty()
                .addListener((obs, o, n) -> resizeCards(n.getWidth()));
        }

        showInfo("Caricamento menu...");

        // Ascolta i cambi di stato rete dal watchdog
        NetworkWatchdog.setListener(this::setOnline);
    }

    /** Riceve il menu precaricato da WelcomeController via Navigator. */
    @Override
    public void receiveData(Object data) {
        if (data instanceof JsonObject menu) {
            cachedMenu = menu;
            // runLater garantisce che il nodo sia già nel grafo della scena
            Platform.runLater(() -> {
                JsonArray cats = extractCategories(menu);
                if (cats != null && !cats.isEmpty()) buildUI(cats);
                else loadMenu(); // fallback: ricarica

                // Avvia sync in background (una sola volta)
                if (!syncStarted) {
                    syncStarted = true;
                    MenuCache.startBackgroundSync(freshMenu -> {
                        JsonArray freshCats = extractCategories(freshMenu);
                        if (freshCats != null && !freshCats.isEmpty()) {
                            System.out.println("[Shop] Menu aggiornato da background sync.");
                            Platform.runLater(() -> buildUI(freshCats));
                        }
                    });
                }
            });
        } else {
            // data null o tipo errato: carica comunque
            Platform.runLater(this::loadMenu);
        }
    }

    /** Fallback: carica il menu direttamente (se navigato senza dati). */
    public void loadMenu() {
        if (cachedMenu != null) return;
        new Thread(() -> {
            try {
                JsonObject r = ViewsService.getMenu();
                JsonArray cats = extractCategories(r);
                if (cats == null || cats.isEmpty()) {
                    Platform.runLater(() -> showInfo("Menu vuoto."));
                    return;
                }
                Platform.runLater(() -> buildUI(cats));
            } catch (Exception e) {
                RemoteLogger.error("ShopPage", "Errore caricamento menu", e);
                Platform.runLater(() -> showInfo("Errore: " + e.getMessage()));
            }
        }, "load-menu").start();
    }

    // ── Menu ──────────────────────────────────────────────────────────

    private JsonArray extractCategories(JsonObject r) {
        if (r.has("data") && r.get("data").isJsonObject()) {
            JsonObject d = r.getAsJsonObject("data");
            if (d.has("categorie") && d.get("categorie").isJsonArray()) return d.getAsJsonArray("categorie");
            if (d.has("menu")      && d.get("menu").isJsonArray())      return d.getAsJsonArray("menu");
        }
        if (r.has("data")     && r.get("data").isJsonArray())      return r.getAsJsonArray("data");
        if (r.has("categorie") && r.get("categorie").isJsonArray()) return r.getAsJsonArray("categorie");
        return null;
    }

    private void buildUI(JsonArray categories) {
        categoriesPane.getChildren().clear();
        productsPane.getChildren().clear();

        for (int i = 0; i < categories.size(); i++) {
            JsonObject cat    = categories.get(i).getAsJsonObject();
            final String name = str(cat, "nome", "Categoria " + (i + 1));
            final JsonArray p = jsonArr(cat, "prodotti");

            Label tab = new Label(name);
            tab.getStyleClass().add("category-tab");
            tab.setMaxWidth(Double.MAX_VALUE);
            Animations.touchFeedback(tab);
            tab.setOnMouseClicked(ev -> selectCategory(tab, name, p != null ? p : new JsonArray()));
            categoriesPane.getChildren().add(tab);
        }

        // Seleziona prima categoria
        if (!categoriesPane.getChildren().isEmpty()) {
            JsonObject first = categories.get(0).getAsJsonObject();
            selectCategory((Label) categoriesPane.getChildren().get(0),
                str(first, "nome", ""),
                jsonArr(first, "prodotti") != null ? jsonArr(first, "prodotti") : new JsonArray());
        }
    }

    private void selectCategory(Label tab, String name, JsonArray prodotti) {
        if (activeCategory != null) activeCategory.getStyleClass().remove("category-tab-active");
        activeCategory = tab;
        tab.getStyleClass().add("category-tab-active");
        if (headerController != null) headerController.setCategory(name);
        showProducts(prodotti);
    }

    // ── Prodotti ──────────────────────────────────────────────────────

    private void showProducts(JsonArray prodotti) {
        productsPane.getChildren().clear();
        if (prodotti == null || prodotti.isEmpty()) { showInfo("Nessun prodotto."); return; }

        for (JsonElement el : prodotti) {
            if (!el.isJsonObject()) continue;
            JsonObject p = el.getAsJsonObject();

            String nome     = str(p, "nome",       "Prodotto");
            String prezzo   = str(p, "prezzo",      "");
            String descr    = str(p, "descrizione", "");
            String prezzoFmt = prezzo.isBlank() ? "" : "€ " + prezzo;
            double prezzoVal = 0;
            try { prezzoVal = Double.parseDouble(prezzo.replace(',', '.')); } catch (Exception ignored) {}

            List<String> allergeni   = toStringList(jsonArr(p, "allergeni"));
            List<String> ingredienti = toIngredientNames(jsonArr(p, "ingredienti"));

            final double pv = prezzoVal;
            ProductCard card = new ProductCard(nome, prezzoFmt, descr, allergeni);
            // NO touchFeedback qui — la card gestisce internamente il feedback
            // visivo tramite MOUSE_PRESSED/RELEASED per non interferire con il click
            card.setOnMouseClicked(ev -> {
                // Risponde a qualsiasi button (touch sintetizza PRIMARY)
                showModal(nome, prezzoFmt, pv, descr, allergeni, ingredienti);
            });
            productsPane.getChildren().add(card);
        }

        double w = productsScroll.getViewportBounds().getWidth();
        if (w > 0) resizeCards(w);
        Platform.runLater(() -> resizeCards(productsScroll.getViewportBounds().getWidth()));
        productsScroll.setVvalue(0);
    }

    private void resizeCards(double vw) {
        if (productsPane == null || vw <= 0) return;
        double pad = 32, gap = productsPane.getHgap(), usable = vw - pad;
        int    cols = Math.max(1, (int) Math.floor((usable + gap) / (240 + gap)));
        double w    = (usable - (cols - 1) * gap) / cols;
        for (var node : productsPane.getChildren())
            node.setStyle("-fx-pref-width:" + w + ";-fx-min-width:" + w + ";-fx-max-width:" + w + ";");
    }

    // ── Modal ─────────────────────────────────────────────────────────

    private void showModal(String name, String price, double priceVal,
                           String desc, List<String> allergens, List<String> ingredients) {
        if (modalOverlay == null) return;

        currentModalName      = name;
        currentModalPrice     = price;
        currentModalPriceVal  = priceVal;
        currentModalProductId = 0;
        currentModalIva       = 0;
        currentModalSku       = null;
        currentModalAllergens = allergens;

        modalTitle.setText(name);
        modalPrice.setText(price);
        modalDesc.setText(desc == null ? "" : desc);
        addToCartBtn.setText(I18n.t("add_to_cart"));

        // Allergeni
        modalAllergenChips.getChildren().clear();
        if (allergens != null && !allergens.isEmpty()) {
            for (String a : allergens) {
                Label c = new Label(a); c.getStyleClass().add("allergen-chip");
                modalAllergenChips.getChildren().add(c);
            }
            modalAllergenSection.setVisible(true); modalAllergenSection.setManaged(true);
        } else {
            modalAllergenSection.setVisible(false); modalAllergenSection.setManaged(false);
        }

        // Ingredienti
        modalIngredients.getChildren().clear();
        if (ingredients != null && !ingredients.isEmpty()) {
            for (String i : ingredients) {
                Label c = new Label(i); c.getStyleClass().add("ingredient-chip");
                modalIngredients.getChildren().add(c);
            }
            modalIngredientSection.setVisible(true); modalIngredientSection.setManaged(true);
        } else {
            modalIngredientSection.setVisible(false); modalIngredientSection.setManaged(false);
        }

        if (rootStack != null && modalCard != null) {
            double w = Math.min(860, rootStack.getWidth()  * 0.88);
            double h = Math.min(920, rootStack.getHeight() * 0.88);
            modalCard.setMaxWidth(w); modalCard.setPrefWidth(w); modalCard.setMaxHeight(h);
        }

        modalOverlay.setManaged(true); modalOverlay.setVisible(true);
        modalClose.requestFocus();
    }

    @FXML private void hideModal() {
        if (modalOverlay == null) return;
        modalOverlay.setVisible(false); modalOverlay.setManaged(false);
    }

    @FXML private void onModalOverlayClicked(javafx.scene.input.MouseEvent e) {
        if (e.getTarget() == modalOverlay) hideModal();
    }

    // ── Carrello ──────────────────────────────────────────────────────

    @FXML
    private void onAddToCart() {
        if (currentModalName == null) return;
        CartManager.get().addItem(currentModalProductId, currentModalName,
                currentModalPrice, currentModalPriceVal,
                currentModalIva, currentModalSku, currentModalAllergens);
        hideModal();
        if (headerController != null) {
            headerController.setCartCount(CartManager.get().totalItems());
            headerController.bounceCart();
        }
        showToast(I18n.t("added"));
    }

    /** Toast "Aggiunto!" che appare in basso e svanisce. */
    private void showToast(String msg) {
        if (toastBox == null) return;
        if (toastLabel != null) toastLabel.setText(msg);
        toastBox.setVisible(true); toastBox.setManaged(true);
        toastBox.setOpacity(0); toastBox.setTranslateY(20);

        Timeline tl = new Timeline(
            new KeyFrame(Duration.millis(0),
                new KeyValue(toastBox.opacityProperty(), 0),
                new KeyValue(toastBox.translateYProperty(), 20)),
            new KeyFrame(Duration.millis(250),
                new KeyValue(toastBox.opacityProperty(), 1),
                new KeyValue(toastBox.translateYProperty(), 0)),
            new KeyFrame(Duration.millis(1400),
                new KeyValue(toastBox.opacityProperty(), 1)),
            new KeyFrame(Duration.millis(1800),
                new KeyValue(toastBox.opacityProperty(), 0))
        );
        tl.setOnFinished(e -> { toastBox.setVisible(false); toastBox.setManaged(false); });
        tl.play();
    }

    /**
     * Chiamato da Navigator quando si torna al menu dalla cache.
     * Aggiorna il badge del carrello (potrebbe essere cambiato).
     */
    @Override
    public void onReturn() {
        if (headerController != null) headerController.setCartCount(CartManager.get().totalItems());
        // Ri-registra il listener (potrebbe essere stato sovrascritto da altri screen)
        NetworkWatchdog.setListener(this::setOnline);
    }

    public void setOnline(boolean online) {
        if (headerController != null) headerController.setOnline(online);
    }

    public void showError(String msg) { showInfo(msg); }

    private void showInfo(String msg) {
        if (productsPane == null) return;
        productsPane.getChildren().clear();
        Label l = new Label(msg); l.getStyleClass().add("info-label");
        l.setWrapText(true); productsPane.getChildren().add(l);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String str(JsonObject o, String k, String fb) {
        return (o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : fb;
    }
    private JsonArray jsonArr(JsonObject o, String k) {
        return (o.has(k) && o.get(k).isJsonArray()) ? o.getAsJsonArray(k) : null;
    }
    private List<String> toStringList(JsonArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (JsonElement el : arr) if (!el.isJsonNull()) out.add(el.getAsString());
        return out;
    }
    private List<String> toIngredientNames(JsonArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            if (o.has("nome") && !o.get("nome").isJsonNull()) out.add(o.get("nome").getAsString());
        }
        return out;
    }
}
