package com.app.components;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.util.Duration;

import com.app.model.I18n;

import java.util.*;
import java.util.function.Consumer;

/**
 * ModalDialog — componente generico per pop-up sovraimpressi.
 *
 * ┌─────────────────────────────────────────────────────┐
 * │ ░░░░░░░░ BACKDROP SEMITRASPARENTE ░░░░░░░░░░░░░░░░░ │
 * │ ░░░ ┌──────────────────────────────────┐ ░░░░░░░ │
 * │ ░░░ │ 🔴 TITOLO [✕] │ ░░░░░░░ │
 * │ ░░░ │─────────────────────────────────│ ░░░░░░░ │
 * │ ░░░ │ Sottotitolo (opzionale) │ ░░░░░░░ │
 * │ ░░░ │ │ ░░░░░░░ │
 * │ ░░░ │ [contenuto custom / messaggio] │ ░░░░░░░ │
 * │ ░░░ │ │ ░░░░░░░ │
 * │ ░░░ │─────────────────────────────────│ ░░░░░░░ │
 * │ ░░░ │ [Annulla] [Conferma] │ ░░░░░░░ │
 * │ ░░░ └──────────────────────────────────┘ ░░░░░░░ │
 * └─────────────────────────────────────────────────────┘
 *
 * <b>Tipi predefiniti (factory method statici):</b>
 * <ul>
 * <li>{@link #info(StackPane, String, String)}</li>
 * <li>{@link #warning(StackPane, String, String, ModalButton...)}</li>
 * <li>{@link #error(StackPane, String, String)}</li>
 * <li>{@link #confirm(StackPane, String, String, Runnable)}</li>
 * <li>{@link #input(StackPane, String, String, String, Consumer)}</li>
 * <li>{@link #loading(StackPane, String)}</li>
 * <li>{@link #custom(StackPane, String, Node, ModalButton...)}</li>
 * </ul>
 *
 * <b>Uso con Builder (massima flessibilità):</b>
 * 
 * <pre>
 * ModalDialog dialog = ModalDialog.builder(rootPane)
 *         .type(ModalDialog.Type.WARNING)
 *         .title("Attenzione")
 *         .subtitle("Operazione irreversibile")
 *         .message("Sei sicuro di voler eliminare questo prodotto?")
 *         .icon("⚠")
 *         .closeOnBackdrop(false)
 *         .closeOnEscape(true)
 *         .width(480)
 *         .button(ModalButton.cancel("Annulla"))
 *         .button(ModalButton.danger("Elimina", this::deleteProduct))
 *         .onClose(() -> System.out.println("modal chiuso"))
 *         .show();
 * </pre>
 */
public final class ModalDialog {

    // ─────────────────────────────────────────────────────────────────
    // Enumerazioni
    // ─────────────────────────────────────────────────────────────────

    /** Tipo semantico del modal — determina icona e colori di default. */
    public enum Type {
        INFO, // ℹ blu
        SUCCESS, // ✓ verde
        WARNING, // ⚠ giallo
        ERROR, // ✕ rosso
        CONFIRM, // ? neutro
        INPUT, // ✏ neutro
        LOADING, // ↻ neutro (no bottoni, no chiusura manuale)
        CUSTOM // definito dal chiamante
    }

    /** Posizione del modal sullo schermo. */
    public enum Position {
        CENTER, TOP, BOTTOM
    }

    /** Direzione dell'animazione di entrata. */
    public enum Animation {
        SLIDE_UP, SLIDE_DOWN, FADE, SCALE, NONE
    }

    // ─────────────────────────────────────────────────────────────────
    // Stato interno
    // ─────────────────────────────────────────────────────────────────

    private final StackPane parent;
    private final Type type;
    private final String title;
    private final String subtitle;
    private final String message;
    private final String icon;
    private final Node customContent;
    private final List<ModalButton> buttons;
    private final double maxWidth;
    private final double maxHeight;
    private final boolean closeable; // mostra il pulsante [X]
    private final boolean closeOnBackdrop; // click sul backdrop chiude
    private final boolean closeOnEscape; // ESC chiude
    private final boolean showDivider; // linea tra header e body
    private final Position position;
    private final Animation animation;
    private final Runnable onClose;
    private final int autoDismissMs; // 0 = no auto-dismiss

