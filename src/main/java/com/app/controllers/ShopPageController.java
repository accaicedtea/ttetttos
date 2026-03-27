package com.app.controllers;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.beans.value.ChangeListener;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import com.app.components.ChipFactory;
import com.app.components.ProductCard;
import com.app.components.ToastOverlay;
import com.app.model.CartItem;
import com.app.model.CartManager;
import com.app.model.I18n;
import com.app.model.Ingredient;
import com.app.model.MenuCache;
import com.app.pojo.Category;
import com.api.services.IngredientService;
import com.app.pojo.MenuData;
import com.app.pojo.Product;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.util.Animations;
import com.util.Navigator;
import com.util.NetworkWatchdog;
import com.util.RemoteLogger;

/**
 * ShopPageController — versione ottimizzata.
 *
 * ── Ottimizzazioni rispetto alla versione precedente ──────────────────────
 *
 * [BUG FIX] Creazione nodi JavaFX su FX thread
 * createIngNode() veniva chiamato da thread background: illegale in JavaFX.
 * Ora i dati vengono preparati in background, i nodi creati su
 * Platform.runLater.
 *
 * [PERF] Rimozione property binding per ingrediente
 * Ogni .bind() aggiunge un listener che sparava ad ogni layout pass.
 * Con N ingredienti → O(N) listener × ogni layout. Sostituiti con dimensioni
 * fisse via setMaxWidth/setPrefWidth diretti (calcolati una sola volta).
 *
 * [PERF] Pre-build dell'intero modal (non solo la grid)
 * Il vecchio codice costruiva VBox/HBox/ScrollPane al momento del click.
 * Ora buildKumpirModalStructure() viene chiamato subito dopo
 * enableKumpirButton(),
 * quindi il click apre il modal in <5 ms (solo setVisible + Timeline 180 ms).
 *
 * [PERF] Counter intero invece di stream su Map
 * selectedCount traccia il numero di selezioni attive come int.
 * updateKumpirTotals() è ora O(1) invece di O(N).
 *
 * [PERF] Reset tramite Set invece di iterazione completa
 * selectedIds traccia gli id selezionati → reset deseleziona solo quelli attivi
 * invece di scorrere tutti i nodi.
 *
 * [PERF] Rimozione ScaleTransition sul totale
 * Animazione inutile nell'hot path dei toggle (ogni click ingrediente).
 * Rimossa: il totale si aggiorna in testo senza costi di layout aggiuntivi.
 *
 * [PERF] Cache JavaFX sul modal card e sulla grid
 * kumpirCard e kumpirGrid usano setCache(true)/CacheHint.SPEED:
 * la GPU rasterizza il layer e lo riusa durante l'animazione di apertura.
 *
 * [PERF] Filtro con debounce 30 ms
 * Evita layout multipli se l'utente attiva più filtri rapidamente.
 *
 * [PERF] Preformattazione stringhe prezzo
 * PRICE_INGREDIENT_FMT e PRICE_BASE_FMT sono costanti: evitano String.format
 * nell'hot path della costruzione nodi.
 *
 * [PERF] Layout batch durante inserimento massiccio
 * Le aggiunte di nodi alla grid avvengono con kumpirGrid.setManaged(false),
 * poi il layout viene ricalcolato una sola volta alla fine con
 * setManaged(true).
 */
