package com.example;

import com.api.services.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Stage;

/**
 * Applicazione di test per le API.
 * Esegui con: mvn javafx:run@test
 */
public class ApiTestApp extends Application {

    // ── Credenziali hardcoded per i test ─────────────────────────
    private static final String TEST_EMAIL    = "admin@accaicedtea.it";
    private static final String TEST_PASSWORD = "admin123";
    // ─────────────────────────────────────────────────────────────

    private TextArea logArea;
    private Button testBtn;
    private Label statusLabel;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        testBtn = new Button("Riesegui tutti i test");
        testBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
        testBtn.setDisable(true);
        testBtn.setOnAction(e -> runAllTests());

        Button clearBtn = new Button("Pulisci");
        clearBtn.setStyle("-fx-background-color: #7f8c8d; -fx-text-fill: white;");
        clearBtn.setOnAction(e -> logArea.clear());

        statusLabel = new Label("Login in corso...");
        statusLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");

        Label credLabel = new Label("📧 " + TEST_EMAIL);

        HBox actionRow = new HBox(10, credLabel, testBtn, clearBtn, statusLabel);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        VBox header = new VBox(actionRow);
        header.setPadding(new Insets(10));
        header.setStyle("-fx-background-color: #2c3e50;");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.setFont(Font.font("Monospaced", 11));
        VBox.setVgrow(logArea, Priority.ALWAYS);

        VBox root = new VBox(header, logArea);
        Scene scene = new Scene(root, 900, 600);
        stage.setScene(scene);
        stage.setTitle("API Test — TOTTEM");
        stage.setOnCloseRequest(e -> { Platform.exit(); System.exit(0); });
        stage.show();

        doLogin();
    }

    // -----------------------------------------------------------------------

    private void doLogin() {
        log(">> Login come \"" + TEST_EMAIL + "\"...");
        new Thread(() -> {
            try {
                var result = AuthService.login(TEST_EMAIL, TEST_PASSWORD);
                Platform.runLater(() -> {
                    log("[OK] Login riuscito. Token salvato.");
                    statusLabel.setText("✔ Autenticato");
                    statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    testBtn.setDisable(false);
                    runAllTests();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    log("[ERRORE] Login fallito: " + ex.getMessage());
                    statusLabel.setText("✘ Login fallito");
                    statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                });
            }
        }).start();
    }

    private void runAllTests() {
        testBtn.setDisable(true);
        log("\n════════════════════════════════════════════════════════════");
        log("  TEST COMPLETO DI TUTTI GLI ENDPOINT");
        log("════════════════════════════════════════════════════════════\n");

        new Thread(() -> {

            // // ── CRUD endpoints ────────────────────────────────────────
            // section("CRUD — allergens");
            // get("allergens.getAll",    () -> AllergensService.getAll());

            // section("CRUD — categories");
            // get("categories.getAll",   () -> CategoriesService.getAll());

            // section("CRUD — ingredients");
            // get("ingredients.getAll",  () -> IngredientsService.getAll());

            // section("CRUD — products");
            // get("products.getAll",     () -> ProductsService.getAll());

            // section("CRUD — promotions");
            // get("promotions.getAll",   () -> PromotionsService.getAll());

            // ── Viste con token ───────────────────────────────────────
            section("VIEW (auth) — v_products_full");
            get("views.getProductsFull",          () -> ViewsService.getProductsFull());

            // section("VIEW (auth) — v_categories_with_count");
            // get("views.getCategoriesWithCount",   () -> ViewsService.getCategoriesWithCount());

            // section("VIEW (auth) — v_ingredients_with_allergens");
            // get("views.getIngredientsWithAllergens", () -> ViewsService.getIngredientsWithAllergens());

            // section("VIEW (auth) — v_products_with_promotions");
            // get("views.getProductsWithPromotions",() -> ViewsService.getProductsWithPromotions());

            Platform.runLater(() -> {
                log("\n════════════════════════════════════════════════════════════");
                log("  TEST COMPLETATI");
                log("════════════════════════════════════════════════════════════");
                testBtn.setDisable(false);
            });

        }).start();
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    @FunctionalInterface
    interface ApiCall {
        com.google.gson.JsonObject call() throws Exception;
    }

    private void section(String title) {
        log("\n── " + title + " ──────────────────────────────");
    }

    private void get(String label, ApiCall call) {
        try {
            var result = call.call();
            // stampa solo i primi 200 caratteri per non intasare il log
            String json = result.toString();
            String preview = json.length() > 200 ? json.substring(0, 200) + "..." : json;
            log("[OK]  " + label + "\n      " + preview);
        } catch (Exception ex) {
            log("[ERR] " + label + " → " + ex.getMessage());
        }
    }

    private void log(String msg) {
        Platform.runLater(() -> {
            logArea.appendText(msg + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }
}
