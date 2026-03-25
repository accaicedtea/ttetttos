package com.app.controllers;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import com.app.components.ModalDialog;
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
 * ShopPageController
 *
 * Ottimizzazioni per il modal kumpir:
 *
 * 1) PRE-BUILD AL BOOT: i nodi ingrediente vengono costruiti una volta sola
 * all'avvio (background thread), non quando l'utente apre il modal.
 * Aprire il modal è quindi istantaneo.
 *
 * 2) CACHE SU FILE: la risposta dell'API viene salvata in
 * {user.home}/.kumpirapp/ingredients_cache.json
 * Al prossimo avvio i nodi vengono costruiti immediatamente dalla cache,
 * senza aspettare la rete.
 *
 * 3) DELTA UPDATE: il background sync confronta la nuova risposta con la
 * cache precedente e aggiorna SOLO i nodi degli ingredienti che sono
 * cambiati (disponibile, nome, allergeni), senza ricreare nulla.
 *
 * 4) DISABILITATO SE NON DISPONIBILE: se disponibile==false il nodo viene
 * mostrato ma con setDisable(true) e la classe CSS "ingredient-unavailable".
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
    private Timeline kumpirBtnPulse;
    private StackPane kumpirOverlay = null;

    // ── Cache ingredienti ─────────────────────────────────────────────
    /** Percorso file cache su disco */
    private static final Path CACHE_FILE = Path.of(
            System.getProperty("user.home"), ".kumpirapp", "ingredients_cache.json");

    /** Record che raccoglie tutto ciò che serve per un nodo ingrediente */
    private record IngNode(
            Ingredient ing, // dati correnti
            ToggleButton btn, // nodo JavaFX (pre-costruito)
            Label nameLbl, // label nome (aggiornabile)
            Label priceLbl, // label prezzo (stabile)
            Label allergenBadge // badge allergeni (nullable, aggiornabile)
    ) {
    }

    /** Lista ordinata dei nodi — costruita una volta sola */
    private final List<IngNode> ingNodes = new ArrayList<>();

    /** Map id → IngNode per delta update O(1) */
    private final Map<Integer, IngNode> ingNodeById = new HashMap<>();

    /** Map id → selezione corrente nel modal */
    private final Map<Integer, Boolean> selectedInModal = new HashMap<>();

    /** Flag: i nodi sono pronti (pre-built) */
    private volatile boolean nodesReady = false;

    /** Label totale e conteggio — create una volta, riusate */
    private Label kumpirTotalLbl;
    private Label kumpirCountLbl;

    /** FlowPane griglia ingredienti — pre-costruita */
    private FlowPane kumpirGrid;

    /** Scheduled sync background (ogni 60 s) */
    private ScheduledExecutorService bgSync;

    private static final double KUMPIR_BASE_PRICE = 5.50;
    private static final double KUMPIR_INGREDIENT_PRICE = 0.70;
    private static final Gson GSON = new Gson();

    // ─────────────────────────────────────────────────────────────────

    @FXML
    private void initialize() {
        this.rootStack = rootStack;

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
        initComposeKumpirButton();

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

        // ── Pre-costruisce i nodi ingrediente in background al boot ───
        prebuildIngredientNodes();
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRE-BUILD — costruisce i nodi una volta sola al boot
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Avvia il pre-build in background:
     * 1) Legge la cache su disco (se esiste) → build immediato
     * 2) Chiama l'API → confronta con cache → delta update
     * 3) Salva la cache aggiornata su disco
     * 4) Avvia sync periodico ogni 60 s
     */
    private void prebuildIngredientNodes() {
        Thread t = new Thread(() -> {
            // Fase 1: build dalla cache disco (istantaneo, nessuna rete)
            List<Ingredient> cached = loadFromDiskCache();
            if (!cached.isEmpty()) {
                buildNodesFromList(cached);
                Platform.runLater(this::enableKumpirButton);
            }

            // Fase 2: fetch rete → delta update
            try {
                JsonArray fresh = IngredientService.getIngredients(true);
                List<Ingredient> freshList = Ingredient.listFromJsonArray(fresh);
                saveToDiskCache(freshList);

                if (ingNodes.isEmpty()) {
                    // Prima volta senza cache
                    buildNodesFromList(freshList);
                    Platform.runLater(this::enableKumpirButton);
                } else {
                    // Delta update: aggiorna solo i nodi cambiati
                    applyDelta(freshList);
                }
            } catch (Exception e) {
                RemoteLogger.error("ShopPage", "prebuild ingredients", e);
                // Se fallisce la rete ma avevamo la cache → ok, bottone già abilitato
            }

            // Fase 3: sync periodico ogni 60 s
            startPeriodicSync();

        }, "prebuild-ingredients");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Costruisce tutti i nodi JavaFX da una lista di Ingredient — thread-safe, poi
     * post su FX.
     */
    private void buildNodesFromList(List<Ingredient> list) {
        // Costruisce i nodi sul thread corrente (background), poi li aggiunge alla
        // struttura
        List<IngNode> newNodes = new ArrayList<>();
        for (Ingredient ing : list) {
            IngNode node = createIngNode(ing);
            newNodes.add(node);
        }
        // Tutti i nodi JavaFX devono essere aggiunti/modificati sull'FX thread
        Platform.runLater(() -> {
            ingNodes.clear();
            ingNodeById.clear();
            ingNodes.addAll(newNodes);
            ingNodes.forEach(n -> ingNodeById.put(n.ing().id, n));

            // Ricostruisce il FlowPane griglia (una sola volta)
            rebuildGrid();
            nodesReady = true;
        });
    }

    /**
     * Delta update: confronta la nuova lista con i nodi già costruiti.
     * Aggiorna solo i nodi effettivamente cambiati, aggiunge i nuovi,
     * non tocca quelli invariati.
     */
    private void applyDelta(List<Ingredient> freshList) {
        // Map id → Ingredient fresco
        Map<Integer, Ingredient> freshMap = new HashMap<>();
        freshList.forEach(i -> freshMap.put(i.id, i));

        // Calcola differenze sul thread background (nessun accesso ai nodi FX)
        List<Ingredient> toAdd = new ArrayList<>();
        List<Ingredient> toUpdate = new ArrayList<>();
        List<Integer> toRemove = new ArrayList<>();

        for (Ingredient fresh : freshList) {
            IngNode existing = ingNodeById.get(fresh.id);
            if (existing == null) {
                toAdd.add(fresh);
            } else if (ingredientChanged(existing.ing(), fresh)) {
                toUpdate.add(fresh);
            }
        }
        for (IngNode node : ingNodes) {
            if (!freshMap.containsKey(node.ing().id))
                toRemove.add(node.ing().id);
        }

        if (toAdd.isEmpty() && toUpdate.isEmpty() && toRemove.isEmpty())
            return; // niente da fare

        // Costruisce nodi nuovi in background (prima di passare all'FX thread)
        List<IngNode> addedNodes = toAdd.stream()
                .map(this::createIngNode)
                .collect(Collectors.toList());

        Platform.runLater(() -> {
            // Rimuovi nodi obsoleti
            toRemove.forEach(id -> {
                IngNode n = ingNodeById.remove(id);
                if (n != null) {
                    ingNodes.remove(n);
                    kumpirGrid.getChildren().remove(n.btn());
                }
            });

            // Aggiorna nodi cambiati (in-place — no rimozione/aggiunta)
            for (Ingredient fresh : toUpdate) {
                IngNode node = ingNodeById.get(fresh.id);
                if (node == null)
                    continue;
                patchIngNode(node, fresh);
                // Sostituisce l'Ingredient nel record aggiornando la lista
                int idx = ingNodes.indexOf(node);
                IngNode updated = new IngNode(fresh, node.btn(), node.nameLbl(), node.priceLbl(), node.allergenBadge());
                ingNodes.set(idx, updated);
                ingNodeById.put(fresh.id, updated);
            }

            // Aggiungi nuovi nodi alla fine
            for (IngNode n : addedNodes) {
                ingNodes.add(n);
                ingNodeById.put(n.ing().id, n);
                kumpirGrid.getChildren().add(n.btn());
            }
        });
    }

    /** Controlla se un Ingredient è cambiato rispetto al nodo pre-costruito */
    private boolean ingredientChanged(Ingredient old, Ingredient fresh) {
        return old.disponibile != fresh.disponibile
                || !old.nome.equals(fresh.nome)
                || !old.allergeni.equals(fresh.allergeni);
    }

    /**
     * Crea un IngNode (ToggleButton + label interne) per un Ingredient.
     * Può essere chiamato dal thread background — non aggiunge il nodo al DOM.
     */
    private IngNode createIngNode(Ingredient ing) {
        ToggleButton tb = new ToggleButton();
        tb.getStyleClass().add("kumpir-ingredient-card");
        tb.setFocusTraversable(false);
        tb.setDisable(!ing.disponibile);
        if (!ing.disponibile)
            tb.getStyleClass().add("ingredient-unavailable");

        VBox inner = new VBox(6);
        inner.setAlignment(Pos.CENTER);
        inner.setPrefWidth(148);
        inner.setPrefHeight(90);

        Label nameLbl = new Label(ing.nome);
        nameLbl.getStyleClass().add("kumpir-ingredient-name");
        nameLbl.setWrapText(true);
        nameLbl.setTextAlignment(TextAlignment.CENTER);
        nameLbl.setMaxWidth(136);

        Label priceLbl = new Label(ing.disponibile
                ? ("+ " + formatPrice(KUMPIR_INGREDIENT_PRICE))
                : "Non disponibile");
        priceLbl.getStyleClass().add(ing.disponibile
                ? "kumpir-ingredient-price"
                : "kumpir-ingredient-unavailable-label");

        Label allergenBadge = null;
        if (!ing.allergeni.isEmpty()) {
            allergenBadge = new Label("⚠ " + String.join(", ", ing.allergeni));
            allergenBadge.getStyleClass().add("kumpir-allergen-badge");
            allergenBadge.setWrapText(true);
            allergenBadge.setTextAlignment(TextAlignment.CENTER);
            allergenBadge.setMaxWidth(136);
            inner.getChildren().addAll(nameLbl, priceLbl, allergenBadge);
        } else {
            inner.getChildren().addAll(nameLbl, priceLbl);
        }

        tb.setGraphic(inner);

        // Il listener aggiorna totale/count — opera sui dati, non sul DOM
        tb.selectedProperty().addListener((obs, wasOn, isOn) -> {
            if (tb.isDisabled())
                return; // non disponibile → ignora
            selectedInModal.put(ing.id, isOn);
            updateKumpirTotals();
        });

        return new IngNode(ing, tb, nameLbl, priceLbl, allergenBadge);
    }

    /**
     * Aggiorna in-place le label di un nodo già costruito (delta update).
     * Non ricrea nessun nodo — zero alloc nel DOM.
     */
    private void patchIngNode(IngNode node, Ingredient fresh) {
        node.nameLbl().setText(fresh.nome);
        node.btn().setDisable(!fresh.disponibile);

        if (!fresh.disponibile) {
            node.btn().getStyleClass().add("ingredient-unavailable");
            node.priceLbl().setText("Non disponibile");
            node.priceLbl().getStyleClass().remove("kumpir-ingredient-price");
            if (!node.priceLbl().getStyleClass().contains("kumpir-ingredient-unavailable-label"))
                node.priceLbl().getStyleClass().add("kumpir-ingredient-unavailable-label");
        } else {
            node.btn().getStyleClass().remove("ingredient-unavailable");
            node.priceLbl().setText("+ " + formatPrice(KUMPIR_INGREDIENT_PRICE));
            node.priceLbl().getStyleClass().remove("kumpir-ingredient-unavailable-label");
            if (!node.priceLbl().getStyleClass().contains("kumpir-ingredient-price"))
                node.priceLbl().getStyleClass().add("kumpir-ingredient-price");
        }

        if (node.allergenBadge() != null) {
            node.allergenBadge().setText("⚠ " + String.join(", ", fresh.allergeni));
        }
    }

    /**
     * Ricostruisce il FlowPane griglia dai nodi esistenti (una sola volta
     * all'init).
     */
    private void rebuildGrid() {
        if (kumpirGrid == null) {
            kumpirGrid = new FlowPane(14, 14);
            kumpirGrid.getStyleClass().add("kumpir-ingredient-grid");
            kumpirGrid.setPadding(new Insets(16));
        } else {
            kumpirGrid.getChildren().clear();
        }
        ingNodes.forEach(n -> kumpirGrid.getChildren().add(n.btn()));
    }

    // ═══════════════════════════════════════════════════════════════════
    // SYNC PERIODICO
    // ═══════════════════════════════════════════════════════════════════

    private void startPeriodicSync() {
        bgSync = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ingredients-sync");
            t.setDaemon(true);
            return t;
        });
        bgSync.scheduleWithFixedDelay(() -> {
            try {
                JsonArray fresh = IngredientService.getIngredients(true);
                List<Ingredient> freshList = Ingredient.listFromJsonArray(fresh);
                saveToDiskCache(freshList);
                applyDelta(freshList);
            } catch (Exception e) {
                RemoteLogger.error("ShopPage", "periodic sync ingredients", e);
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CACHE SU DISCO
    // ═══════════════════════════════════════════════════════════════════

    /** Salva la lista ingredienti in JSON su disco. */
    private void saveToDiskCache(List<Ingredient> list) {
        try {
            Files.createDirectories(CACHE_FILE.getParent());
            // Serializza come array JSON minimale
            var arr = new com.google.gson.JsonArray();
            for (Ingredient ing : list) {
                var obj = new JsonObject();
                obj.addProperty("id", ing.id);
                obj.addProperty("nome", ing.nome);
                obj.addProperty("disponibile", ing.disponibile ? 1 : 0);
                var allergens = new com.google.gson.JsonArray();
                ing.allergeni.forEach(allergens::add);
                obj.add("allergeni", allergens);
                arr.add(obj);
            }
            Files.writeString(CACHE_FILE, GSON.toJson(arr), StandardCharsets.UTF_8);
        } catch (IOException e) {
            RemoteLogger.error("ShopPage", "saveToDiskCache", e);
        }
    }

    /** Legge la cache dal disco, ritorna lista vuota in caso di errore/assenza. */
    private List<Ingredient> loadFromDiskCache() {
        try {
            if (!Files.exists(CACHE_FILE))
                return List.of();
            String json = Files.readString(CACHE_FILE, StandardCharsets.UTF_8);
            JsonArray arr = GSON.fromJson(json, JsonArray.class);
            return Ingredient.listFromJsonArray(arr);
        } catch (Exception e) {
            RemoteLogger.error("ShopPage", "loadFromDiskCache", e);
            return List.of();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MODAL — istantaneo perché i nodi sono già pronti
    // ═══════════════════════════════════════════════════════════════════

    private void openComposeKumpir() {
        if (!nodesReady) {
            // Caso raro: pre-build ancora in corso (primo avvio senza cache + rete lenta)
            if (toastOverlay != null)
                toastOverlay.show("Preparazione ingredienti…");
            return;
        }
        showKumpirModal();
    }

    /**
     * Mostra il modal kumpir — istantaneo perché i nodi sono già costruiti.
     * Resetta le selezioni, non tocca il DOM degli ingredienti.
     */
    private void showKumpirModal() {
        if (rootStack == null || kumpirGrid == null)
            return;
        if (kumpirOverlay != null)
            rootStack.getChildren().remove(kumpirOverlay);

        // Reset selezioni del modal precedente
        selectedInModal.clear();
        ingNodes.forEach(n -> n.btn().setSelected(false));

        // Istanzia label totale/count (o riusa quelle esistenti)
        if (kumpirTotalLbl == null) {
            kumpirTotalLbl = new Label(formatPrice(KUMPIR_BASE_PRICE));
            kumpirTotalLbl.getStyleClass().add("kumpir-total-label");
        } else {
            kumpirTotalLbl.setText(formatPrice(KUMPIR_BASE_PRICE));
        }
        if (kumpirCountLbl == null) {
            kumpirCountLbl = new Label("Nessun ingrediente selezionato");
            kumpirCountLbl.getStyleClass().add("kumpir-count-label");
        } else {
            kumpirCountLbl.setText("Nessun ingrediente selezionato");
        }

        // ── Overlay scuro ──────────────────────────────────────────────
        kumpirOverlay = new StackPane();
        kumpirOverlay.setAlignment(Pos.CENTER);
        kumpirOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.68);");

        // ── Card principale ────────────────────────────────────────────
        VBox card = new VBox(0);
        card.getStyleClass().add("kumpir-modal-card");
        double cardW = Math.min(920, rootStack.getWidth() * 0.92);
        double cardH = Math.min(840, rootStack.getHeight() * 0.90);
        card.setMaxWidth(cardW);
        card.setPrefWidth(cardW);
        card.setMaxHeight(cardH);
        card.setPrefHeight(cardH);

        // Header
        HBox modalHeader = new HBox(16);
        modalHeader.setAlignment(Pos.CENTER_LEFT);
        modalHeader.getStyleClass().add("kumpir-modal-header");
        VBox titleBlock = new VBox(4);
        HBox.setHgrow(titleBlock, Priority.ALWAYS);
        Label titleLbl = new Label("Componi il tuo Kumpir");
        titleLbl.getStyleClass().add("kumpir-modal-title");
        Label subtitleLbl = new Label(String.format("Base patata: € %.2f  •  Ogni ingrediente: € %.2f",
                KUMPIR_BASE_PRICE, KUMPIR_INGREDIENT_PRICE).replace('.', ','));
        subtitleLbl.getStyleClass().add("kumpir-modal-subtitle");
        titleBlock.getChildren().addAll(titleLbl, subtitleLbl);
        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("modal-close");
        closeBtn.setFocusTraversable(false);
        modalHeader.getChildren().addAll(titleBlock, closeBtn);

        // Filtri allergeni (senza input di ricerca testo)
        ToggleButton noAllergens = filterChip("Senza allergeni");
        ToggleButton noGluten = filterChip("Senza glutine");
        ToggleButton noLactose = filterChip("Senza lattosio");
        HBox filters = new HBox(10, noAllergens, noGluten, noLactose);
        filters.setAlignment(Pos.CENTER_LEFT);
        VBox filterBar = new VBox(10, filters);
        filterBar.getStyleClass().add("kumpir-filter-bar");
        filterBar.setPadding(new Insets(14, 20, 12, 20));

        // Filtro show/hide — O(1) per nodo, nessuna ricreazione
        Runnable applyFilter = () -> {
            for (IngNode ic : ingNodes) {
                Ingredient ing = ic.ing();
                boolean show = true;
                if (noAllergens.isSelected() && !ing.allergeni.isEmpty())
                    show = false;
                if (noGluten.isSelected() && ing.allergeni.stream()
                        .anyMatch(a -> a.equalsIgnoreCase("glutine")))
                    show = false;
                if (noLactose.isSelected() && ing.allergeni.stream()
                        .anyMatch(a -> a.equalsIgnoreCase("latte e derivati")))
                    show = false;
                ic.btn().setManaged(show);
                ic.btn().setVisible(show);
            }
        };
        noAllergens.selectedProperty().addListener((obs, o, n) -> applyFilter.run());
        noGluten.selectedProperty().addListener((obs, o, n) -> applyFilter.run());
        noLactose.selectedProperty().addListener((obs, o, n) -> applyFilter.run());
        // Tutti visibili all'apertura
        ingNodes.forEach(n -> {
            n.btn().setManaged(true);
            n.btn().setVisible(true);
        });

        // Scroll griglia (usa kumpirGrid pre-costruita)
        ScrollPane gridScroll = new ScrollPane(kumpirGrid);
        gridScroll.setFitToWidth(true);
        gridScroll.setFocusTraversable(false);
        gridScroll.getStyleClass().add("kumpir-grid-scroll");
        VBox.setVgrow(gridScroll, Priority.ALWAYS);
        Animations.inertiaScroll(gridScroll);

        // Barra totale
        Button addBtn = new Button("🛒  Aggiungi al carrello");
        addBtn.getStyleClass().add("kumpir-add-btn");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox totalBar = new HBox(16, kumpirCountLbl, spacer, kumpirTotalLbl, addBtn);
        totalBar.setAlignment(Pos.CENTER);
        totalBar.getStyleClass().add("kumpir-total-bar");
        totalBar.setPadding(new Insets(16, 24, 16, 24));

        // Chiudi
        Runnable closeOverlay = () -> {
            Timeline out = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(card.opacityProperty(), 1.0),
                            new KeyValue(card.scaleXProperty(), 1.0),
                            new KeyValue(card.scaleYProperty(), 1.0)),
                    new KeyFrame(Duration.millis(150),
                            new KeyValue(card.opacityProperty(), 0.0, Interpolator.EASE_IN),
                            new KeyValue(card.scaleXProperty(), 0.94, Interpolator.EASE_IN),
                            new KeyValue(card.scaleYProperty(), 0.94, Interpolator.EASE_IN)));
            out.setOnFinished(ev -> {
                rootStack.getChildren().remove(kumpirOverlay);
                kumpirOverlay = null;
            });
            out.play();
        };

        closeBtn.setOnAction(e -> closeOverlay.run());
        kumpirOverlay.setOnMouseClicked(e -> {
            if (e.getTarget() == kumpirOverlay)
                closeOverlay.run();
        });

        // Aggiungi al carrello
        addBtn.setOnAction(e -> {
            List<String> selectedNames = ingNodes.stream()
                    .filter(n -> Boolean.TRUE.equals(selectedInModal.get(n.ing().id)))
                    .map(n -> n.ing().nome)
                    .collect(Collectors.toList());

            if (selectedNames.isEmpty()) {
                shakeNode(addBtn);
                if (toastOverlay != null)
                    toastOverlay.show("Seleziona almeno un ingrediente! 🥔");
                return;
            }

            long count = selectedNames.size();
            double finalTotal = KUMPIR_BASE_PRICE + count * KUMPIR_INGREDIENT_PRICE;
            CartItem item = CartItem.builder(0, "Kumpir personalizzato",
                    formatPrice(finalTotal), finalTotal)
                    .category("kumpir")
                    .allergens(new ArrayList<>())
                    .build();
            CartManager.get().addItem(item);
            closeOverlay.run();
            if (headerController != null) {
                headerController.setCartCount(CartManager.get().totalItems());
                headerController.bounceCart();
            }
            updateQuickCartButton();
            if (toastOverlay != null)
                toastOverlay.show("Kumpir aggiunto al carrello! 🥔");
        });

        card.getChildren().addAll(modalHeader, filterBar, gridScroll, totalBar);
        kumpirOverlay.getChildren().add(card);
        card.setOpacity(0);
        card.setScaleX(0.92);
        card.setScaleY(0.92);
        rootStack.getChildren().add(kumpirOverlay);

        new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(card.opacityProperty(), 0.0),
                        new KeyValue(card.scaleXProperty(), 0.92),
                        new KeyValue(card.scaleYProperty(), 0.92)),
                new KeyFrame(Duration.millis(200),
                        new KeyValue(card.opacityProperty(), 1.0, Interpolator.EASE_OUT),
                        new KeyValue(card.scaleXProperty(), 1.00, Interpolator.EASE_OUT),
                        new KeyValue(card.scaleYProperty(), 1.00, Interpolator.EASE_OUT)))
                .play();
    }

    /** Aggiorna label totale e count — chiamato dai listener dei ToggleButton */
    private void updateKumpirTotals() {
        if (kumpirTotalLbl == null || kumpirCountLbl == null)
            return;
        long count = selectedInModal.values().stream().filter(v -> v).count();
        double total = KUMPIR_BASE_PRICE + count * KUMPIR_INGREDIENT_PRICE;
        kumpirTotalLbl.setText(formatPrice(total));
        kumpirCountLbl.setText(count == 0
                ? "Nessun ingrediente selezionato"
                : count + " ingredient" + (count == 1 ? "e" : "i")
                        + " selezionat" + (count == 1 ? "o" : "i"));
        ScaleTransition st = new ScaleTransition(Duration.millis(110), kumpirTotalLbl);
        st.setFromX(1.15);
        st.setFromY(1.15);
        st.setToX(1.00);
        st.setToY(1.00);
        st.play();
    }

    // ═══════════════════════════════════════════════════════════════════
    // BOTTONE KUMPIR
    // ═══════════════════════════════════════════════════════════════════

    private void initComposeKumpirButton() {
        if (composeKumpirBtn == null)
            return;
        composeKumpirBtn.setText("Componi il tuo Kumpir");
        composeKumpirBtn.setDisable(true); // abilitato solo quando i nodi sono pronti
        composeKumpirBtn.setOnAction(e -> openComposeKumpir());

        kumpirBtnPulse = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(composeKumpirBtn.scaleXProperty(), 1.00),
                        new KeyValue(composeKumpirBtn.scaleYProperty(), 1.00)),
                new KeyFrame(Duration.millis(900),
                        new KeyValue(composeKumpirBtn.scaleXProperty(), 1.05),
                        new KeyValue(composeKumpirBtn.scaleYProperty(), 1.05)),
                new KeyFrame(Duration.millis(1800),
                        new KeyValue(composeKumpirBtn.scaleXProperty(), 1.00),
                        new KeyValue(composeKumpirBtn.scaleYProperty(), 1.00)));
        kumpirBtnPulse.setCycleCount(Animation.INDEFINITE);
        // Pulse parte quando il bottone viene abilitato
    }

    /** Chiamato quando i nodi sono pronti (da cache o rete). */
    private void enableKumpirButton() {
        if (composeKumpirBtn == null)
            return;
        composeKumpirBtn.setDisable(false);
        kumpirBtnPulse.play();
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
                    MenuCache.startBackgroundSync(fresh -> Platform.runLater(() -> buildUI(MenuData.from(fresh))));
                }
            });
        } else {
            Platform.runLater(this::loadMenu);
        }
    }

    public void loadMenu() {
        new Thread(() -> {
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
        }, "load-menu").start();
    }

    private void buildUI(MenuData menu) {
        categoriesPane.getChildren().clear();
        productsPane.getChildren().clear();

        for (Category cat : menu.categorie) {
            Label tab = new Label(cat.nome);
            tab.getStyleClass().add("category-tab");
            tab.setMaxWidth(Double.MAX_VALUE);
            Animations.touchFeedback(tab);
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
        tab.getStyleClass().add("category-tab-active");
        if (headerController != null)
            headerController.setCategory(cat.nome);
        showProducts(cat.prodotti);
    }

    private void showProducts(java.util.List<Product> prodotti) {
        productsPane.getChildren().clear();
        if (prodotti == null || prodotti.isEmpty()) {
            showInfo("Nessun prodotto.");
            return;
        }
        for (Product p : prodotti) {
            ProductCard card = new ProductCard(p.nome, p.prezzoFmt, p.descrizione, p.allergeni);
            card.setOnMouseClicked(ev -> showModal(p));
            productsPane.getChildren().add(card);
        }
        productsScroll.setVvalue(0);
        double w0 = productsScroll.getViewportBounds().getWidth();
        if (w0 > 0)
            resizeCards(w0);
        Platform.runLater(() -> resizeCards(productsScroll.getViewportBounds().getWidth()));
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
                new KeyFrame(Duration.millis(0), new KeyValue(toastBox.opacityProperty(), 0),
                        new KeyValue(toastBox.translateYProperty(), 20)),
                new KeyFrame(Duration.millis(250), new KeyValue(toastBox.opacityProperty(), 1),
                        new KeyValue(toastBox.translateYProperty(), 0)),
                new KeyFrame(Duration.millis(1650), new KeyValue(toastBox.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(1900), new KeyValue(toastBox.opacityProperty(), 0)));
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
                new KeyFrame(Duration.ZERO, new KeyValue(quickCartBtn.scaleXProperty(), 1.0),
                        new KeyValue(quickCartBtn.scaleYProperty(), 1.0)),
                new KeyFrame(Duration.millis(500), new KeyValue(quickCartBtn.scaleXProperty(), 1.12),
                        new KeyValue(quickCartBtn.scaleYProperty(), 1.12)),
                new KeyFrame(Duration.millis(1000), new KeyValue(quickCartBtn.scaleXProperty(), 1.0),
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

    private ToggleButton filterChip(String text) {
        ToggleButton tb = new ToggleButton(text);
        tb.getStyleClass().add("kumpir-filter-chip");
        tb.setFocusTraversable(false);
        return tb;
    }

    private String formatPrice(double price) {
        return String.format("€ %.2f", price).replace('.', ',');
    }

    private void shakeNode(Node node) {
        new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(node.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(55), new KeyValue(node.translateXProperty(), -9)),
                new KeyFrame(Duration.millis(110), new KeyValue(node.translateXProperty(), 9)),
                new KeyFrame(Duration.millis(165), new KeyValue(node.translateXProperty(), -7)),
                new KeyFrame(Duration.millis(220), new KeyValue(node.translateXProperty(), 7)),
                new KeyFrame(Duration.millis(275), new KeyValue(node.translateXProperty(), 0))).play();
    }

    public void setOnline(boolean online) {
        if (headerController != null)
            headerController.setOnline(online);
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

    // Ferma il sync quando la schermata viene distrutta (evita leak)
    public void destroy() {
        if (bgSync != null && !bgSync.isShutdown())
            bgSync.shutdownNow();
        if (kumpirBtnPulse != null)
            kumpirBtnPulse.stop();
    }
}