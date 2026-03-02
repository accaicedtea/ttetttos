package com.example;

import javafx.animation.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;
import com.util.Log;
import com.util.Animations;
public class TestPageController {

    @FXML private ScrollPane rootScroll;

    @FXML private StackPane skewedToggle;
    @FXML private StackPane skewedBg;
    @FXML private Label skewedOffLabel;
    @FXML private Label skewedOnLabel;
    @FXML private Label skewedStatusLabel;

    @FXML private StackPane flipToggle;
    @FXML private StackPane flipFront;
    @FXML private StackPane flipBack;
    @FXML private Label flipStatusLabel;

    @FXML private StackPane slideToggle;
    @FXML private StackPane slideTrack;
    @FXML private Circle slideThumb;
    @FXML private Label slideStatusLabel;

    @FXML private ComboBox<String> testCombo;
    @FXML private Spinner<Integer> testSpinner;
    @FXML private ProgressBar testProgress;
    @FXML private ProgressBar animatedProgress;
    @FXML private Label progressLabel;
    @FXML private Slider testSlider;
    @FXML private Label sliderValueLabel;
    @FXML private TableView<String[]> testTable;
    @FXML private TableColumn<String[], String> colName;
    @FXML private TableColumn<String[], String> colType;
    @FXML private TableColumn<String[], String> colSize;
    @FXML private TableColumn<String[], String> colStatus;
    
    private boolean skewedOn = false;
    private boolean flipOn   = false;
    private boolean slideOn  = false;
    
    @FXML private BorderPane animatedCard;
    @FXML private BorderPane animatedCard2;
    
     @FXML
    public void initialize() {
        Log.info("initialize() START");
    
        setupSkewedToggle();
        setupFlipToggle();
        setupSlideToggle();
        setupControls();
        setupTable();
        setupCard();
        setupInertiaScroll();
        Log.info("initialize() DONE");
    }
    
