package com.app.controllers;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import javafx.beans.value.ChangeListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import com.app.components.ToastOverlay;
import com.app.model.CartItem;
import com.app.model.CartManager;
import com.app.model.Ingredient;
import com.api.services.IngredientService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.util.Animations;
import com.util.RemoteLogger;

/**
 * Controllore dedicato al compositore Kumpir in modal.
 */
public class ComposeKumpirController extends BaseController {
    private static final double KUMPIR_BASE_PRICE = 5.50;
    private static final String PRICE_BASE_FMT = formatPriceStatic(KUMPIR_BASE_PRICE);
    private static final String LABEL_UNAVAILABLE = "Non disponibile";
    private static final Gson GSON = new Gson();
    private static final double ING_INNER_W = 188.0;
    private static final double ING_LABEL_W = 170.0;

    private static final Path CACHE_FILE = Path.of(
            System.getProperty("user.home"), ".kumpirapp", "ingredients_cache.json");

    private final ShopHeaderController headerController;
    private final ExecutorService backgroundExecutor;
    private ScheduledExecutorService bgSync;

    private Button composeKumpirBtn;
    private ToastOverlay toastOverlay;

    private StackPane kumpirOverlay;
    private VBox kumpirCard;
    private Label kumpirTotalLbl;
    private Label kumpirCountLbl;
    private ToggleButton kumpirFilterNoAllergens;
    private ToggleButton kumpirFilterNoGluten;
    private ToggleButton kumpirFilterNoLactose;
    private FlowPane kumpirGrid;
    private Timeline filterDebounce;
    private Timeline kumpirBtnPulse;

    private record IngNode(Ingredient ing, ToggleButton btn, Label nameLbl, Label priceLbl, Label allergenBadge) {
    }

    private final List<IngNode> ingNodes = new ArrayList<>();
    private final Map<Integer, IngNode> ingNodeById = new HashMap<>();
    private final Set<Integer> selectedIds = new HashSet<>();

    private volatile boolean nodesReady = false;
    private boolean suppressSelectionEvents = false;
    private int selectedCount = 0;
    private double selectedIngredientsTotal = 0.0;