    // Nodi costruiti al momento dello show()
    private StackPane backdropNode;
    private VBox dialogNode;
    private boolean dismissed = false;

    // ─────────────────────────────────────────────────────────────────
    // Costruttore privato (via Builder)
    // ─────────────────────────────────────────────────────────────────

    private ModalDialog(Builder b) {
        this.parent = b.parent;
        this.type = b.type;
        this.title = b.title;
        this.subtitle = b.subtitle;
        this.message = b.message;
        this.icon = resolveIcon(b.icon, b.type);
        this.customContent = b.customContent;
        this.buttons = Collections.unmodifiableList(b.buttons);
        this.maxWidth = b.maxWidth;
        this.maxHeight = b.maxHeight;
        this.closeable = b.closeable;
        this.closeOnBackdrop = b.closeOnBackdrop;
        this.closeOnEscape = b.closeOnEscape;
        this.showDivider = b.showDivider;
        this.position = b.position;
        this.animation = b.animation;
        this.onClose = b.onClose;
        this.autoDismissMs = b.autoDismissMs;
    }

    // ─────────────────────────────────────────────────────────────────
    // Factory method statici — pattern comuni pronti all'uso
    // ─────────────────────────────────────────────────────────────────

    /**
     * Modal informativo con un solo pulsante "OK".
     * 
     * <pre>
     * ModalDialog.info(rootPane, "Titolo", "Messaggio");
     * </pre>
     */
    public static ModalDialog info(StackPane parent, String title, String message) {
        return builder(parent).type(Type.INFO).title(title).message(message)
                .button(ModalButton.primary(I18n.t("ok").isEmpty() ? "OK" : I18n.t("ok"), null))
                .show();
    }

    /**
     * Modal di avviso con pulsanti personalizzabili.
     * 
     * <pre>
     * ModalDialog.warning(rootPane, "Attenzione", "Testo", ModalButton.cancel("OK"));
     * </pre>
     */
    public static ModalDialog warning(StackPane parent, String title, String message, ModalButton... btns) {
        Builder b = builder(parent).type(Type.WARNING).title(title).message(message);
        if (btns.length == 0)
            b.button(ModalButton.primary("OK", null));
        else
            for (ModalButton mb : btns)
                b.button(mb);
        return b.show();
    }

    /**
     * Modal di errore con dettaglio opzionale.
     * 
     * <pre>
     * ModalDialog.error(rootPane, "Errore", e.getMessage());
     * </pre>
     */
    public static ModalDialog error(StackPane parent, String title, String message) {
        return builder(parent).type(Type.ERROR).title(title).message(message)
                .button(ModalButton.ghost("Chiudi", null))
                .show();
    }

    /**
     * Modal di conferma con pulsanti Annulla / Conferma.
     * 
     * <pre>
     * ModalDialog.confirm(rootPane, "Elimina?", "Operazione irreversibile.", () -> delete());
     * </pre>
     */
    public static ModalDialog confirm(StackPane parent, String title, String message, Runnable onConfirm) {
        return builder(parent).type(Type.CONFIRM).title(title).message(message)
                .closeOnBackdrop(false)
                .button(ModalButton.cancel("Annulla"))
                .button(ModalButton.primary("Conferma", onConfirm))
                .show();
    }

    /**
     * Variante confirm con testo bottone di conferma personalizzato e stile danger.
     */
    public static ModalDialog confirmDanger(StackPane parent, String title, String message,
            String confirmLabel, Runnable onConfirm) {
        return builder(parent).type(Type.WARNING).title(title).message(message)
                .closeOnBackdrop(false)
                .button(ModalButton.cancel("Annulla"))
                .button(ModalButton.danger(confirmLabel, onConfirm))
                .show();
    }