    // ─────────────────────────────────────────────────────────────
    //  INERTIA SCROLL
    // ─────────────────────────────────────────────────────────────
    private void setupInertiaScroll() {
        rootScroll.setPannable(false);

        // Buffer circolare per calcolare velocità robusta
        final int     BUF      = 8;
        final double[] bufY    = new double[BUF];
        final long[]   bufT    = new long[BUF];
        final int[]    bufHead = {0};
        final int[]    bufSize = {0};

        final double[] velocityPxMs = {0};

        AnimationTimer inertiaTimer = new AnimationTimer() {
            private long prevNano = 0;
            @Override
            public void handle(long nowNano) {
                if (prevNano == 0) { prevNano = nowNano; return; }
                double dtMs = (nowNano - prevNano) / 1_000_000.0;
                prevNano = nowNano;
                double scrollableH = scrollableHeight();
                if (scrollableH <= 0) { stop(); return; }
                rootScroll.setVvalue(clamp01(
                    rootScroll.getVvalue() + velocityPxMs[0] * dtMs / scrollableH));
                // attrito: dimezza ogni 400ms
                velocityPxMs[0] *= Math.pow(0.5, dtMs / 400.0);
                if (Math.abs(velocityPxMs[0]) < 0.001) {
                    velocityPxMs[0] = 0; prevNano = 0; stop();
                }
            }
        };

        rootScroll.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            inertiaTimer.stop();
            velocityPxMs[0] = 0;
            bufHead[0] = 0; bufSize[0] = 0;
            bufY[0] = e.getSceneY(); bufT[0] = System.nanoTime();
            bufHead[0] = 1; bufSize[0] = 1;
        });

        rootScroll.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            long   now = System.nanoTime();
            double y   = e.getSceneY();

            // scrivi nel buffer circolare
            int idx = bufHead[0] % BUF;
            bufY[idx] = y; bufT[idx] = now;
            bufHead[0]++;
            if (bufSize[0] < BUF) bufSize[0]++;

            // scorri immediatamente
            double scrollableH = scrollableHeight();
            if (scrollableH <= 0) return;
            int prevIdx = ((bufHead[0] - 2) % BUF + BUF) % BUF;
            double dtMs = (now - bufT[prevIdx]) / 1_000_000.0;
            if (dtMs <= 0) return;
            double dy = bufY[prevIdx] - y;
            rootScroll.setVvalue(clamp01(rootScroll.getVvalue() + dy / scrollableH));
        });

        rootScroll.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (bufSize[0] < 2) return;

            // Calcola velocità sugli ultimi 80ms (ignora micro-movimenti finali)
            long   now      = System.nanoTime();
            int    newest   = ((bufHead[0] - 1) % BUF + BUF) % BUF;
            double newestY  = bufY[newest];
            long   newestT  = bufT[newest];

            double oldestY = newestY;
            long   oldestT = newestT;
            for (int i = 1; i < bufSize[0]; i++) {
                int idx = ((bufHead[0] - 1 - i) % BUF + BUF) % BUF;
                double ageMsOfSample = (now - bufT[idx]) / 1_000_000.0;
                if (ageMsOfSample > 80.0) break;
                oldestY = bufY[idx];
                oldestT = bufT[idx];
            }

            double dtMs = (newestT - oldestT) / 1_000_000.0;
            if (dtMs > 5.0) {
                velocityPxMs[0] = (oldestY - newestY) / dtMs;
                if (Math.abs(velocityPxMs[0]) > 0.05) {
                    inertiaTimer.start();
                }
            }
        });
    }

    private double scrollableHeight() {
        if (rootScroll.getContent() == null) return 0;
        double contentH  = rootScroll.getContent().getBoundsInLocal().getHeight();
        double viewportH = rootScroll.getViewportBounds().getHeight();
        return Math.max(0, contentH - viewportH);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    // ─────────────────────────────────────────────────────────────
    //  SKEWED TOGGLE
    // ─────────────────────────────────────────────────────────────
    private void setupSkewedToggle() {
        skewedToggle.getTransforms().add(new javafx.scene.transform.Shear(-0.18, 0));

        Rectangle clip = new Rectangle(120, 48);
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        skewedToggle.setClip(clip);
        skewedToggle.layoutBoundsProperty().addListener((obs, o, n) -> {
            clip.setWidth(n.getWidth());
            clip.setHeight(n.getHeight());
        });

        skewedOnLabel.setTranslateX(120);
        skewedOnLabel.setOpacity(0);

        skewedToggle.setOnMouseClicked(e -> toggleSkewed());
    }

    private void toggleSkewed() {
        skewedOn = !skewedOn;
        Duration dur = Duration.millis(250);

        if (skewedOn) {
            skewedOffLabel.setOpacity(1);
            TranslateTransition offOut = new TranslateTransition(dur, skewedOffLabel);
            offOut.setToX(-120);
            FadeTransition offFade = new FadeTransition(dur, skewedOffLabel);
            offFade.setToValue(0);

            skewedOnLabel.setOpacity(1);
            skewedOnLabel.setTranslateX(120);
            TranslateTransition onIn = new TranslateTransition(dur, skewedOnLabel);
            onIn.setToX(0);
            FadeTransition onFade = new FadeTransition(dur, skewedOnLabel);
            onFade.setFromValue(0);
            onFade.setToValue(1);

            skewedBg.setStyle("-fx-background-color: #86d993; -fx-background-radius: 8;");
            new ParallelTransition(offOut, offFade, onIn, onFade).play();
            skewedStatusLabel.setText("ON");
            skewedStatusLabel.setStyle("-fx-text-fill: #86d993; -fx-font-weight: bold; -fx-font-size: 15px;");
        } else {
            skewedOnLabel.setOpacity(1);
            TranslateTransition onOut = new TranslateTransition(dur, skewedOnLabel);
            onOut.setToX(120);
            FadeTransition onFade = new FadeTransition(dur, skewedOnLabel);
            onFade.setToValue(0);

            skewedOffLabel.setOpacity(1);
            skewedOffLabel.setTranslateX(-120);
            TranslateTransition offIn = new TranslateTransition(dur, skewedOffLabel);
            offIn.setToX(0);
            FadeTransition offFade = new FadeTransition(dur, skewedOffLabel);
            offFade.setFromValue(0);
            offFade.setToValue(1);

            skewedBg.setStyle("-fx-background-color: #888888; -fx-background-radius: 8;");
            new ParallelTransition(onOut, onFade, offIn, offFade).play();
            skewedStatusLabel.setText("OFF");
            skewedStatusLabel.setStyle("-fx-text-fill: #8888a0; -fx-font-size: 15px;");
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  FLIP TOGGLE
    // ─────────────────────────────────────────────────────────────
    private void setupFlipToggle() {
        flipBack.setVisible(false);
        flipToggle.setOnMouseClicked(e -> toggleFlip());
    }

    private void toggleFlip() {
        flipOn = !flipOn;
        Duration half = Duration.millis(200);

        RotateTransition p1 = new RotateTransition(half, flipToggle);
        p1.setAxis(Rotate.Y_AXIS);
        p1.setFromAngle(0);
        p1.setToAngle(90);
        p1.setInterpolator(Interpolator.EASE_IN);
        p1.setOnFinished(e -> {
            flipFront.setVisible(!flipOn);
            flipBack.setVisible(flipOn);
            RotateTransition p2 = new RotateTransition(half, flipToggle);
            p2.setAxis(Rotate.Y_AXIS);
            p2.setFromAngle(-90);
            p2.setToAngle(0);
            p2.setInterpolator(Interpolator.EASE_OUT);
            p2.play();
        });
        p1.play();

        if (flipOn) {
            flipStatusLabel.setText("Yeah!");
            flipStatusLabel.setStyle("-fx-text-fill: #7FC6A6; -fx-font-weight: bold; -fx-font-size: 15px;");
        } else {
            flipStatusLabel.setText("Nope");
            flipStatusLabel.setStyle("-fx-text-fill: #8888a0; -fx-font-size: 15px;");
        }
    }
    
    @FXML
    private void TestFlip2() {
        Animations.flip(animatedCard, animatedCard2);
        System.out.println("animatedCard2.isVisible(): " + animatedCard2.isVisible());
        System.out.println("animatedCard.isVisible(): " + animatedCard.isVisible());
        
    }
    // ─────────────────────────────────────────────────────────────
    //  SLIDE TOGGLE
    // ─────────────────────────────────────────────────────────────
    private void setupSlideToggle() {
        slideThumb.setTranslateX(-15);
        slideToggle.setOnMouseClicked(e -> toggleSlide());
    }

    private void toggleSlide() {
        slideOn = !slideOn;
        Duration dur = Duration.millis(200);

        TranslateTransition slide = new TranslateTransition(dur, slideThumb);
        slide.setInterpolator(Interpolator.EASE_BOTH);

        if (slideOn) {
            slide.setToX(15);
            slideTrack.getStyleClass().remove("tgl-slide-track-off");
            if (!slideTrack.getStyleClass().contains("tgl-slide-track-on"))
                slideTrack.getStyleClass().add("tgl-slide-track-on");
            slideStatusLabel.setText("ON");
            slideStatusLabel.setStyle("-fx-text-fill: #4ec97a; -fx-font-weight: bold; -fx-font-size: 15px;");
        } else {
            slide.setToX(-15);
            slideTrack.getStyleClass().remove("tgl-slide-track-on");
            slideStatusLabel.setText("OFF");
            slideStatusLabel.setStyle("-fx-text-fill: #8888a0; -fx-font-size: 15px;");
        }

        ScaleTransition bump = new ScaleTransition(Duration.millis(100), slideThumb);
        bump.setToX(1.2);
        bump.setToY(1.2);
        bump.setAutoReverse(true);
        bump.setCycleCount(2);

        new ParallelTransition(slide, bump).play();
    }

    // ─────────────────────────────────────────────────────────────
    //  STANDARD CONTROLS
    // ─────────────────────────────────────────────────────────────
    private void setupControls() {
        testCombo.setItems(FXCollections.observableArrayList(
            "Documenti", "Immagini", "Video", "Audio", "Codice", "Archivi"
        ));
        testCombo.getSelectionModel().selectFirst();

        testSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100, 30)
        );

        testSlider.valueProperty().addListener((obs, old, val) ->
            sliderValueLabel.setText(String.valueOf(val.intValue()))
        );
    }

    // ─────────────────────────────────────────────────────────────
    //  TABLE
    // ─────────────────────────────────────────────────────────────
    private void setupTable() {
        colName.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue()[0]));
        colType.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue()[1]));
        colSize.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue()[2]));
        colStatus.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue()[3]));

        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                switch (item) {
                    case "✓ OK"       -> setStyle("-fx-text-fill: #4ec97a;");
                    case "⏳ Pending" -> setStyle("-fx-text-fill: #e0b84d;");
                    case "✗ Error"    -> setStyle("-fx-text-fill: #e05555;");
                    default           -> setStyle("-fx-text-fill: #8888a0;");
                }
            }
        });

        testTable.setItems(FXCollections.observableArrayList(
            new String[]{"documento.pdf",      "Documenti", "2.4 MB",  "✓ OK"},
            new String[]{"foto_001.jpg",       "Immagini",  "5.1 MB",  "✓ OK"},
            new String[]{"video_tutorial.mp4", "Video",     "842 MB",  "⏳ Pending"},
            new String[]{"progetto.zip",       "Archivi",   "156 MB",  "✓ OK"},
            new String[]{"backup_old.tar",     "Archivi",   "1.2 GB",  "✗ Error"},
            new String[]{"main.py",            "Codice",    "12 KB",   "✓ OK"}
        ));
    }

    // ─────────────────────────────────────────────────────────────
    //  CARD
    // ─────────────────────────────────────────────────────────────
    private void setupCard() {
        // Check if card are visible before animation
        Log.info("animatedCard = " + animatedCard.isVisible());
        Log.info("animatedCard2 = " + animatedCard2.isVisible());
        if(animatedCard.isVisible() && animatedCard2.isVisible()) {
            Log.info("Both cards are visible before animation. Correction");
            animatedCard2.setVisible(false);
            Log.info("Correction completed.");
        } else {
            Log.info("One or both cards are NOT visible before animation.");
            
        }
    }


    // ─────────────────────────────────────────────────────────────
    //  PROGRESS ANIMATION
    // ─────────────────────────────────────────────────────────────
    @FXML
    private void onAnimateProgress() {
        animatedProgress.setProgress(0);
        Timeline tl = new Timeline();
        for (int i = 0; i <= 100; i++) {
            final int pct = i;
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(i * 30),
                e -> animatedProgress.setProgress(pct / 100.0)));
        }
        tl.play();
    }

    @FXML
    private void onCardClicked() {
        Animations.scaleDown(animatedCard, 120, 0.96);
    }
}