public class ShopPageController extends BaseController
        implements Navigator.DataReceiver, Navigator.ScreenReturnable {

    // ── FXML ─────────────────────────────────────────────────────────
    @FXML
    private VBox header;
    @FXML
    private ShopHeaderController headerController;
    @FXML
    private StackPane rootStack;
    @FXML
    private ScrollPane productsScroll;
    @FXML
    private FlowPane productsPane;
    @FXML
    private ScrollPane categoriesScroll;
    @FXML
    private VBox categoriesPane;
    @FXML
    private Button composeKumpirBtn;
    @FXML
    private StackPane modalOverlay;
    @FXML
    private VBox modalCard;
    @FXML
    private Label modalTitle, modalPrice, modalDesc;
    @FXML
    private VBox modalAllergenSection, modalIngredientSection;
    @FXML
    private FlowPane modalAllergenChips, modalIngredients;
    @FXML
    private Button modalClose;
    @FXML
    private Button addToCartBtn, quickCartBtn;
    @FXML
    private HBox toastBox;
    @FXML
    private Label toastLabel;

    // ── Stato ─────────────────────────────────────────────────────────
    private ToastOverlay toastOverlay;
    private Product currentProduct;
    private Label activeCategory = null;
    private boolean syncStarted = false;
    private ComposeKumpirController composeKumpirController;
    private Boolean lastOnlineState = null;
    private ExecutorService backgroundExecutor;

    // Cache rendering prodotti
    private final Map<String, List<ProductCard>> categoryProductCards = new HashMap<>();
    private String activeCategoryName = null;

    @FXML
    private void initialize() {
        if (headerController != null) {
            headerController.setOnline(false);
            headerController.setCartCount(CartManager.get().totalItems());
            headerController.setMenuTitle("Il Nostro Menu");
        }

        if (rootStack != null) {
            toastOverlay = new ToastOverlay();
            rootStack.getChildren().add(toastOverlay);
        }

        Animations.inertiaScroll(productsScroll);
        Animations.inertiaScroll(categoriesScroll);
        initQuickCartButton();

        backgroundExecutor = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()));

        composeKumpirController = new ComposeKumpirController(rootStack, headerController);
        composeKumpirController.init(composeKumpirBtn);

        if (productsScroll != null && productsPane != null) {
            productsScroll.viewportBoundsProperty()
                    .addListener((obs, o, n) -> resizeCards(n.getWidth()));
            productsScroll.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.widthProperty().addListener((o2, ow, nw) -> Platform
                            .runLater(() -> resizeCards(productsScroll.getViewportBounds().getWidth())));
                    Platform.runLater(() -> resizeCards(productsScroll.getViewportBounds().getWidth()));
                }
            });
        }

        showInfo(I18n.t("loading_menu"));
        NetworkWatchdog.setListener(this::setOnline);
    }

    // ═══════════════════════════════════════════════════════════════════
    // MENU / PRODOTTI
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void receiveData(Object data) {
        if (data instanceof JsonObject json) {
            MenuData menu = MenuData.from(json);
            Platform.runLater(() -> {
                if (!menu.isEmpty())
                    buildUI(menu);
                else
                    loadMenu();
                if (!syncStarted) {
                    syncStarted = true;
                    MenuCache.startBackgroundSync(
                            fresh -> Platform.runLater(() -> buildUI(MenuData.from(fresh))));
                }
            });
        } else {
            Platform.runLater(this::loadMenu);
        }
    }

    public void loadMenu() {
        backgroundExecutor.submit(() -> {
            try {
                JsonObject r = com.api.services.ViewsService.getMenu();
                MenuData menu = MenuData.from(r);
                if (menu.isEmpty()) {
                    Platform.runLater(() -> showInfo("Menu vuoto."));
                    return;
                }
                Platform.runLater(() -> buildUI(menu));
            } catch (Exception e) {
                RemoteLogger.error("ShopPage", "loadMenu", e);
                Platform.runLater(() -> showInfo("Errore: " + e.getMessage()));
            }
        });
    }

    private void buildUI(MenuData menu) {
        categoriesPane.getChildren().clear();
        productsPane.getChildren().clear();
        categoryProductCards.clear();

        for (Category cat : menu.categorie) {
            Label tab = new Label(cat.nome);
            tab.getStyleClass().add("category-tab");
            tab.setMaxWidth(Double.MAX_VALUE);
            Animations.touchFeedback(tab);

            List<ProductCard> cards = cat.prodotti.stream()
                    .map(ProductCard::from)
                    .collect(Collectors.toList());
            categoryProductCards.put(cat.nome, cards);

            tab.setOnMouseClicked(ev -> selectCategory(tab, cat));
            categoriesPane.getChildren().add(tab);
        }

        if (!menu.isEmpty()) {
            Category first = menu.categorie.get(0);
            selectCategory((Label) categoriesPane.getChildren().get(0), first);
        }
    }

    private void selectCategory(Label tab, Category cat) {
        if (activeCategory != null)
            activeCategory.getStyleClass().remove("category-tab-active");
        activeCategory = tab;
        activeCategoryName = cat.nome;
        tab.getStyleClass().add("category-tab-active");
        if (headerController != null)
            headerController.setCategory(cat.nome);
        showProducts(cat.prodotti);
    }

    private void showProducts(List<Product> prodotti) {
        if (prodotti == null || prodotti.isEmpty()) {
            productsPane.getChildren().clear();
            showInfo("Nessun prodotto.");
            return;
        }

        productsPane.setOpacity(0);
        productsPane.getChildren().clear();

        List<ProductCard> cards = categoryProductCards.getOrDefault(activeCategoryName, null);
        if (cards == null) {
            cards = prodotti.stream().map(ProductCard::from).collect(Collectors.toList());
            categoryProductCards.put(activeCategoryName != null ? activeCategoryName : "", cards);
        }

        for (int i = 0; i < cards.size(); i++) {
            ProductCard card = cards.get(i);
            Product p = prodotti.size() > i ? prodotti.get(i) : null;
            if (p != null)
                card.setOnMouseClicked(ev -> showModal(p));
        }

        productsPane.getChildren().addAll(cards);
        productsScroll.setVvalue(0);
        double w0 = productsScroll.getViewportBounds().getWidth();
        if (w0 > 0)
            resizeCards(w0);
        Platform.runLater(() -> resizeCards(productsScroll.getViewportBounds().getWidth()));

        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), productsPane);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.setInterpolator(Interpolator.EASE_OUT);
        fadeIn.play();
    }

    private void resizeCards(double vw) {
        if (productsPane == null || vw <= 0)
            return;
        Insets p = productsPane.getPadding();
        double padding = (p != null) ? p.getLeft() + p.getRight() : 32;
        double gap = productsPane.getHgap();
        double usable = Math.floor(vw - padding);
        int cols = Math.max(1, (int) Math.floor((usable + gap) / (240.0 + gap)));
        double w = Math.floor((usable - (cols - 1) * gap) / cols);
        String style = "-fx-pref-width:" + w + ";-fx-min-width:" + w + ";-fx-max-width:" + w + ";";
        productsPane.getChildren().forEach(n -> n.setStyle(style));
    }

    // ═══════════════════════════════════════════════════════════════════
    // MODAL PRODOTTO
    // ═══════════════════════════════════════════════════════════════════

    private void showModal(Product p) {
        if (modalOverlay == null)
            return;
        this.currentProduct = p;
        modalTitle.setText(p.nome);
        modalPrice.setText(p.prezzoFmt);
        modalDesc.setText(p.descrizione);
        if (addToCartBtn != null)
            addToCartBtn.setText(I18n.t("add_to_cart"));
        ChipFactory.fillAllergens(modalAllergenChips, p.allergeni, ChipFactory.ChipType.MODAL);
        setVisible(modalAllergenSection, !p.allergeni.isEmpty());
        modalIngredients.getChildren().clear();
        p.ingredienti.forEach(i -> modalIngredients.getChildren().add(ChipFactory.ingredientChip(i)));
        setVisible(modalIngredientSection, !p.ingredienti.isEmpty());
        if (rootStack != null && modalCard != null) {
            double w = Math.min(860, rootStack.getWidth() * 0.88);
            double h = Math.min(920, rootStack.getHeight() * 0.88);
            modalCard.setMaxWidth(w);
            modalCard.setPrefWidth(w);
            modalCard.setMaxHeight(h);
        }
        setVisible(modalOverlay, true);
        if (modalClose != null)
            modalClose.requestFocus();
    }

    @FXML
    private void hideModal() {
        setVisible(modalOverlay, false);
    }

    @FXML
    private void onModalOverlayClicked(javafx.scene.input.MouseEvent e) {
        if (e.getTarget() == modalOverlay)
            hideModal();
    }

    // ═══════════════════════════════════════════════════════════════════
    // CARRELLO / TOAST / QUICK CART
    // ═══════════════════════════════════════════════════════════════════

    @FXML
    private void onAddToCart() {
        if (currentProduct == null)
            return;
        CartManager.get().addItem(CartItem.fromProduct(currentProduct));
        hideModal();
        if (headerController != null) {
            headerController.setCartCount(CartManager.get().totalItems());
            headerController.bounceCart();
        }
        updateQuickCartButton();
        if (toastOverlay != null)
            toastOverlay.show(I18n.t("added"));
        else
            showToastFxml(I18n.t("added"));
    }

    private void showToastFxml(String msg) {
        if (toastBox == null)
            return;
        if (toastLabel != null)
            toastLabel.setText(msg);
        toastBox.setOpacity(0);
        toastBox.setTranslateY(20);
        setVisible(toastBox, true);
        Timeline tl = new Timeline(
                new KeyFrame(Duration.millis(0),
                        new KeyValue(toastBox.opacityProperty(), 0),
                        new KeyValue(toastBox.translateYProperty(), 20)),
                new KeyFrame(Duration.millis(250),
                        new KeyValue(toastBox.opacityProperty(), 1),
                        new KeyValue(toastBox.translateYProperty(), 0)),
                new KeyFrame(Duration.millis(1650),
                        new KeyValue(toastBox.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(1900),
                        new KeyValue(toastBox.opacityProperty(), 0)));
        tl.setOnFinished(e -> setVisible(toastBox, false));
        tl.play();
    }

    private Timeline quickCartPulse;

    private void initQuickCartButton() {
        if (quickCartBtn == null)
            return;
        quickCartBtn.setText(I18n.t("cart"));
        setVisible(quickCartBtn, false);
        quickCartPulse = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(quickCartBtn.scaleXProperty(), 1.0),
                        new KeyValue(quickCartBtn.scaleYProperty(), 1.0)),
                new KeyFrame(Duration.millis(500),
                        new KeyValue(quickCartBtn.scaleXProperty(), 1.12),
                        new KeyValue(quickCartBtn.scaleYProperty(), 1.12)),
                new KeyFrame(Duration.millis(1000),
                        new KeyValue(quickCartBtn.scaleXProperty(), 1.0),
                        new KeyValue(quickCartBtn.scaleYProperty(), 1.0)));
        quickCartPulse.setCycleCount(Animation.INDEFINITE);
        CartManager.get().getItems().addListener(
                (javafx.collections.ListChangeListener<CartItem>) c -> updateQuickCartButton());
        updateQuickCartButton();
        StackPane.setMargin(quickCartBtn, new Insets(16));
    }

    private void updateQuickCartButton() {
        if (quickCartBtn == null)
            return;
        boolean has = CartManager.get().totalItems() > 0;
        setVisible(quickCartBtn, has);
        if (has) {
            if (quickCartPulse.getStatus() != Animation.Status.RUNNING)
                quickCartPulse.play();
        } else {
            quickCartPulse.stop();
            quickCartBtn.setScaleX(1);
            quickCartBtn.setScaleY(1);
        }
    }

    @FXML
    private void onQuickCartClick() {
        Navigator.goTo(Navigator.Screen.CART);
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    public void setOnline(boolean online) {
        if (headerController != null)
            headerController.setOnline(online);

        if (composeKumpirController != null)
            composeKumpirController.setOnline(online);

        if (toastOverlay != null) {
            if (lastOnlineState == null) {
                lastOnlineState = online;
                return;
            }
            if (!lastOnlineState && online) {
                toastOverlay.show("Connessione internet ripristinata");
            } else if (lastOnlineState && !online) {
                toastOverlay.show("Connessione internet persa: modalità offline");
            }
            lastOnlineState = online;
        }
    }

    private void showInfo(String msg) {
        if (productsPane == null)
            return;
        productsPane.getChildren().clear();
        Label l = new Label(msg);
        l.getStyleClass().add("info-label");
        l.setWrapText(true);
        productsPane.getChildren().add(l);
    }

    @Override
    public void onReturn() {
        if (headerController != null)
            headerController.setCartCount(CartManager.get().totalItems());
        NetworkWatchdog.setListener(this::setOnline);
    }

    public void destroy() {
        if (composeKumpirController != null)
            composeKumpirController.destroy();
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown())
            backgroundExecutor.shutdownNow();
        if (quickCartPulse != null)
            quickCartPulse.stop();
    }
}