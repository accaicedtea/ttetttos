package com.app.controllers;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.*;
import java.util.stream.Collectors;

import com.api.repository.DataRepository;
import com.app.components.ChipFactory;
import com.app.components.ProductCard;
import com.app.model.CartItem;
import com.app.model.CartManager;
import com.app.model.I18n;
import com.app.pojo.Category;
import com.app.pojo.MenuData;
import com.app.pojo.Product;
import com.util.Animations;
import com.util.Navigator;
import com.util.NetworkWatchdog;
import com.util.RemoteLogger;

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                   SHOPAGE CONTROLLER — MENU E PRODOTTI                     ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 *
 * Responsabilità:
 * • Caricamento menu e categorie da DataRepository
 * • Visualizzazione prodotti con filtri per categoria
 * • Gestione modal per dettagli prodotto
 * • Integrazione header sistema (ShopHeaderController iniettato)
 * • Gestione carrello con toast feedback
 * • Sincronizzazione stato online/offline
 *
 * Pattern:
 * • Extends BaseController per accesso a runAsync(), showToast()
 * • Implements Navigator.DataReceiver per ricevere dati da altri controller
 * • Implements Navigator.ScreenReturnable per callback onReturn()
 * • ShopHeaderController iniettato da FXML automaticamente
 * • ComposeKumpirController creato e gestito a runtime
 */