    /**
     * Modal con campo di input testuale.
     * 
     * <pre>
     * ModalDialog.input(rootPane, "Inserisci nome", "Nome prodotto:", "default", val -> use(val));
     * </pre>
     *
     * @param onConfirm riceve il testo inserito dall'utente (già trimmed)
     */
    public static ModalDialog input(StackPane parent, String title, String placeholder,
            String defaultValue, Consumer<String> onConfirm) {
        TextField tf = new TextField(defaultValue == null ? "" : defaultValue);
        tf.setPromptText(placeholder);
        tf.getStyleClass().add("modal-input-field");
        tf.setMaxWidth(Double.MAX_VALUE);

        ModalDialog[] ref = new ModalDialog[1]; // accesso al dialog dall'interno del closure
        Builder b = builder(parent)
                .type(Type.INPUT).title(title)
                .customContent(tf)
                .button(ModalButton.cancel("Annulla"))
                .button(ModalButton.primary("Conferma", () -> {
                    if (onConfirm != null)
                        onConfirm.accept(tf.getText().trim());
                }));

        ref[0] = b.show();
        // Focus sul campo di testo
        Platform.runLater(tf::requestFocus);
        // Enter nel campo di testo = click Conferma
        tf.setOnAction(e -> {
            if (ref[0] != null) {
                if (onConfirm != null)
                    onConfirm.accept(tf.getText().trim());
                ref[0].dismiss();
            }
        });
        return ref[0];
    }

    /**
     * Modal di loading (non chiudibile manualmente).
     * Usare {@link #dismiss()} programmaticamente quando l'operazione finisce.
     * 
     * <pre>
     * ModalDialog loading = ModalDialog.loading(rootPane, "Caricamento...");
     * // ... operazione asincrona ...
     * Platform.runLater(loading::dismiss);
     * </pre>
     */
    public static ModalDialog loading(StackPane parent, String message) {
        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(64, 64);
        pi.getStyleClass().add("modal-progress");

        VBox content = new VBox(16, pi);
        content.setAlignment(Pos.CENTER);
        if (message != null && !message.isBlank()) {
            Label lbl = new Label(message);
            lbl.getStyleClass().add("modal-loading-label");
            lbl.setWrapText(true);
            content.getChildren().add(lbl);
        }

        return builder(parent)
                .type(Type.LOADING)
                .closeable(false)
                .closeOnBackdrop(false)
                .closeOnEscape(false)
                .showDivider(false)
                .customContent(content)
                .width(280)
                .show();
    }

    /**
     * Modal con successo e auto-dismiss configurabile.
     * 
     * <pre>
     * ModalDialog.success(rootPane, "Salvato!", "I dati sono stati aggiornati.", 2000);
     * </pre>
     *
     * @param autoDismissMs millisecondi dopo cui chiudersi automaticamente (0 = no)
     */
    public static ModalDialog success(StackPane parent, String title, String message, int autoDismissMs) {
        return builder(parent).type(Type.SUCCESS).title(title).message(message)
                .autoDismiss(autoDismissMs)
                .button(ModalButton.ghost("Chiudi", null))
                .show();
    }

    /**
     * Modal completamente custom — il chiamante fornisce qualsiasi Node come corpo.
     * 
     * <pre>
     * ModalDialog.custom(rootPane, "Dettaglio Prodotto", myProductNode,
     *         ModalButton.ghost("Chiudi", null),
     *         ModalButton.primary("Aggiungi al carrello", () -> addToCart()));
     * </pre>
     */
    public static ModalDialog custom(StackPane parent, String title, Node content, ModalButton... btns) {
        Builder b = builder(parent).type(Type.CUSTOM).title(title).customContent(content);
        for (ModalButton mb : btns)
            b.button(mb);
        return b.show();
    }

    // ─────────────────────────────────────────────────────────────────
    // Builder
    // ─────────────────────────────────────────────────────────────────

    public static Builder builder(StackPane parent) {
        return new Builder(parent);
    }

    public static final class Builder {
        private final StackPane parent;
        private Type type = Type.INFO;
        private String title = "";
        private String subtitle = null;
        private String message = null;
        private String icon = null; // null = usa default del tipo
        private Node customContent = null;
        private final List<ModalButton> buttons = new ArrayList<>();
        private double maxWidth = 520;
        private double maxHeight = 0; // 0 = auto
        private boolean closeable = true;
        private boolean closeOnBackdrop = true;
        private boolean closeOnEscape = true;
        private boolean showDivider = true;
        private Position position = Position.CENTER;
        private Animation animation = Animation.SLIDE_UP;
        private Runnable onClose = null;
        private int autoDismissMs = 0;

