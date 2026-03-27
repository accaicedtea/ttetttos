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
    private Timeline kumpirBtnPulse;
    private StackPane kumpirOverlay = null;
    private Boolean lastOnlineState = null;

    // Cache rendering prodotti
    private final Map<String, List<ProductCard>> categoryProductCards = new HashMap<>();
    private String activeCategoryName = null;

    // ── Cache ingredienti ─────────────────────────────────────────────
    private static final Path CACHE_FILE = Path.of(
            System.getProperty("user.home"), ".kumpirapp", "ingredients_cache.json");

    /**
     * Record nodo ingrediente.
     * NON contiene property binding: dimensioni fisse, aggiornamenti diretti.
     */
    private record IngNode(
            Ingredient ing,
            ToggleButton btn,
            Label nameLbl,
            Label priceLbl,
            Label allergenBadge // null se nessun allergene
    ) {
    }

    private final List<IngNode> ingNodes = new ArrayList<>();
    private final Map<Integer, IngNode> ingNodeById = new HashMap<>();

    // ── Selezioni modal — O(1) invece di stream su Map ────────────────
    /**
     * Set degli id selezionati (sostituisce Map<Integer,Boolean>).
     * selectedCount aggiornato in sincronia → updateKumpirTotals() è O(1).
     */
    private final Set<Integer> selectedIds = new HashSet<>();
    private int selectedCount = 0; // invariante: selectedCount == selectedIds.size()

    private volatile boolean nodesReady = false;
    private boolean suppressSelectionEvents = false;

    // ── Nodi modal riusati ─────────────────────────────────────────────
    private Label kumpirTotalLbl;
    private Label kumpirCountLbl;
    private ToggleButton kumpirFilterNoAllergens;
    private ToggleButton kumpirFilterNoGluten;
    private ToggleButton kumpirFilterNoLactose;

    /** FlowPane griglia ingredienti — pre-costruita una sola volta */
    private FlowPane kumpirGrid;

    /** VBox card del modal kumpir — pre-costruita subito dopo enableKumpirButton */
    private VBox kumpirCard;

    /** Debounce per il filtro (30 ms) */
    private Timeline filterDebounce;

    // ── Thread pool ────────────────────────────────────────────────────
    private ExecutorService backgroundExecutor;
    private ScheduledExecutorService bgSync;

    // ── Costanti ───────────────────────────────────────────────────────
    private static final double KUMPIR_BASE_PRICE = 5.50;
    private static final String PRICE_BASE_FMT = formatPriceStatic(KUMPIR_BASE_PRICE);
    private static final String LABEL_UNAVAILABLE = "Non disponibile";
    private static final Gson GSON = new Gson();

    // Totale ingredienti selezionati (calcolo O(1) dal listener)
    private double selectedIngredientsTotal = 0.0;

    // ── Dimensioni fisse nodi ingrediente (px) ─────────────────────────
    // Evitano property binding: calcolate una sola volta, non per-nodo.
    private static final double ING_INNER_W = 188.0;
    private static final double ING_LABEL_W = 170.0;

    // ─────────────────────────────────────────────────────────────────────

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

        backgroundExecutor = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()));

        prebuildIngredientNodes();
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRE-BUILD ingredienti
    // ═══════════════════════════════════════════════════════════════════

    private void prebuildIngredientNodes() {
        backgroundExecutor.submit(() -> {
            // Fase 1 — cache disco → build immediato
            List<Ingredient> cached = loadFromDiskCache();
            if (!cached.isEmpty())
                buildNodesFromList(cached);

            // Fase 2 — rete → delta update
            try {
                JsonArray fresh = IngredientService.getIngredients();
                List<Ingredient> freshList = Ingredient.listFromJsonArray(fresh);
                saveToDiskCache(freshList);
                if (ingNodes.isEmpty())
                    buildNodesFromList(freshList);
                else
                    applyDelta(freshList);
            } catch (Exception e) {
                RemoteLogger.error("ShopPage", "prebuild ingredients", e);
            }

            startPeriodicSync();
        });
    }

    /**
     * Costruisce tutti i nodi da una lista.
     *
     * FIX CRITICO: i nodi JavaFX vengono creati SOLO dentro Platform.runLater.
     * La versione precedente li creava nel thread background → violazione JavaFX
     * che causava crash non deterministici.
     *
     * PERF: Layout batch — il FlowPane viene popolato con setManaged(false) per
     * sopprimere N layout intermedi, poi riabilitato per un unico calcolo finale.
     */
    private void buildNodesFromList(List<Ingredient> list) {
        // Prepara dati puri sul thread background (nessun nodo JavaFX)
        record IngData(Ingredient ing, boolean hasAllergens, String allergenText) {
        }
        List<IngData> prepared = list.stream()
                .map(ing -> new IngData(
                        ing,
                        !ing.allergeni.isEmpty(),
                        ing.allergeni.isEmpty() ? "" : "⚠ " + String.join(", ", ing.allergeni)))
                .collect(Collectors.toList());

        Platform.runLater(() -> {
            ingNodes.clear();
            ingNodeById.clear();
            ensureKumpirGrid();

            // Batch insert: sopprime layout durante la costruzione
            kumpirGrid.setManaged(false);
            kumpirGrid.getChildren().clear();

            for (IngData d : prepared) {
                IngNode node = createIngNodeFX(d.ing(), d.hasAllergens(), d.allergenText());
                ingNodes.add(node);
                ingNodeById.put(d.ing().id, node);
                kumpirGrid.getChildren().add(node.btn());
            }

            // Riabilita layout — un solo pass
            kumpirGrid.setManaged(true);

            nodesReady = true;
            enableKumpirButton();
        });
    }

    /**
     * Crea un IngNode interamente sull'FX thread.
     *
     * PERF: nessun property binding.
     * La versione precedente usava:
     * inner.maxWidthProperty().bind(tb.widthProperty().multiply(0.65))
     * Ogni bind() aggiunge un ChangeListener che scatta ad ogni resize del nodo.
     * Con 50+ ingredienti → 50+ listener × ogni layout pass della finestra.
     * Sostituiti con ING_INNER_W e ING_LABEL_W (costanti fisse).
     */
    private IngNode createIngNodeFX(Ingredient ing, boolean hasAllergens, String allergenText) {
        ToggleButton tb = new ToggleButton();
        tb.getStyleClass().add("kumpir-ingredient-card");
        tb.setFocusTraversable(false);
        tb.setDisable(!ing.disponibile);
        if (!ing.disponibile)
            tb.getStyleClass().add("ingredient-unavailable");
        tb.setCache(true);
        tb.setCacheHint(CacheHint.SPEED);

        // Dimensioni iniziali per card ingredienti. Poi verranno adattate da
        // syncGridGap.
        tb.setMinWidth(220);
        tb.setPrefWidth(220);
        tb.setMaxWidth(260);
        tb.setMinHeight(180);
        tb.setPrefHeight(200);
        tb.setMaxHeight(240);

        VBox inner = new VBox(8); // era 6 — più respiro verticale
        inner.setAlignment(Pos.CENTER);
        inner.setMaxWidth(ING_INNER_W);
        inner.setPrefWidth(ING_INNER_W);
        inner.setPadding(new Insets(8, 4, 8, 4)); // padding interno top/bottom

        Label nameLbl = new Label(ing.nome);
        nameLbl.getStyleClass().add("kumpir-ingredient-name");
        nameLbl.setWrapText(true);
        nameLbl.setTextAlignment(TextAlignment.CENTER);
        nameLbl.setMaxWidth(ING_LABEL_W);
        nameLbl.setMinHeight(Label.USE_PREF_SIZE); // non tronca mai il nome

        String priceText = ing.disponibile ? "+ " + formatPriceStatic(ing.prezzo) : LABEL_UNAVAILABLE;
        Label priceLbl = new Label(priceText);
        priceLbl.getStyleClass().add(ing.disponibile
                ? "kumpir-ingredient-price"
                : "kumpir-ingredient-unavailable-label");

        Label allergenBadge = null;
        if (hasAllergens) {
            allergenBadge = new Label(allergenText);
            allergenBadge.getStyleClass().add("kumpir-allergen-badge");
            allergenBadge.setWrapText(true);
            allergenBadge.setTextAlignment(TextAlignment.CENTER);
            allergenBadge.setMaxWidth(ING_LABEL_W);
            allergenBadge.setMinHeight(Label.USE_PREF_SIZE);
            inner.getChildren().addAll(nameLbl, priceLbl, allergenBadge);
        } else {
            inner.getChildren().addAll(nameLbl, priceLbl);
        }
        tb.setGraphic(inner);

        // Listener O(1): aggiorna selectedIds, selectedCount, selectedIngredientsTotal
        final int id = ing.id;
        tb.selectedProperty().addListener((obs, wasOn, isOn) -> {
            if (tb.isDisabled() || suppressSelectionEvents)
                return;
            if (isOn) {
                if (selectedIds.add(id)) {
                    selectedCount++;
                    selectedIngredientsTotal += ing.prezzo;
                }
            } else {
                if (selectedIds.remove(id)) {
                    selectedCount--;
                    selectedIngredientsTotal -= ing.prezzo;
                }
            }
            updateKumpirTotals();
        });

        return new IngNode(ing, tb, nameLbl, priceLbl, allergenBadge);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DELTA UPDATE
    // ═══════════════════════════════════════════════════════════════════

    private void applyDelta(List<Ingredient> freshList) {
        Map<Integer, Ingredient> freshMap = new HashMap<>(freshList.size() * 2);
        freshList.forEach(i -> freshMap.put(i.id, i));

        List<Ingredient> toAdd = new ArrayList<>();
        List<Ingredient> toUpdate = new ArrayList<>();
        List<Integer> toRemove = new ArrayList<>();

        for (Ingredient fresh : freshList) {
            IngNode existing = ingNodeById.get(fresh.id);
            if (existing == null)
                toAdd.add(fresh);
            else if (changed(existing.ing(), fresh))
                toUpdate.add(fresh);
        }
        for (IngNode node : ingNodes) {
            if (!freshMap.containsKey(node.ing().id))
                toRemove.add(node.ing().id);
        }

        if (toAdd.isEmpty() && toUpdate.isEmpty() && toRemove.isEmpty())
            return;

        // Prepara dati nuovi in background (nessun nodo FX)
        record IngData(Ingredient ing, boolean hasAllergens, String allergenText) {
        }
        List<IngData> addData = toAdd.stream()
                .map(ing -> new IngData(
                        ing, !ing.allergeni.isEmpty(),
                        ing.allergeni.isEmpty() ? "" : "⚠ " + String.join(", ", ing.allergeni)))
                .collect(Collectors.toList());

        Platform.runLater(() -> {
            // Rimozioni
            toRemove.forEach(id -> {
                IngNode n = ingNodeById.remove(id);
                if (n != null) {
                    ingNodes.remove(n);
                    kumpirGrid.getChildren().remove(n.btn());
                    if (selectedIds.remove(id))
                        selectedCount--;
                }
            });

            // Aggiornamenti in-place
            for (Ingredient fresh : toUpdate) {
                IngNode node = ingNodeById.get(fresh.id);
                if (node == null)
                    continue;
                patchIngNode(node, fresh);
                int idx = ingNodes.indexOf(node);
                IngNode updated = new IngNode(fresh, node.btn(), node.nameLbl(),
                        node.priceLbl(), node.allergenBadge());
                ingNodes.set(idx, updated);
                ingNodeById.put(fresh.id, updated);
            }

            // Aggiunte batch
            if (!addData.isEmpty()) {
                kumpirGrid.setManaged(false);
                for (IngData d : addData) {
                    IngNode n = createIngNodeFX(d.ing(), d.hasAllergens(), d.allergenText());
                    ingNodes.add(n);
                    ingNodeById.put(n.ing().id, n);
                    kumpirGrid.getChildren().add(n.btn());
                }
                kumpirGrid.setManaged(true);
            }
        });
    }

    private boolean changed(Ingredient old, Ingredient fresh) {
        return old.disponibile != fresh.disponibile
                || !old.nome.equals(fresh.nome)
                || !old.allergeni.equals(fresh.allergeni);
    }

    private void patchIngNode(IngNode node, Ingredient fresh) {
        node.nameLbl().setText(fresh.nome);
        node.btn().setDisable(!fresh.disponibile);
        if (!fresh.disponibile) {
            node.btn().getStyleClass().add("ingredient-unavailable");
            node.priceLbl().setText(LABEL_UNAVAILABLE);
            node.priceLbl().getStyleClass().remove("kumpir-ingredient-price");
            if (!node.priceLbl().getStyleClass().contains("kumpir-ingredient-unavailable-label"))
                node.priceLbl().getStyleClass().add("kumpir-ingredient-unavailable-label");
        } else {
            node.btn().getStyleClass().remove("ingredient-unavailable");
            node.priceLbl().setText("+ " + formatPriceStatic(fresh.prezzo));
            node.priceLbl().getStyleClass().remove("kumpir-ingredient-unavailable-label");
            if (!node.priceLbl().getStyleClass().contains("kumpir-ingredient-price"))
                node.priceLbl().getStyleClass().add("kumpir-ingredient-price");
        }
        if (node.allergenBadge() != null)
            node.allergenBadge().setText("⚠ " + String.join(", ", fresh.allergeni));
    }

    // ═══════════════════════════════════════════════════════════════════
    // GRID — creata una sola volta
    // ═══════════════════════════════════════════════════════════════════

    private void ensureKumpirGrid() {
        if (kumpirGrid != null)
            return;
        kumpirGrid = new FlowPane(16, 16); // era (10,10)
        kumpirGrid.getStyleClass().add("kumpir-ingredient-grid");
        kumpirGrid.setPadding(new Insets(16));
        kumpirGrid.setCache(true);
        kumpirGrid.setCacheHint(CacheHint.SPEED);
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
                JsonArray fresh = IngredientService.getIngredients();
                List<Ingredient> freshList = Ingredient.listFromJsonArray(fresh);
                saveToDiskCache(freshList);
                applyDelta(freshList);
            } catch (Exception e) {
                RemoteLogger.error("ShopPage", "periodic sync", e);
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CACHE DISCO
    // ═══════════════════════════════════════════════════════════════════

    private void saveToDiskCache(List<Ingredient> list) {
        try {
            Files.createDirectories(CACHE_FILE.getParent());
            var arr = new JsonArray();
            for (Ingredient ing : list) {
                var obj = new JsonObject();
                obj.addProperty("id", ing.id);
                obj.addProperty("nome", ing.nome);
                obj.addProperty("disponibile", ing.disponibile ? 1 : 0);
                var allergens = new JsonArray();
                ing.allergeni.forEach(allergens::add);
                obj.add("allergeni", allergens);
                arr.add(obj);
            }
            Files.writeString(CACHE_FILE, GSON.toJson(arr), StandardCharsets.UTF_8);
        } catch (IOException e) {
            RemoteLogger.error("ShopPage", "saveToDiskCache", e);
        }
    }

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
    // MODAL KUMPIR — pre-costruito, apertura <5 ms
    // ═══════════════════════════════════════════════════════════════════

    private void openComposeKumpir() {
        if (!nodesReady) {
            if (toastOverlay != null)
                toastOverlay.show("Preparazione ingredienti…");
            return;
        }
        showKumpirModal();
    }

    /**
     * Mostra il modal.
     *
     * OTTIMIZZAZIONE CHIAVE: kumpirOverlay e kumpirCard vengono costruiti
     * in buildKumpirModalStructure(), chiamato in enableKumpirButton() — non al
     * click.
     * Al click dell'utente rimangono solo:
     * 1) resetKumpirModalState() → O(selectedCount), non O(N)
     * 2) setVisible(true)
     * 3) Timeline 180 ms
     * Zero allocazioni, zero layout da zero.
     */
    private void showKumpirModal() {
        if (rootStack == null || kumpirOverlay == null)
            return;

        resetKumpirModalState();

        if (!rootStack.getChildren().contains(kumpirOverlay))
            rootStack.getChildren().add(kumpirOverlay);
        kumpirOverlay.setVisible(true);

        // Animazione apertura — beneficia della cache GPU impostata in
        // buildKumpirModalStructure
        new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(kumpirCard.opacityProperty(), 0.0),
                        new KeyValue(kumpirCard.scaleXProperty(), 0.92),
                        new KeyValue(kumpirCard.scaleYProperty(), 0.92)),
                new KeyFrame(Duration.millis(180),
                        new KeyValue(kumpirCard.opacityProperty(), 1.0, Interpolator.EASE_OUT),
                        new KeyValue(kumpirCard.scaleXProperty(), 1.00, Interpolator.EASE_OUT),
                        new KeyValue(kumpirCard.scaleYProperty(), 1.00, Interpolator.EASE_OUT)))
                .play();
    }

    /**
     * Costruisce l'intera struttura del modal una sola volta.
     * Chiamato da enableKumpirButton() (subito dopo che i nodi sono pronti),
     * NON al primo click dell'utente.
     */
    private void buildKumpirModalStructure() {
        if (rootStack == null || kumpirGrid == null)
            return;

        kumpirTotalLbl = new Label(PRICE_BASE_FMT);
        kumpirTotalLbl.getStyleClass().add("kumpir-total-label");

        kumpirCountLbl = new Label("Nessun ingrediente selezionato");
        kumpirCountLbl.getStyleClass().add("kumpir-count-label");

        // ── Overlay ────────────────────────────────────────────────────────
        kumpirOverlay = new StackPane();
        kumpirOverlay.setAlignment(Pos.CENTER);
        // kumpirOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.68);");
        kumpirOverlay.setVisible(false);
        // kumpirOverlay.setManaged(false);

        // Copre sempre l'intero rootStack, indipendentemente dal layout
        kumpirOverlay.prefWidthProperty().bind(rootStack.widthProperty());
        kumpirOverlay.prefHeightProperty().bind(rootStack.heightProperty());
        kumpirOverlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // ── Card ───────────────────────────────────────────────────────────
        kumpirCard = new VBox(0);
        kumpirCard.getStyleClass().add("kumpir-modal-card");
        kumpirCard.setCache(true);
        kumpirCard.setCacheHint(CacheHint.SPEED);

        // Dimensioni reattive: ricalcolate ogni volta che rootStack cambia
        ChangeListener<Number> sizeListener = (obs, o, n) -> syncCardSize();
        rootStack.widthProperty().addListener(sizeListener);
        rootStack.heightProperty().addListener(sizeListener);
        syncCardSize(); // applica subito i valori correnti

        // ── Header ────────────────────────────────────────────────────────
        HBox modalHeader = new HBox(16);
        modalHeader.setAlignment(Pos.CENTER_LEFT);
        modalHeader.getStyleClass().add("kumpir-modal-header");

        VBox titleBlock = new VBox(4);
        HBox.setHgrow(titleBlock, Priority.ALWAYS);
        Label titleLbl = new Label("Componi il tuo Kumpir");
        titleLbl.getStyleClass().add("kumpir-modal-title");
        Label subtitleLbl = new Label("Base patata: € " + String.format("%.2f", KUMPIR_BASE_PRICE).replace('.', ',')
                + "  •  Ingredienti con prezzo variabile");
        subtitleLbl.getStyleClass().add("kumpir-modal-subtitle");
        titleBlock.getChildren().addAll(titleLbl, subtitleLbl);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("modal-close");
        closeBtn.setFocusTraversable(false);
        modalHeader.getChildren().addAll(titleBlock, closeBtn);

        // ── Filtri ────────────────────────────────────────────────────────
        kumpirFilterNoAllergens = filterChip("Senza allergeni");
        kumpirFilterNoGluten = filterChip("Senza glutine");
        kumpirFilterNoLactose = filterChip("Senza lattosio");

        // FlowPane: si wrappa automaticamente su schermi stretti
        FlowPane filterFlow = new FlowPane(8, 8);
        filterFlow.setAlignment(Pos.CENTER_LEFT);
        filterFlow.getChildren().addAll(
                kumpirFilterNoAllergens, kumpirFilterNoGluten, kumpirFilterNoLactose);

        VBox filterBar = new VBox(10, filterFlow);
        filterBar.getStyleClass().add("kumpir-filter-bar");
        filterBar.setPadding(new Insets(12, 16, 12, 16));

        // Filtro con debounce 30 ms
        Runnable applyFilter = () -> {
            boolean noAll = kumpirFilterNoAllergens.isSelected();
            boolean noGluten = kumpirFilterNoGluten.isSelected();
            boolean noLac = kumpirFilterNoLactose.isSelected();
            for (IngNode ic : ingNodes) {
                Ingredient ing = ic.ing();
                boolean show = true;
                if (noAll && !ing.allergeni.isEmpty())
                    show = false;
                if (show && noGluten && ing.allergeni.stream()
                        .anyMatch(a -> a.equalsIgnoreCase("glutine")))
                    show = false;
                if (show && noLac && ing.allergeni.stream()
                        .anyMatch(a -> a.equalsIgnoreCase("latte e derivati")))
                    show = false;
                ic.btn().setManaged(show);
                ic.btn().setVisible(show);
            }
        };

        ChangeListener<Boolean> filterListener = (obs, o, n) -> {
            if (filterDebounce != null)
                filterDebounce.stop();
            filterDebounce = new Timeline(
                    new KeyFrame(Duration.millis(30), e -> applyFilter.run()));
            filterDebounce.play();
        };
        kumpirFilterNoAllergens.selectedProperty().addListener(filterListener);
        kumpirFilterNoGluten.selectedProperty().addListener(filterListener);
        kumpirFilterNoLactose.selectedProperty().addListener(filterListener);

        // ── Grid scroll ───────────────────────────────────────────────────
        ScrollPane gridScroll = new ScrollPane(kumpirGrid);
        gridScroll.setFitToWidth(true);
        gridScroll.setFocusTraversable(false);
        gridScroll.getStyleClass().add("kumpir-grid-scroll");
        VBox.setVgrow(gridScroll, Priority.ALWAYS);
        Animations.inertiaScroll(gridScroll);

        // La griglia adatta il gap al viewport corrente
        kumpirCard.widthProperty().addListener((obs, o, nw) -> syncGridGap(nw.doubleValue()));

        // ── Total bar ─────────────────────────────────────────────────────
        Button addBtn = new Button("🛒  Aggiungi al carrello");
        addBtn.getStyleClass().add("kumpir-add-btn");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox totalBar = new HBox(12, kumpirCountLbl, spacer, kumpirTotalLbl, addBtn);
        totalBar.setAlignment(Pos.CENTER);
        totalBar.getStyleClass().add("kumpir-total-bar");
        totalBar.setPadding(new Insets(12, 16, 12, 16));

        // su schermi stretti la total bar si wrappa
        kumpirCard.widthProperty().addListener((obs, o, nw) -> {
            if (nw.doubleValue() < 480) {
                kumpirCountLbl.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(kumpirCountLbl, Priority.ALWAYS);
            }
        });

        // ── Chiudi ────────────────────────────────────────────────────────
        Runnable closeOverlay = () -> {
            Timeline out = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(kumpirCard.opacityProperty(), 1.0),
                            new KeyValue(kumpirCard.scaleXProperty(), 1.0),
                            new KeyValue(kumpirCard.scaleYProperty(), 1.0)),
                    new KeyFrame(Duration.millis(130),
                            new KeyValue(kumpirCard.opacityProperty(), 0.0, Interpolator.EASE_IN),
                            new KeyValue(kumpirCard.scaleXProperty(), 0.94, Interpolator.EASE_IN),
                            new KeyValue(kumpirCard.scaleYProperty(), 0.94, Interpolator.EASE_IN)));
            out.setOnFinished(ev -> kumpirOverlay.setVisible(false));
            out.play();
        };
        closeBtn.setOnAction(e -> closeOverlay.run());
        kumpirOverlay.setOnMouseClicked(e -> {
            if (e.getTarget() == kumpirOverlay)
                closeOverlay.run();
        });

        // ── Aggiungi al carrello ──────────────────────────────────────────
        addBtn.setOnAction(e -> {
            if (selectedCount == 0) {
                shakeNode(addBtn);
                if (toastOverlay != null)
                    toastOverlay.show("Seleziona almeno un ingrediente! 🥔");
                return;
            }
            double finalTotal = KUMPIR_BASE_PRICE + selectedIngredientsTotal;
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

        kumpirCard.getChildren().addAll(modalHeader, filterBar, gridScroll, totalBar);
        kumpirOverlay.getChildren().add(kumpirCard);
        rootStack.getChildren().add(kumpirOverlay);
    }

    /**
     * Calcola e applica le dimensioni della card in base alle dimensioni correnti
     * di rootStack. Viene chiamato sia all'inizializzazione che ad ogni resize.
     *
     * Logica portrait/landscape con margini proporzionali:
     * landscape → 90% larghezza, 88% altezza, max 940×860
     * portrait → 96% larghezza, 93% altezza (quasi a schermo intero)
     */
    private void syncCardSize() {
        if (kumpirCard == null || rootStack == null)
            return;
        double sw = rootStack.getWidth();
        double sh = rootStack.getHeight();
        if (sw <= 0 || sh <= 0)
            return;

        boolean portrait = sh > sw * 1.15;

        double w, h;
        if (portrait) {
            w = Math.min(sw * 0.96, 700);
            h = Math.min(sh * 0.93, sh - 20);
        } else {
            w = Math.min(sw * 0.90, 940);
            h = Math.min(sh * 0.88, 860);
            // non scendere sotto soglie minime usabili
            w = Math.max(w, Math.min(520, sw * 0.96));
            h = Math.max(h, Math.min(480, sh * 0.93));
        }

        kumpirCard.setMaxWidth(w);
        kumpirCard.setPrefWidth(w);
        kumpirCard.setMaxHeight(h);
        kumpirCard.setPrefHeight(h);
    }

    /**
     * Adatta il gap della FlowPane ingredienti alla larghezza corrente della card.
     * Meno spazio → gap più stretto → più ingredienti per riga.
     */
    private void syncGridGap(double cardWidth) {
        if (kumpirGrid == null)
            return;
        double gap = cardWidth < 640 ? 10 : cardWidth < 840 ? 12 : 16;
        kumpirGrid.setHgap(gap);
        kumpirGrid.setVgap(gap);

        double safeWidth = Math.max(0, cardWidth - 40); // margini del card interno
        // Ogni card dovrebbe occupare ~1/3 con margini, ma non meno di 220 e non più di
        // 260.
        double targetCard = Math.floor((safeWidth - 2 * gap) / 3);
        double cardW = Math.min(260, Math.max(220, targetCard));

        kumpirGrid.getChildren().forEach(child -> {
            if (child instanceof Region r) {
                r.setMinWidth(cardW);
                r.setPrefWidth(cardW);
                r.setMaxWidth(cardW);
                r.setMinHeight(190);
                r.setPrefHeight(210);
                r.setMaxHeight(240);
            }
        });

        // Forza almeno 3 card per riga riducendo wrapLength a misura corretta.
        double wrapLen = Math.max(0, cardW * 3 + gap * 2);
        kumpirGrid.setPrefWrapLength(wrapLen);
    }

    /**
     * Reset dello stato del modal.
     *
     * PERF: deseleziona solo i nodi in selectedIds (O(selectedCount)),
     * non tutti gli N nodi (O(N)).
     * Con 60 ingredienti e 3 selezionati → 3 operazioni invece di 60.
     */
    private void resetKumpirModalState() {
        suppressSelectionEvents = true;
        for (int id : selectedIds) {
            IngNode n = ingNodeById.get(id);
            if (n != null)
                n.btn().setSelected(false);
        }
        selectedIds.clear();
        selectedCount = 0;
        selectedIngredientsTotal = 0.0;
        suppressSelectionEvents = false;

        if (filterDebounce != null) {
            filterDebounce.stop();
            filterDebounce = null;
        }
        if (kumpirFilterNoAllergens != null)
            kumpirFilterNoAllergens.setSelected(false);
        if (kumpirFilterNoGluten != null)
            kumpirFilterNoGluten.setSelected(false);
        if (kumpirFilterNoLactose != null)
            kumpirFilterNoLactose.setSelected(false);

        // ← rimossa updateKumpirModalSize(): syncCardSize() è già agganciata al
        // listener

        for (IngNode n : ingNodes) {
            n.btn().setManaged(true);
            n.btn().setVisible(true);
        }
        if (kumpirTotalLbl != null)
            kumpirTotalLbl.setText(PRICE_BASE_FMT);
        if (kumpirCountLbl != null)
            kumpirCountLbl.setText("Nessun ingrediente selezionato");
    }

    /**
     * Aggiorna label totale e count.
     *
     * PERF: O(1) — selectedCount è già mantenuto dal listener del ToggleButton.
     * PERF: ScaleTransition rimossa — animazione costosa nell'hot path (ogni
     * click).
     */
    private void updateKumpirTotals() {
        if (kumpirTotalLbl == null || kumpirCountLbl == null)
            return;
        double total = KUMPIR_BASE_PRICE + selectedIngredientsTotal;
        kumpirTotalLbl.setText(formatPrice(total));
        kumpirCountLbl.setText(selectedCount == 0
                ? "Nessun ingrediente selezionato"
                : selectedCount + " ingredient" + (selectedCount == 1 ? "e" : "i")
                        + " selezionat" + (selectedCount == 1 ? "o" : "i"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // BOTTONE KUMPIR
    // ═══════════════════════════════════════════════════════════════════

    private void initComposeKumpirButton() {
        if (composeKumpirBtn == null)
            return;
        composeKumpirBtn.setText("Componi il tuo Kumpir");
        composeKumpirBtn.setDisable(true);
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
    }

    /**
     * Abilita il bottone e pre-costruisce l'intera struttura del modal.
     * Così il PRIMO click dell'utente è già istantaneo (non solo quelli
     * successivi).
     */
    private void enableKumpirButton() {
        if (composeKumpirBtn == null)
            return;
        composeKumpirBtn.setDisable(false);
        kumpirBtnPulse.play();
        buildKumpirModalStructure(); // pre-build ora, non al click
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

    private ToggleButton filterChip(String text) {
        ToggleButton tb = new ToggleButton(text);
        tb.getStyleClass().add("kumpir-filter-chip");
        tb.setFocusTraversable(false);
        return tb;
    }

    private String formatPrice(double price) {
        return String.format("€ %.2f", price).replace('.', ',');
    }

    private static String formatPriceStatic(double price) {
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

        if (toastOverlay != null) {
            if (lastOnlineState == null) {
                // Stato iniziale; non notificare recupero per evitare messaggi a startup
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
        if (bgSync != null && !bgSync.isShutdown())
            bgSync.shutdownNow();
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown())
            backgroundExecutor.shutdownNow();
        if (kumpirBtnPulse != null)
            kumpirBtnPulse.stop();
        if (filterDebounce != null)
            filterDebounce.stop();
    }
}