public class ShopPageController extends BaseController
        implements Navigator.DataReceiver, Navigator.ScreenReturnable {

    // Inactivity monitoring
    private EventHandler<MouseEvent> inactivityResetMouseHandler;
    private EventHandler<KeyEvent> inactivityResetKeyHandler;

    // ═══════════════════════════════════════════════════════════════════════════
    // FXML COMPONENTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    @FXML private StackPane rootStack;
    @FXML private ScrollPane productsScroll;
    @FXML private FlowPane productsPane;
    @FXML private ScrollPane categoriesScroll;
    @FXML private VBox categoriesPane;
    @FXML private Button composeKumpirBtn;
    @FXML private StackPane modalOverlay;
    @FXML private VBox modalCard;
    @FXML private Label modalTitle, modalPrice, modalDesc;
    @FXML private VBox modalAllergenSection, modalIngredientSection;
    @FXML private FlowPane modalAllergenChips, modalIngredients;
    @FXML private Button modalClose, addToCartBtn, quickCartBtn;
    @FXML private ShopHeaderController headerController;

    // ═══════════════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    private Product currentProduct;
    private Label activeCategory;
    private ComposeKumpirController composeKumpirController;
    private final Map<String, List<ProductCard>> categoryProductCards = new HashMap<>();
    private String activeCategoryName;
    private Timeline quickCartPulse;

    // ═══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════

    @FXML
    private void initialize() {
        try {
            // Setup header se disponibile
            if (headerController != null) {
                headerController.setOnline(NetworkWatchdog.isOnline());
                headerController.setCartCount(CartManager.get().totalItems());
                headerController.setMenuTitle("Il Nostro Menu");
            }

            // Setup scroll inertia
            if (productsScroll != null)
                makeInertiaScrollable(productsScroll);
            if (categoriesScroll != null)
                makeInertiaScrollable(categoriesScroll);

            // Setup quick cart
            initQuickCartButton();

            // Setup componi kumpir button
            if (composeKumpirBtn != null) {
                composeKumpirController = new ComposeKumpirController(rootStack, headerController);
                composeKumpirController.init(composeKumpirBtn);
            }

            // FIX: modalOverlay deve occupare tutto lo spazio di rootStack per bloccare i click
            if (rootStack != null && modalOverlay != null) {
                modalOverlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                modalOverlay.prefWidthProperty().bind(rootStack.widthProperty());
                modalOverlay.prefHeightProperty().bind(rootStack.heightProperty());
            }

            // Setup product card resizing
            setupProductCardResizing();

            // Load menu
            showInfo(I18n.t("loading_menu"));
            loadMenu();

            // Setup inactivity monitoring
            setupInactivityMonitoring();

        } catch (Exception e) {
            RemoteLogger.error("ShopPageController", "initialize", e);
            showInfo("Errore inizializzazione: " + e.getMessage());
        }
    }

    private void setupInactivityMonitoring() {
        if (rootStack == null) return;

        // Handler per mouse e tastiera che resetta il timer
        inactivityResetMouseHandler = e -> com.util.InactivityManager.resetTimer("ShopPage MouseEvent");
        inactivityResetKeyHandler = e -> com.util.InactivityManager.resetTimer("ShopPage KeyEvent");

        // Aggiungi event filter al rootStack per catturare tutte le interazioni
        rootStack.addEventFilter(MouseEvent.ANY, inactivityResetMouseHandler);
        rootStack.addEventFilter(KeyEvent.ANY, inactivityResetKeyHandler);

        // Avvia il monitoraggio con rootStack per il dialogo di conferma
        com.util.InactivityManager.startMonitoring(Navigator.Screen.MENU, rootStack);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MENU LOADING & UI BUILD
    // ═══════════════════════════════════════════════════════════════════════════

    private void loadMenu() {
        runAsync(() -> {
            try {
                MenuData menu = DataRepository.getMenu();
                List<com.app.pojo.Promotion> promos = null;
                try {
                    promos = DataRepository.getPromotions();
                } catch (Exception e) {
                    RemoteLogger.error("ShopPage", "loadPromos", e);
                }
                
                final List<com.app.pojo.Promotion> finalPromos = promos;
                runOnUI(() -> {
                    if (menu == null || menu.isEmpty()) {
                        showInfo("Menu vuoto.");
                        return;
                    }
                    buildUI(menu, finalPromos);
                });
            } catch (Exception e) {
                RemoteLogger.error("ShopPage", "loadMenu", e);
                runOnUI(() -> showInfo("Errore: " + e.getMessage()));
            }
        });
    }

    @Override
    public void receiveData(Object data) {
        // Fallback if data is received from navigator
        loadMenu();
    }

    private void buildUI(MenuData menu, List<com.app.pojo.Promotion> promos) {
        categoriesPane.getChildren().clear();
        productsPane.getChildren().clear();
        categoryProductCards.clear();

        // 1. Aggiungi Categorie normali
        for (Category cat : menu.categorie) {
            Label tab = new Label(cat.nome != null ? cat.nome.toUpperCase() : "");
            tab.getStyleClass().add("category-tab");
            tab.setMaxWidth(Double.MAX_VALUE);
            Animations.touchFeedback(tab);

            List<ProductCard> cards = cat.prodotti.stream()
                    .map(ProductCard::from)
                    .collect(Collectors.toList());
            categoryProductCards.put(cat.nome, cards);

            tab.setOnMouseClicked(ev -> selectCategory(tab, cat.nome));
            categoriesPane.getChildren().add(tab);
        }

        // 2. Aggiungi Promozioni in fondo se ci sono
        if (promos != null && !promos.isEmpty()) {
            Label tab = new Label("PROMOZIONI");
            tab.getStyleClass().add("category-tab");
            tab.getStyleClass().add("promo-tab"); // Classe spec per UI distinct
            tab.setMaxWidth(Double.MAX_VALUE);
            Animations.touchFeedback(tab);

            List<ProductCard> promoCards = promos.stream()
                    .map(p -> {
                        ProductCard card = ProductCard.from(com.app.pojo.Product.fromPromotion(p));
                        card.getStyleClass().add("prod-card-promo");
                        return card;
                    })
                    .collect(Collectors.toList());
            categoryProductCards.put("Promozioni", promoCards);

            tab.setOnMouseClicked(ev -> selectCategory(tab, "Promozioni"));
            categoriesPane.getChildren().add(tab);
        }

        // 3. Seleziona il primo tab utile (ora prima la categoria normale)
        if (!categoriesPane.getChildren().isEmpty()) {
            String firstName = (!menu.categorie.isEmpty()) ? menu.categorie.get(0).nome : "Promozioni";
            selectCategory((Label) categoriesPane.getChildren().get(0), firstName);
        }
    }

    private void selectCategory(Label tab, String catName) {
        if (activeCategory != null)
            activeCategory.getStyleClass().remove("category-tab-active");
        activeCategory = tab;
        activeCategoryName = catName;
        tab.getStyleClass().add("category-tab-active");
        if (headerController != null)
            headerController.setCategory(catName);
        
        List<ProductCard> cards = categoryProductCards.get(catName);
        showProducts(cards);
    }

    private void showProducts(List<ProductCard> cards) {
        if (cards == null || cards.isEmpty()) {
            productsPane.getChildren().clear();
            showInfo("Nessun prodotto.");
            return;
        }

        productsPane.setOpacity(0);
        productsPane.getChildren().clear();

        for (int i = 0; i < cards.size(); i++) {
            ProductCard card = cards.get(i);
            Product p = card.getProduct();
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

    private void setupProductCardResizing() {
        if (productsScroll == null || productsPane == null)
            return;
        productsScroll.viewportBoundsProperty()
                .addListener((obs, o, n) -> resizeCards(n.getWidth()));
        productsScroll.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.widthProperty().addListener((o2, ow, nw) ->
                        Platform.runLater(() -> resizeCards(productsScroll.getViewportBounds().getWidth())));
                Platform.runLater(() -> resizeCards(productsScroll.getViewportBounds().getWidth()));
            }
        });
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

    // ═══════════════════════════════════════════════════════════════════════════
    // MODAL
    // ═══════════════════════════════════════════════════════════════════════════

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
        // FIX: Rendi modalOverlay managed=true quando visible per bloccare i click
        if (modalOverlay != null) {
            modalOverlay.setManaged(true);
            modalOverlay.setVisible(true);
            modalOverlay.toFront(); // Assicura che sia davanti
            
            // Applica blur effect al contenuto dietro (Layer 1 content)
            if (!rootStack.getChildren().isEmpty()) {
                rootStack.getChildren().get(0).setEffect(new javafx.scene.effect.GaussianBlur(8));
            }
        }
        if (modalClose != null)
            modalClose.requestFocus();
    }

    @FXML
    private void hideModal() {
        if (modalOverlay != null) {
            modalOverlay.setVisible(false);
            modalOverlay.setManaged(false); // FIX: Rilascia lo spazio quando nascosto
            
            // Rimuovi blur effect dal contenuto (Layer 1 content)
            if (!rootStack.getChildren().isEmpty()) {
                rootStack.getChildren().get(0).setEffect(null);
            }
        }
    }

    @FXML
    private void onModalOverlayClicked(MouseEvent e) {
        if (e.getTarget() == modalOverlay)
            hideModal();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CART & TOAST
    // ═══════════════════════════════════════════════════════════════════════════

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
        showToast(I18n.t("added"));
    }

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

    @FXML
    private void onCartClick() {
        Navigator.goTo(Navigator.Screen.CART);
    }



    // ═══════════════════════════════════════════════════════════════════════════
    // NETWORK & LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════

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
        
        // Avvia il monitoraggio dell'inattività quando si ritorna a questo schermo
        com.util.InactivityManager.startMonitoring(Navigator.Screen.MENU, rootStack);
    }

    public void destroy() {
        if (composeKumpirController != null)
            composeKumpirController.destroy();
        if (quickCartPulse != null)
            quickCartPulse.stop();
    }
}
