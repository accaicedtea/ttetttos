package com.example;

// ─────────────────────────────────────────────────────────────────────────────
// IMPORT DEI SERVIZI API
//
// AuthService  → gestisce login/logout, salva il token JWT in SessionManager
// ViewsService → espone le viste SQL del backend (v_products_full, ecc.)
//
// Per aggiungere altri dati basta importare il servizio corrispondente, es.:
//   import com.api.services.CategoriesService;  // getAll(), getById(id)
//   import com.api.services.IngredientsService;  // getAll(), getById(id)
//   import com.api.services.PromotionsService;   // getAll(), getById(id)
// ─────────────────────────────────────────────────────────────────────────────
import com.api.services.AuthService;
import com.api.services.ViewsService;

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENTI GRAFICI E MODELLI
//
// ProductCard → componente VBox riutilizzabile che mostra una card prodotto
// Product     → model che wrappa un JsonObject da v_products_full e ne espone
//               i campi tipizzati (name, price, imageUrl, allergens, ingredients…)
// ─────────────────────────────────────────────────────────────────────────────
import com.example.components.ProductCard;
import com.example.model.Product;

// Gson: JsonObject e JsonArray sono i tipi restituiti da tutti i metodi delle API
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.stage.Stage;

// ─────────────────────────────────────────────────────────────────────────────
// COME ESEGUIRE QUESTA APP
//
//   mvn javafx:run@card-test
//
// L'esecuzione @card-test è configurata in pom.xml con il mainClass
// com.example.ProductCardTestApp, separata dall'app principale (App.java).
// ─────────────────────────────────────────────────────────────────────────────
public class ProductCardTestApp extends Application {

    // ─────────────────────────────────────────────────────────────────────────
    // CREDENZIALI DI TEST
    //
    // In produzione queste vengono inserite dall'utente (form di login).
    // AuthService.login() le invia a POST /auth/login e salva automaticamente
    // il token JWT in SessionManager — da quel momento tutte le chiamate API
    // successive usano il token senza doverlo passare manualmente.
    // ─────────────────────────────────────────────────────────────────────────
    private static final String TEST_EMAIL    = "admin@accaicedtea.it";
    private static final String TEST_PASSWORD = "admin123";

    private FlowPane cardsPane;
    private Label    statusLbl;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        // Etichetta di stato che aggiorniamo man mano (login → caricamento → ok/errore)
        statusLbl = new Label("Login in corso...");
        statusLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #f39c12;");

        Label title = new Label("ProductCard — dati reali da v_products_full");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #5b9cf5;");