        private Builder(StackPane parent) {
            if (parent == null)
                throw new IllegalArgumentException("parent StackPane non può essere null");
            this.parent = parent;
        }

        public Builder type(Type t) {
            this.type = t;
            return this;
        }

        public Builder title(String t) {
            this.title = t;
            return this;
        }

        public Builder subtitle(String s) {
            this.subtitle = s;
            return this;
        }

        public Builder message(String m) {
            this.message = m;
            return this;
        }

        public Builder icon(String i) {
            this.icon = i;
            return this;
        }

        public Builder customContent(Node n) {
            this.customContent = n;
            return this;
        }

        public Builder width(double w) {
            this.maxWidth = w;
            return this;
        }

        public Builder height(double h) {
            this.maxHeight = h;
            return this;
        }

        public Builder closeable(boolean c) {
            this.closeable = c;
            return this;
        }

        public Builder closeOnBackdrop(boolean c) {
            this.closeOnBackdrop = c;
            return this;
        }

        public Builder closeOnEscape(boolean c) {
            this.closeOnEscape = c;
            return this;
        }

        public Builder showDivider(boolean d) {
            this.showDivider = d;
            return this;
        }

        public Builder position(Position p) {
            this.position = p;
            return this;
        }

        public Builder animation(Animation a) {
            this.animation = a;
            return this;
        }

        public Builder onClose(Runnable r) {
            this.onClose = r;
            return this;
        }

        public Builder autoDismiss(int ms) {
            this.autoDismissMs = ms;
            return this;
        }

        public Builder button(ModalButton btn) {
            if (btn != null)
                this.buttons.add(btn);
            return this;
        }

        /**
         * Costruisce e mostra il modal. Ritorna l'istanza per dismiss() programmatico.
         */
        public ModalDialog show() {
            ModalDialog d = new ModalDialog(this);
            Platform.runLater(d::buildAndShow);
            return d;
        }

        /** Costruisce senza mostrare (per show() ritardato). */
        public ModalDialog build() {
            return new ModalDialog(this);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Costruzione UI
    // ─────────────────────────────────────────────────────────────────

    private void buildAndShow() {
        // ── Backdrop ──────────────────────────────────────────────────
        backdropNode = new StackPane();
        backdropNode.getStyleClass().add("modal-backdrop");
        backdropNode.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // Chiusura su click backdrop
        if (closeOnBackdrop) {
            backdropNode.setOnMouseClicked(e -> {
                if (e.getTarget() == backdropNode)
                    dismiss();
            });
        }

        // ── Dialog card ───────────────────────────────────────────────
        dialogNode = new VBox();
        dialogNode.getStyleClass().addAll("modal-dialog", "modal-type-" + type.name().toLowerCase());
        dialogNode.setMaxWidth(maxWidth);
        if (maxHeight > 0)
            dialogNode.setMaxHeight(maxHeight);
        dialogNode.setMinWidth(Math.min(maxWidth, 280));

        // ── Header ────────────────────────────────────────────────────
        HBox header = buildHeader();
        dialogNode.getChildren().add(header);

        // ── Divider ───────────────────────────────────────────────────
        if (showDivider && (message != null || customContent != null || subtitle != null)) {
            Region div = new Region();
            div.getStyleClass().add("modal-divider");
            div.setPrefHeight(1);
            div.setMaxWidth(Double.MAX_VALUE);
            dialogNode.getChildren().add(div);
        }

        // ── Body ──────────────────────────────────────────────────────
        buildBody().ifPresent(body -> dialogNode.getChildren().add(body));

        // ── Footer con bottoni ────────────────────────────────────────
        if (!buttons.isEmpty()) {
            Region footerDiv = new Region();
            footerDiv.getStyleClass().add("modal-divider");
            footerDiv.setPrefHeight(1);
            footerDiv.setMaxWidth(Double.MAX_VALUE);
            dialogNode.getChildren().add(footerDiv);
            dialogNode.getChildren().add(buildFooter());
        }

        // ── Posizionamento ────────────────────────────────────────────
        StackPane.setAlignment(dialogNode, resolveAlignment());
        backdropNode.getChildren().add(dialogNode);

        // ── Clip arrotondato per il dialog ────────────────────────────
        dialogNode.setStyle("-fx-background-radius:16;");

        // ── ESC per chiudere ──────────────────────────────────────────
        if (closeOnEscape) {
            backdropNode.setFocusTraversable(true);
            backdropNode.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE)
                    dismiss();
            });
        }