    public ComposeKumpirController(StackPane rootStack, ShopHeaderController headerController) {
        this.rootStack = rootStack;
        this.headerController = headerController;
        this.backgroundExecutor = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()));
    }

    public void init(Button composeKumpirBtn) {
        this.composeKumpirBtn = composeKumpirBtn;
        if (rootStack != null) {
            toastOverlay = new ToastOverlay();
            rootStack.getChildren().add(toastOverlay);
        }
        initComposeKumpirButton();
        prebuildIngredientNodes();
    }

    private void prebuildIngredientNodes() {
        backgroundExecutor.submit(() -> {
            List<Ingredient> cached = loadFromDiskCache();
            if (!cached.isEmpty())
                buildNodesFromList(cached);

            try {
                JsonArray fresh = IngredientService.getIngredients();
                List<Ingredient> freshList = Ingredient.listFromJsonArray(fresh);
                saveToDiskCache(freshList);
                if (ingNodes.isEmpty())
                    buildNodesFromList(freshList);
                else
                    applyDelta(freshList);
            } catch (Exception e) {
                RemoteLogger.error("ComposeKumpir", "prebuild ingredients", e);
            }
            startPeriodicSync();
        });
    }

    private void buildNodesFromList(List<Ingredient> list) {
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

            kumpirGrid.setManaged(false);
            kumpirGrid.getChildren().clear();

            for (IngData d : prepared) {
                IngNode node = createIngNodeFX(d.ing(), d.hasAllergens(), d.allergenText());
                ingNodes.add(node);
                ingNodeById.put(d.ing().id, node);
                kumpirGrid.getChildren().add(node.btn());
            }

            kumpirGrid.setManaged(true);
            nodesReady = true;
            enableKumpirButton();
        });
    }

    private IngNode createIngNodeFX(Ingredient ing, boolean hasAllergens, String allergenText) {
        ToggleButton tb = new ToggleButton();
        tb.getStyleClass().add("kumpir-ingredient-card");
        tb.setFocusTraversable(false);
        tb.setDisable(!ing.disponibile);
        if (!ing.disponibile)
            tb.getStyleClass().add("ingredient-unavailable");
        tb.setCache(true);
        tb.setCacheHint(CacheHint.SPEED);

        tb.setMinWidth(220);
        tb.setPrefWidth(220);
        tb.setMaxWidth(260);
        tb.setMinHeight(180);
        tb.setPrefHeight(200);
        tb.setMaxHeight(240);

        VBox inner = new VBox(8);
        inner.setAlignment(Pos.CENTER);
        inner.setMaxWidth(ING_INNER_W);
        inner.setPrefWidth(ING_INNER_W);
        inner.setPadding(new Insets(8, 4, 8, 4));

        Label nameLbl = new Label(ing.nome);
        nameLbl.getStyleClass().add("kumpir-ingredient-name");
        nameLbl.setWrapText(true);
        nameLbl.setTextAlignment(TextAlignment.CENTER);
        nameLbl.setMaxWidth(ING_LABEL_W);
        nameLbl.setMinHeight(Label.USE_PREF_SIZE);

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

        record IngData(Ingredient ing, boolean hasAllergens, String allergenText) {
        }
        List<IngData> addData = toAdd.stream()
                .map(ing -> new IngData(
                        ing, !ing.allergeni.isEmpty(),
                        ing.allergeni.isEmpty() ? "" : "⚠ " + String.join(", ", ing.allergeni)))
                .collect(Collectors.toList());

        Platform.runLater(() -> {
            toRemove.forEach(id -> {
                IngNode n = ingNodeById.remove(id);
                if (n != null) {
                    ingNodes.remove(n);
                    kumpirGrid.getChildren().remove(n.btn());
                    if (selectedIds.remove(id))
                        selectedCount--;
                }
            });

            for (Ingredient fresh : toUpdate) {
                IngNode node = ingNodeById.get(fresh.id);
                if (node == null)
                    continue;
                patchIngNode(node, fresh);
                int idx = ingNodes.indexOf(node);
                IngNode updated = new IngNode(fresh, node.btn(), node.nameLbl(), node.priceLbl(), node.allergenBadge());
                ingNodes.set(idx, updated);
                ingNodeById.put(fresh.id, updated);
            }

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

    private void ensureKumpirGrid() {
        if (kumpirGrid != null)
            return;
        kumpirGrid = new FlowPane(16, 16);
        kumpirGrid.getStyleClass().add("kumpir-ingredient-grid");
        kumpirGrid.setPadding(new Insets(16));
        kumpirGrid.setCache(true);
        kumpirGrid.setCacheHint(CacheHint.SPEED);
    }

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
                RemoteLogger.error("ComposeKumpir", "periodic sync", e);
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

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
            RemoteLogger.error("ComposeKumpir", "saveToDiskCache", e);
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
            RemoteLogger.error("ComposeKumpir", "loadFromDiskCache", e);
            return List.of();
        }
    }

    public void openComposeKumpir() {
        if (!nodesReady) {
            showToast("Preparazione ingredienti…");
            return;
        }
        showKumpirModal();
    }

    private void showKumpirModal() {
        if (rootStack == null || kumpirOverlay == null)
            return;

        resetKumpirModalState();

        if (!rootStack.getChildren().contains(kumpirOverlay))
            rootStack.getChildren().add(kumpirOverlay);
        kumpirOverlay.setVisible(true);

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

    private void buildKumpirModalStructure() {
        if (rootStack == null || kumpirGrid == null)
            return;

        kumpirTotalLbl = new Label(PRICE_BASE_FMT);
        kumpirTotalLbl.getStyleClass().add("kumpir-total-label");

        kumpirCountLbl = new Label("Nessun ingrediente selezionato");
        kumpirCountLbl.getStyleClass().add("kumpir-count-label");

        kumpirOverlay = new StackPane();
        kumpirOverlay.setAlignment(Pos.CENTER);
        kumpirOverlay.setVisible(false);
        kumpirOverlay.prefWidthProperty().bind(rootStack.widthProperty());
        kumpirOverlay.prefHeightProperty().bind(rootStack.heightProperty());
        kumpirOverlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        kumpirCard = new VBox(0);
        kumpirCard.getStyleClass().add("kumpir-modal-card");
        kumpirCard.setCache(true);
        kumpirCard.setCacheHint(CacheHint.SPEED);

        ChangeListener<Number> sizeListener = (obs, o, n) -> syncCardSize();
        rootStack.widthProperty().addListener(sizeListener);
        rootStack.heightProperty().addListener(sizeListener);
        syncCardSize();

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

        kumpirFilterNoAllergens = filterChip("Senza allergeni");
        kumpirFilterNoGluten = filterChip("Senza glutine");
        kumpirFilterNoLactose = filterChip("Senza lattosio");

        FlowPane filterFlow = new FlowPane(8, 8);
        filterFlow.setAlignment(Pos.CENTER_LEFT);
        filterFlow.getChildren().addAll(
                kumpirFilterNoAllergens, kumpirFilterNoGluten, kumpirFilterNoLactose);

        VBox filterBar = new VBox(10, filterFlow);
        filterBar.getStyleClass().add("kumpir-filter-bar");
        filterBar.setPadding(new Insets(12, 16, 12, 16));

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

        ScrollPane gridScroll = new ScrollPane(kumpirGrid);
        gridScroll.setFitToWidth(true);
        gridScroll.setFocusTraversable(false);
        gridScroll.getStyleClass().add("kumpir-grid-scroll");
        VBox.setVgrow(gridScroll, Priority.ALWAYS);
        Animations.inertiaScroll(gridScroll);

        kumpirCard.widthProperty().addListener((obs, o, nw) -> syncGridGap(nw.doubleValue()));

        Button addBtn = new Button("🛒  Aggiungi al carrello");
        addBtn.getStyleClass().add("kumpir-add-btn");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox totalBar = new HBox(12, kumpirCountLbl, spacer, kumpirTotalLbl, addBtn);
        totalBar.setAlignment(Pos.CENTER);
        totalBar.getStyleClass().add("kumpir-total-bar");
        totalBar.setPadding(new Insets(12, 16, 12, 16));

        if (rootStack != null && rootStack.getScene() != null) {
            // keep focus
        }

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

        addBtn.setOnAction(e -> {
            if (selectedCount == 0) {
                shakeNode(addBtn);
                showToast("Seleziona almeno un ingrediente! 🥔");
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
            if (toastOverlay != null)
                toastOverlay.show("Kumpir aggiunto al carrello! 🥔");
            else
                showToast("Kumpir aggiunto al carrello! 🥔");
        });

        kumpirCard.getChildren().addAll(modalHeader, filterBar, gridScroll, totalBar);
        kumpirOverlay.getChildren().add(kumpirCard);
        rootStack.getChildren().add(kumpirOverlay);
    }

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
            w = Math.max(w, Math.min(520, sw * 0.96));
            h = Math.max(h, Math.min(480, sh * 0.93));
        }

        kumpirCard.setMaxWidth(w);
        kumpirCard.setPrefWidth(w);
        kumpirCard.setMaxHeight(h);
        kumpirCard.setPrefHeight(h);
    }

    private void syncGridGap(double cardWidth) {
        if (kumpirGrid == null)
            return;
        double gap = cardWidth < 640 ? 10 : cardWidth < 840 ? 12 : 16;
        kumpirGrid.setHgap(gap);
        kumpirGrid.setVgap(gap);

        double safeWidth = Math.max(0, cardWidth - 40);
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

        double wrapLen = Math.max(0, cardW * 3 + gap * 2);
        kumpirGrid.setPrefWrapLength(wrapLen);
    }

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

        for (IngNode n : ingNodes) {
            n.btn().setManaged(true);
            n.btn().setVisible(true);
        }
        if (kumpirTotalLbl != null)
            kumpirTotalLbl.setText(PRICE_BASE_FMT);
        if (kumpirCountLbl != null)
            kumpirCountLbl.setText("Nessun ingrediente selezionato");
    }

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

    private void enableKumpirButton() {
        if (composeKumpirBtn == null)
            return;
        composeKumpirBtn.setDisable(false);
        kumpirBtnPulse.play();
        buildKumpirModalStructure();
    }

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
            showToast(online ? "Connessione internet ripristinata" : "Connessione internet persa: modalità offline");
        }
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