        HBox header = new HBox(16, title, statusLbl);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 20, 10, 20));
        header.setStyle("-fx-background-color: #14141e;");

        // FlowPane: dispone le card in righe, andando a capo automaticamente
        cardsPane = new FlowPane(16, 16);
        cardsPane.setAlignment(Pos.TOP_LEFT);
        cardsPane.setPadding(new Insets(20));

        ScrollPane scroll = new ScrollPane(cardsPane);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #18181e; -fx-background: #18181e;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox root = new VBox(header, scroll);
        root.setStyle("-fx-background-color: #18181e;");

        Scene scene = new Scene(root, 1100, 700);
        stage.setTitle("ProductCard Test — TOTTEM");
        // Forza la chiusura del processo JVM quando si chiude la finestra
        stage.setOnCloseRequest(e -> { Platform.exit(); System.exit(0); });
        stage.setScene(scene);
        stage.show();

        // Avvia il caricamento in background (mai bloccare il thread JavaFX!)
        loadProducts();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PATTERN STANDARD PER CHIAMARE LE API
    //
    // REGOLA FONDAMENTALE: le chiamate HTTP sono bloccanti → devono girare
    // in un thread separato (new Thread(...).start()).
    // Per aggiornare la UI dal thread di rete si usa Platform.runLater(...)
    // che esegue il codice sul JavaFX Application Thread (l'unico autorizzato
    // a toccare i nodi della scena).
    //
    // Schema da replicare ovunque:
    //
    //   new Thread(() -> {
    //       try {
    //           // 1. [una volta sola] login → salva token in SessionManager
    //           AuthService.login(email, password);
    //
    //           // 2. chiamata API → restituisce sempre JsonObject
    //           JsonObject res = QualcheService.getAll();
    //
    //           // 3. aggiorna la UI sul thread corretto
    //           Platform.runLater(() -> { /* usa res */ });
    //
    //       } catch (Exception ex) {
    //           Platform.runLater(() -> { /* mostra errore */ });
    //       }
    //   }).start();
    // ─────────────────────────────────────────────────────────────────────────
    private void loadProducts() {
        new Thread(() -> {
            try {
                // ── STEP 1: autenticazione ────────────────────────────────────
                // login() fa POST /auth/login con email+password.
                // Se le credenziali sono corrette, il token JWT viene salvato
                // automaticamente in SessionManager.getToken() e allegato a
                // tutte le chiamate successive come header Authorization: Bearer <token>.
                AuthService.login(TEST_EMAIL, TEST_PASSWORD);
                Platform.runLater(() -> statusLbl.setText("Caricamento prodotti..."));

                // ── STEP 2: recupera i dati ───────────────────────────────────
                // ViewsService.getProductsFull() chiama GET /views/v_products_full
                // e restituisce il JsonObject grezzo: { status, data: [...], timestamp }
                //
                // Altri endpoint disponibili in ViewsService:
                //   ViewsService.getCategoriesWithCount()      → categorie + n° prodotti
                //   ViewsService.getIngredientsWithAllergens() → ingredienti con allergeni
                //   ViewsService.getProductsWithPromotions()   → prodotti + promozioni attive
                //   ViewsService.getAllergensUsage()            → (pubblico) uso allergeni
                //   ViewsService.getActivePromotions()         → (pubblico) promozioni attive
                JsonObject response = ViewsService.getProductsFull();

                // ── STEP 3: valida la risposta ────────────────────────────────
                // Il backend risponde sempre con { status: "success"|"error", data: ... }
                // Prima di usare i dati verifica che "data" esista e sia un array.
                if (!response.has("data") || !response.get("data").isJsonArray()) {
                    Platform.runLater(() -> statusLbl.setText("Risposta non valida"));
                    return;
                }

                JsonArray data = response.getAsJsonArray("data");

                // ── STEP 4: costruisci la UI sul JavaFX thread ────────────────
                Platform.runLater(() -> {
                    cardsPane.getChildren().clear();
                    int count = 0;

                    for (var el : data) {
                        if (!el.isJsonObject()) continue;

                        // Product wrappa il JsonObject e:
                        //   • espone i campi tipizzati (name, price, imageUrl…)
                        //   • parsa allergensRaw → List<Allergen>
                        //   • parsa ingredientsRaw → List<Ingredient>
                        //   • costruisce l'URL immagine completo con buildImageUrl()
                        Product p = new Product(el.getAsJsonObject());

                        // ProductCard accetta nome, categoria e URL immagine nel costruttore.
                        // I metodi "with*" sono facoltativi: aggiungono sezioni alla card
                        // solo se i dati sono presenti (lista vuota = sezione nascosta).
                        ProductCard card = new ProductCard(p.name, p.categoryName, p.imageUrl)
                                .withPrice(p.getPriceFormatted())       // es. "€ 3.50"
                                .withAllergens(p.allergens)             // badge arancioni
                                .withIngredients(p.ingredients);        // testo inline

                        cardsPane.getChildren().add(card);
                        count++;
                    }

                    // Aggiorna etichetta di stato con il conteggio finale
                    statusLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #27ae60;");
                    statusLbl.setText("✔ " + count + " prodotti caricati");
                });

            } catch (Exception ex) {
                // Qualsiasi eccezione (rete, credenziali errate, JSON malformato)
                // viene catturata qui e mostrata nella UI senza far crashare l'app.
                Platform.runLater(() -> {
                    statusLbl.setText("✘ Errore: " + ex.getMessage());
                    statusLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #e74c3c;");
                });
            }
        }).start();
    }
}