        // ── Aggiunge al parent ────────────────────────────────────────
        parent.getChildren().add(backdropNode);

        // ── Animazione entrata ────────────────────────────────────────
        playEnterAnimation();

        // ── Auto-dismiss ──────────────────────────────────────────────
        if (autoDismissMs > 0) {
            Timeline timer = new Timeline(
                    new KeyFrame(Duration.millis(autoDismissMs), e -> dismiss()));
            timer.play();
        }

        // ── Focus ────────────────────────────────────────────────────
        Platform.runLater(backdropNode::requestFocus);
    }

    // ── Header ───────────────────────────────────────────────────────

    private HBox buildHeader() {
        HBox header = new HBox(12);
        header.getStyleClass().add("modal-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 20, 16, 20));

        // Icona (emoji/testo)
        if (icon != null && !icon.isBlank()) {
            Label iconLbl = new Label(icon);
            iconLbl.getStyleClass().addAll("modal-icon", "modal-icon-" + type.name().toLowerCase());
            header.getChildren().add(iconLbl);
        }

        // Titolo + sottotitolo (colonna)
        VBox titleBox = new VBox(2);
        titleBox.setFillWidth(true);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        if (title != null && !title.isBlank()) {
            Label titleLbl = new Label(title);
            titleLbl.getStyleClass().add("modal-title");
            titleLbl.setWrapText(true);
            titleBox.getChildren().add(titleLbl);
        }
        if (subtitle != null && !subtitle.isBlank()) {
            Label subLbl = new Label(subtitle);
            subLbl.getStyleClass().add("modal-subtitle");
            subLbl.setWrapText(true);
            titleBox.getChildren().add(subLbl);
        }
        header.getChildren().add(titleBox);

        // Pulsante chiusura [X]
        if (closeable) {
            Button closeBtn = new Button("✕");
            closeBtn.getStyleClass().add("modal-close-btn");
            closeBtn.setOnAction(e -> dismiss());
            header.getChildren().add(closeBtn);
        }

        return header;
    }

    // ── Body ─────────────────────────────────────────────────────────

    private Optional<Node> buildBody() {
        // Se c'è contenuto custom, ha la precedenza
        if (customContent != null) {
            VBox wrap = new VBox(customContent);
            wrap.getStyleClass().add("modal-body");
            wrap.setPadding(new Insets(16, 20, 16, 20));
            return Optional.of(wrap);
        }

        // Messaggio testuale
        if (message != null && !message.isBlank()) {
            Label msgLbl = new Label(message);
            msgLbl.getStyleClass().add("modal-message");
            msgLbl.setWrapText(true);
            msgLbl.setMaxWidth(Double.MAX_VALUE);
            msgLbl.setPadding(new Insets(0, 20, 20, 20));
            return Optional.of(msgLbl);
        }

        return Optional.empty();
    }

    // ── Footer ────────────────────────────────────────────────────────

    private HBox buildFooter() {
        HBox footer = new HBox(10);
        footer.getStyleClass().add("modal-footer");
        footer.setPadding(new Insets(14, 20, 18, 20));
        footer.setAlignment(Pos.CENTER_RIGHT);

        // Il primo bottone è "secondario" (allineato a sinistra se più di 2)
        if (buttons.size() > 2) {
            footer.setAlignment(Pos.CENTER);
            footer.getStyleClass().add("modal-footer-multi");
        }

        for (ModalButton mb : buttons) {
            Button btn = mb.buildNode();
            HBox.setHgrow(btn, buttons.size() <= 2 ? Priority.SOMETIMES : Priority.ALWAYS);
            btn.setOnAction(e -> {
                if (mb.getAction() != null)
                    mb.getAction().run();
                if (mb.closesModal())
                    dismiss();
            });
            footer.getChildren().add(btn);
        }

        return footer;
    }

    // ─────────────────────────────────────────────────────────────────
    // Animazioni
    // ─────────────────────────────────────────────────────────────────

    private void playEnterAnimation() {
        if (animation == Animation.NONE)
            return;

        backdropNode.setOpacity(0);

        switch (animation) {
            case SLIDE_UP -> {
                dialogNode.setTranslateY(60);
                dialogNode.setOpacity(0);
                new ParallelTransition(
                        fadeIn(backdropNode, 200),
                        new SequentialTransition(
                                new PauseTransition(Duration.millis(50)),
                                new ParallelTransition(
                                        fadeIn(dialogNode, 250),
                                        translateY(dialogNode, 60, 0, 300, Interpolator.EASE_OUT))))
                        .play();
            }
            case SLIDE_DOWN -> {
                dialogNode.setTranslateY(-60);
                dialogNode.setOpacity(0);
                new ParallelTransition(
                        fadeIn(backdropNode, 200),
                        new ParallelTransition(
                                fadeIn(dialogNode, 250),
                                translateY(dialogNode, -60, 0, 300, Interpolator.EASE_OUT)))
                        .play();
            }
            case SCALE -> {
                dialogNode.setScaleX(0.85);
                dialogNode.setScaleY(0.85);
                dialogNode.setOpacity(0);

                ScaleTransition scale = new ScaleTransition(Duration.millis(280), dialogNode);
                scale.setFromX(0.85);
                scale.setFromY(0.85);
                scale.setToX(1.0);
                scale.setToY(1.0);
                scale.setInterpolator(Interpolator.EASE_OUT);

                new ParallelTransition(
                        fadeIn(backdropNode, 200),
                        fadeIn(dialogNode, 250),
                        scale).play();
            }
            case FADE -> {
                dialogNode.setOpacity(0);
                new ParallelTransition(fadeIn(backdropNode, 220), fadeIn(dialogNode, 280)).play();
            }
        }
    }

    private void playExitAnimation(Runnable onFinished) {
        if (animation == Animation.NONE) {
            onFinished.run();
            return;
        }

        double toY = (animation == Animation.SLIDE_DOWN) ? -40 : 40;

        FadeTransition fadeBackdrop = new FadeTransition(Duration.millis(180), backdropNode);
        fadeBackdrop.setToValue(0);

        FadeTransition fadeDialog = new FadeTransition(Duration.millis(150), dialogNode);
        fadeDialog.setToValue(0);

        ParallelTransition exit = new ParallelTransition(
                fadeBackdrop,
                fadeDialog,
                translateY(dialogNode, 0, toY, 200, Interpolator.EASE_IN));
        exit.setOnFinished(e -> onFinished.run());
        exit.play();
    }

    // ─────────────────────────────────────────────────────────────────
    // Dismiss
    // ─────────────────────────────────────────────────────────────────

    /**
     * Chiude il modal con animazione di uscita.
     * Idempotente: chiamare più volte non causa problemi.
     */
    public void dismiss() {
        if (dismissed)
            return;
        dismissed = true;

        playExitAnimation(() -> {
            parent.getChildren().remove(backdropNode);
            if (onClose != null)
                onClose.run();
        });
    }

    /** Chiude il modal immediatamente senza animazione. */
    public void dismissImmediate() {
        if (dismissed)
            return;
        dismissed = true;
        parent.getChildren().remove(backdropNode);
        if (onClose != null)
            onClose.run();
    }

    /** true se il modal è già stato chiuso. */
    public boolean isDismissed() {
        return dismissed;
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private static String resolveIcon(String overrideIcon, Type type) {
        if (overrideIcon != null)
            return overrideIcon;
        return switch (type) {
            case INFO -> "ℹ";
            case SUCCESS -> "✓";
            case WARNING -> "⚠";
            case ERROR -> "✕";
            case CONFIRM -> "?";
            case INPUT -> "✏";
            case LOADING, CUSTOM -> null;
        };
    }

    private Pos resolveAlignment() {
        return switch (position) {
            case TOP -> Pos.TOP_CENTER;
            case BOTTOM -> Pos.BOTTOM_CENTER;
            default -> Pos.CENTER;
        };
    }

    private static FadeTransition fadeIn(Node n, int ms) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), n);
        ft.setToValue(1);
        return ft;
    }

    private static TranslateTransition translateY(Node n, double from, double to, int ms, Interpolator ip) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(ms), n);
        tt.setFromY(from);
        tt.setToY(to);
        tt.setInterpolator(ip);
        return tt;
    }
}
