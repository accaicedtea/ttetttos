package com.example;

import com.util.Animations;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShopPageController {

    @FXML private ScrollPane productsScroll;
    @FXML private ScrollPane categoriesScroll;
    @FXML private TilePane   productsPane;
    @FXML private VBox       categoriesPane;
    @FXML private Label      categoryTitleLabel;

    // ── Data model ───────────────────────────────────────────────
    record Product(String emoji, String name, String description, String price) {}

    private final Map<String, List<Product>> catalog = new LinkedHashMap<>();
    private String selectedCategory = null;

    @FXML
    public void initialize() {
        buildCatalog();
        buildCategoryButtons();
        selectCategory(catalog.keySet().iterator().next());
        Animations.inertiaScroll(productsScroll);
        Animations.inertiaScroll(categoriesScroll);

        // Calcola dimensioni tile in base al viewport (larghezza e altezza)
        productsScroll.viewportBoundsProperty().addListener((obs, old, bounds) -> {
            double w      = bounds.getWidth();
            double h      = bounds.getHeight();
            double hgap   = 20, vgap = 20, pad = 24 * 2;
            double tileW  = (w - pad - hgap * 3) / 4.0;
            double tileH  = (h - pad - vgap) / 2.5;
            if (tileW > 80)  productsPane.setPrefTileWidth(tileW);
            if (tileH > 80)  productsPane.setPrefTileHeight(tileH);
        });
    }

    // ─────────────────────────────────────────────────────────────
    //  DATA
    // ─────────────────────────────────────────────────────────────
    private void buildCatalog() {
        catalog.put("🍕 Pizze", List.of(
            new Product("🍕", "Margherita",           "Pomodoro, mozzarella, basilico",              "€ 7.50"),
            new Product("🍕", "Diavola",               "Pomodoro, mozzarella, salame piccante",       "€ 9.00"),
            new Product("🍕", "Quattro Formaggi",     "Mozzarella, gorgonzola, asiago, brie",        "€ 10.50"),
            new Product("🍕", "Capricciosa",          "Prosciutto, funghi, carciofi, olive",         "€ 11.00"),
            new Product("🍕", "Bufalina",              "Pomodorini, bufala DOP, rucola",              "€ 12.00"),
            new Product("🍕", "Tonno e Cipolla",      "Tonno, cipolla, origano",                     "€ 9.50"),
            new Product("🍕", "Vegana",                "Verdure grigliate, pesto di basilico",        "€ 10.00"),
            new Product("🍕", "Calzone",               "Ricotta, salame, mozzarella (chiuso)",        "€ 11.50"),
            new Product("🍕", "Norma",                 "Melanzane fritte, pomodoro, ricotta salata",  "€ 10.00"),
            new Product("🍕", "Marinara",              "Pomodoro, aglio, origano, EVOO",              "€ 6.50"),
            new Product("🍕", "Prosciutto e Funghi",  "Prosciutto cotto, funghi champignon",         "€ 10.00"),
            new Product("🍕", "Salsiccia e Friarielli","Salsiccia napoletana, friarielli",           "€ 12.00"),
            new Product("🍕", "Speck e Brie",         "Speck alto adige, brie fuso, rucola",         "€ 13.00"),
            new Product("🍕", "Ortolana",              "Zucchine, peperoni, melanzane, pomodoro",     "€ 10.50"),
            new Product("🍕", "Nduja",                 "Nduja calabrese, pomodoro, fior di latte",   "€ 11.50"),
            new Product("🍕", "Patate e Rosmarino",   "Patate a fette, rosmarino, scamorza",         "€ 10.00"),
            new Product("🍕", "Bianca al Tartufo",    "Crema di tartufo, mozzarella, funghi",        "€ 15.00"),
            new Product("🍕", "Bresaola e Grana",     "Bresaola, grana, rucola, limone",             "€ 13.50"),
            new Product("🍕", "Wurstel e Patatine",   "Wurstel, patatine fritte, ketchup",           "€ 9.00"),
            new Product("🍕", "Doppio Impasto",       "Ripiena: prosciutto, ricotta, mozzarella",    "€ 14.00")
        ));
        catalog.put("🥪 Panini", List.of(
            new Product("🥪", "Classico",              "Prosciutto cotto, formaggio",                 "€ 5.00"),
            new Product("🥪", "Crudo e Fichi",        "Prosciutto crudo, fichi, rucola",             "€ 7.00"),
            new Product("🥪", "Pollo Grill",           "Pollo alla griglia, lattuga, maionese",       "€ 6.50"),
            new Product("🥪", "Veggie",                "Avocado, pomodoro, hummus",                   "€ 6.00"),
            new Product("🥪", "Club Sandwich",        "Bacon, uovo, lattuga, tomate",                "€ 8.00"),
            new Product("🥪", "Mozzarella Pesto",     "Mozzarella, pesto, pomodori secchi",          "€ 6.50"),
            new Product("🥪", "Porchetta",             "Porchetta romana, salsa verde, cipolla",      "€ 7.50"),
            new Product("🥪", "Lampredotto",           "Quinto quarto fiorentino, salsa verde",       "€ 6.00"),
            new Product("🥪", "Hamburger Classico",   "Manzo 180g, lattuga, pomodoro, cipolla",      "€ 9.00"),
            new Product("🥪", "Hamburger Bacon",      "Manzo 180g, bacon croccante, cheddar",        "€ 11.00"),
            new Product("🥪", "Hamburger Vegano",     "Patty di legumi, avocado, cipolla caramellata","€ 10.00"),
            new Product("🥪", "BLT",                   "Bacon, lattuga, pomodoro, maionese",          "€ 7.00"),
            new Product("🥪", "Tonno e Olive",        "Tonno, olive, pomodoro, rucola",              "€ 6.50"),
            new Product("🥪", "Caprese",               "Mozzarella, pomodoro, basilico, EVOO",        "€ 6.00"),
            new Product("🥪", "Speck e Brie",         "Speck, brie, miele di acacia",                "€ 8.00"),
            new Product("🥪", "Pulled Pork",           "Maiale sfilacciato BBQ, coleslaw",            "€ 9.50"),
            new Product("🥪", "Falafel",               "Falafel, tzatziki, insalatina, pomodori",     "€ 8.00"),
            new Product("🥪", "Salmone Affumicato",   "Salmone, cream cheese, capperi, limone",      "€ 9.00")
        ));
        catalog.put("🍝 Primi", List.of(
            new Product("🍝", "Carbonara",             "Guanciale, uova, pecorino, pepe",             "€ 12.00"),
            new Product("🍝", "Amatriciana",           "Guanciale, pomodoro, pecorino",               "€ 11.50"),
            new Product("🍝", "Cacio e Pepe",         "Pecorino, pepe nero",                         "€ 10.00"),
            new Product("🍝", "Lasagne",               "Ragù di carne, besciamella",                  "€ 13.00"),
            new Product("🍝", "Risotto Funghi",       "Porcini, parmigiano, burro",                  "€ 13.50"),
            new Product("🍝", "Gnocchi al Ragù",      "Gnocchi di patate, ragù bolognese",           "€ 12.50"),
            new Product("🍝", "Spaghetti Pomodoro",   "Pomodoro San Marzano, basilico, EVOO",        "€ 9.00"),
            new Product("🍝", "Penne all'Arrabbiata", "Pomodoro, aglio, peperoncino",                "€ 9.50"),
            new Product("🍝", "Tagliatelle al Ragù",  "Ragù bolognese DOP, parmigiano",              "€ 13.00"),
            new Product("🍝", "Orecchiette Cime",     "Cime di rapa, salsiccia, aglio",              "€ 12.00"),
            new Product("🍝", "Pappardelle Cinghiale","Ragù di cinghiale, rosmarino",                "€ 15.00"),
            new Product("🍝", "Risotto al Nero",      "Nero di seppia, gamberi, prezzemolo",         "€ 16.00"),
            new Product("🍝", "Rigatoni alla Gricia", "Guanciale, pecorino, pepe",                   "€ 11.00"),
            new Product("🍝", "Linguine Vongole",     "Vongole veraci, aglio, prezzemolo, vino",     "€ 15.00"),
            new Product("🍝", "Trofie al Pesto",      "Pesto genovese DOP, patate, fagiolini",       "€ 12.00"),
            new Product("🍝", "Minestrone",            "Verdure di stagione, legumi, pasta",          "€ 9.00"),
            new Product("🍝", "Zuppa di Pesce",       "Brodo di pesce, crostacei, crostini",         "€ 18.00"),
            new Product("🍝", "Tortellini Panna",     "Tortellini emiliani, panna, prosciutto",      "€ 13.00"),
            new Product("🍝", "Paccheri Frutti di Mare","Cozze, vongole, gamberi, pomodorini",      "€ 17.00"),
            new Product("🍝", "Gnocchi Sorrentini",   "Pomodoro, mozzarella, basilico al forno",     "€ 12.50")
        ));
        catalog.put("🥩 Secondi", List.of(
            new Product("🥩", "Tagliata",              "Manzo, rucola, grana, limone",                "€ 18.00"),
            new Product("🥩", "Pollo Arrosto",        "Pollo intero al forno con patate",           "€ 14.00"),
            new Product("🥩", "Salmone Grigliato",    "Salmone al limone e erbe",                    "€ 16.00"),
            new Product("🥩", "Bistecca T-Bone",      "250g, sale grosso, rosmarino",                "€ 24.00"),
            new Product("🥩", "Cotoletta",             "Vitello panato, insalatina",                  "€ 15.00"),
            new Product("🥩", "Filetto al Pepe Verde","Filetto di manzo, salsa pepe verde",          "€ 26.00"),
            new Product("🥩", "Entrecôte",             "200g, burro alle erbe, patatine",             "€ 22.00"),
            new Product("🥩", "Scottadito",            "Costolette di agnello alla brace",            "€ 20.00"),
            new Product("🥩", "Pollo alla Cacciatora","Pomodoro, olive, capperi, vino bianco",       "€ 14.00"),
            new Product("🥩", "Branzino al Sale",     "Branzino 400g, sale grosso, limone",          "€ 22.00"),
            new Product("🥩", "Calamari Fritti",      "Calamari freschissimi, salsa tartara",        "€ 16.00"),
            new Product("🥩", "Gamberi alla Busara",  "Gamberi, pomodoro, aglio, vino bianco",       "€ 20.00"),
            new Product("🥩", "Ossobuco",              "Ossobuco di vitello, gremolata, risotto",     "€ 22.00"),
            new Product("🥩", "Polpette al Sugo",     "Polpette di manzo, sugo di pomodoro",         "€ 13.00"),
            new Product("🥩", "Stinco al Forno",      "Stinco di maiale, patate, rosmarino",         "€ 18.00"),
            new Product("🥩", "Merluzzo al Vapore",   "Merluzzo, capperi, olive, pomodorini",        "€ 17.00")
        ));
        catalog.put("🥗 Insalate", List.of(
            new Product("🥗", "Caesar",                "Lattuga, crouton, parmigiano, Caesar",        "€ 9.00"),
            new Product("🥗", "Greca",                 "Feta, olive, pomodoro, cetriolo",             "€ 9.50"),
            new Product("🥗", "Caprese",               "Mozzarella, pomodoro, basilico, EVOO",        "€ 10.00"),
            new Product("🥗", "Mista di Stagione",    "Verdure fresche, dressing a scelta",          "€ 7.00"),
            new Product("🥗", "Nizzarda",              "Tonno, uova, fagiolini, olive nere",          "€ 11.00"),
            new Product("🥗", "Pollo e Avocado",      "Pollo grigliato, avocado, mais, rucola",      "€ 12.00"),
            new Product("🥗", "Spinaci e Noci",       "Spinacino, noci, grana, aceto di mele",       "€ 10.00"),
            new Product("🥗", "Panzanella",            "Pane toscano, pomodoro, basilico, cipolla",   "€ 9.00"),
            new Product("🥗", "Cous Cous Verdure",    "Cous cous, verdure grigliate, spezie",        "€ 10.50"),
            new Product("🥗", "Bulgur e Feta",        "Bulgur, feta, melagrana, rucola",             "€ 11.00"),
            new Product("🥗", "Salmone e Sesamo",     "Salmone crudo, sesamo, salsa di soia",        "€ 13.00"),
            new Product("🥗", "Insalata di Lenticchie","Lenticchie, pomodori secchi, rucola",        "€ 9.50")
        ));
        catalog.put("🍺 Bevande", List.of(
            new Product("🍺", "Birra alla Spina",      "0.3 L / 0.5 L chiara o rossa",               "€ 3.50"),
            new Product("🍺", "Birra Artigianale IPA", "Luppolata, amara, 0.4 L",                     "€ 5.50"),
            new Product("🍺", "Birra Artigianale Stout","Scura, caffè e cioccolato, 0.4 L",           "€ 5.50"),
            new Product("🍺", "Birra Weizen",          "Frumento, banana, garofano, 0.5 L",           "€ 4.50"),
            new Product("🥤", "Coca-Cola",              "33 cl, ghiaccio e limone",                    "€ 2.50"),
            new Product("🥤", "Fanta Arancia",         "33 cl",                                       "€ 2.50"),
            new Product("🥤", "Sprite",                 "33 cl",                                       "€ 2.50"),
            new Product("🥤", "Tè Freddo Pesca",       "500 ml, fatto in casa",                       "€ 3.00"),
            new Product("🥤", "Limonata",               "Limoni freschi, zucchero, ghiaccio",          "€ 3.50"),
            new Product("💧", "Acqua Naturale",        "500 ml",                                      "€ 1.50"),
            new Product("💧", "Acqua Frizzante",       "500 ml",                                      "€ 1.50"),
            new Product("🍷", "Vino Rosso",            "Bicchiere, Montepulciano d'Abruzzo",          "€ 5.00"),
            new Product("🍷", "Vino Bianco",           "Bicchiere, Pinot Grigio",                     "€ 5.00"),
            new Product("🍷", "Prosecco",               "Bicchiere, DOC Treviso, 0.2 L",               "€ 5.50"),
            new Product("🍷", "Vino Rosato",           "Bicchiere, Cerasuolo d'Abruzzo",              "€ 5.00"),
            new Product("🍾", "Bottiglia Vino Rosso",  "750 ml, selezione del sommelier",             "€ 22.00"),
            new Product("🍾", "Bottiglia Prosecco",    "750 ml, DOC Treviso",                         "€ 18.00"),
            new Product("🍹", "Aperol Spritz",         "Prosecco, Aperol, soda",                      "€ 7.00"),
            new Product("🍹", "Hugo",                   "Prosecco, sciroppo sambuco, menta",           "€ 7.00"),
            new Product("🍹", "Negroni",                "Gin, vermouth rosso, Campari",                "€ 8.00"),
            new Product("🍹", "Mojito",                 "Rum, menta, lime, soda",                      "€ 8.00"),
            new Product("🍹", "Daiquiri Fragola",      "Rum, fragole, lime, sciroppo",                "€ 8.50"),
            new Product("🍹", "Cosmopolitan",           "Vodka, triple sec, lime, cranberry",          "€ 8.50"),
            new Product("🧃", "Succo ACE",              "200 ml",                                      "€ 2.00"),
            new Product("🧃", "Succo Pesca",            "200 ml",                                      "€ 2.00"),
            new Product("🧃", "Succo Albicocca",       "200 ml",                                      "€ 2.00")
        ));
        catalog.put("☕ Caffetteria", List.of(
            new Product("☕", "Espresso",               "Arabica 100%",                                "€ 1.00"),
            new Product("☕", "Espresso Doppio",        "Doppia dose di arabica",                      "€ 1.60"),
            new Product("☕", "Cappuccino",             "Latte montato, schiuma vellutata",            "€ 1.50"),
            new Product("☕", "Cappuccino di Soia",    "Latte vegetale di soia montato",              "€ 2.00"),
            new Product("🧋", "Latte Macchiato",       "Latte caldo, espresso",                       "€ 1.80"),
            new Product("🧋", "Latte Macchiato Freddo","Latte freddo, espresso, ghiaccio",            "€ 2.50"),
            new Product("🧋", "Flat White",             "Doppio espresso, latte vellutato",            "€ 3.00"),
            new Product("🧋", "Matcha Latte",           "Tè matcha, latte di avena, miele",            "€ 4.00"),
            new Product("🧋", "Frappuccino",            "Caffè, ghiaccio, latte, sciroppo vaniglia",   "€ 4.50"),
            new Product("🧊", "Caffè Freddo",          "Espresso sciroppo, ghiaccio",                 "€ 2.00"),
            new Product("🧊", "Cold Brew",              "Infusione a freddo 12h, ghiaccio",            "€ 3.50"),
            new Product("🍵", "Tè Verde",               "Sencha giapponese",                           "€ 2.00"),
            new Product("🍵", "Tè Nero",                "English Breakfast",                           "€ 2.00"),
            new Product("🍵", "Camomilla",              "Fiori di camomilla essiccati",                "€ 2.00"),
            new Product("🍵", "Tè Zenzero Limone",     "Zenzero fresco, limone, miele",               "€ 2.50"),
            new Product("🍵", "Rooibos",                "Rosso sudafricano, vaniglia",                 "€ 2.50"),
            new Product("🍫", "Cioccolata Calda",      "Fondente, al latte o bianca",                 "€ 3.00"),
            new Product("🍫", "Cioccolata Viennese",   "Con panna montata fresca",                    "€ 3.50"),
            new Product("☕", "Marocchino",             "Espresso, cacao, latte montato",              "€ 2.00"),
            new Product("☕", "Caffè Americano",       "Espresso allungato con acqua calda",           "€ 1.50")
        ));
        catalog.put("🍰 Dolci", List.of(
            new Product("🍰", "Tiramisù",               "Classico, ricetta della casa",                "€ 5.50"),
            new Product("🍮", "Panna Cotta",            "Con coulis di frutti di bosco",               "€ 5.00"),
            new Product("🍫", "Brownie",                "Cioccolato fondente, noci",                   "€ 4.50"),
            new Product("🍦", "Gelato Artigianale",    "2 gusti a scelta",                            "€ 3.50"),
            new Product("🥐", "Cannolo Siciliano",     "Ricotta, gocce di cioccolato",                "€ 4.00"),
            new Product("🍮", "Cheesecake",             "New York style, frutti rossi",                "€ 6.00"),
            new Product("🍮", "Crème Brûlée",          "Vaniglia Bourbon, zucchero caramellato",      "€ 6.50"),
            new Product("🧁", "Cupcake Cioccolato",    "Base cacao, frosting ganache",                "€ 3.00"),
            new Product("🧁", "Cupcake Red Velvet",    "Impasto rosso, frosting cream cheese",        "€ 3.50"),
            new Product("🥞", "Pancake Stack",          "3 pancake, sciroppo d'acero, burro",          "€ 6.00"),
            new Product("🥞", "Pancake Nutella",        "3 pancake, Nutella, banane, zucchero a velo", "€ 7.00"),
            new Product("🍓", "Fragole e Panna",       "Fragole fresche, panna montata, zucchero",    "€ 5.00"),
            new Product("🍨", "Coppa Gelato",           "3 palline, panna, cioccolato fondente",       "€ 5.50"),
            new Product("🍧", "Granita al Limone",     "Limoni siciliani, fatta in casa",             "€ 3.50"),
            new Product("🍩", "Donuts",                 "Glassata, cioccolato o zucchero",             "€ 2.50"),
            new Product("🥐", "Croissant Artigianale", "Burro, mandorle o crema pasticcera",          "€ 2.50"),
            new Product("🎂", "Torta della Casa",      "Cambia ogni giorno, chiedi al banco",          "€ 5.00"),
            new Product("🍰", "Millefoglie",            "Pasta sfoglia, crema chantilly",              "€ 5.50"),
            new Product("🍮", "Budino al Cioccolato",  "Fondente 70%, crema vaniglia",                "€ 4.50"),
            new Product("🥧", "Crostata Marmellata",   "Frolla, marmellata albicocca artigianale",    "€ 4.00")
        ));
        catalog.put("🍟 Contorni", List.of(
            new Product("🍟", "Patatine Fritte",        "Patate fresche, sale, rosmarino",             "€ 4.00"),
            new Product("🍟", "Patatine a Spicchi",    "Con paprika affumicata e aglio",              "€ 4.50"),
            new Product("🥦", "Verdure Grigliate",     "Zucchine, peperoni, melanzane, EVOO",         "€ 5.00"),
            new Product("🧅", "Cipolla Caramellata",   "Cipolla rossa, aceto balsamico, timo",        "€ 4.00"),
            new Product("🍄", "Funghi Trifolati",      "Porcini e champignon, aglio, prezzemolo",     "€ 5.50"),
            new Product("🥦", "Broccoli all'Aglio",    "Broccoli saltati, aglio, peperoncino",        "€ 4.50"),
            new Product("🫘", "Fagioli all'Uccelletto","Fagioli cannellini, pomodoro, salvia",        "€ 5.00"),
            new Product("🥗", "Insalata Semplice",     "Lattuga, pomodoro, carota, dressing",         "€ 4.00"),
            new Product("🍆", "Melanzane alla Parmigiana","Melanzane, pomodoro, mozzarella al forno", "€ 6.00"),
            new Product("🧀", "Formaggi Misti",        "Selezione di 4 formaggi, miele, noci",        "€ 10.00"),
            new Product("🥓", "Affettati Misti",       "Prosciutto crudo, salame, coppa, bresaola",   "€ 12.00"),
            new Product("🍞", "Bruschette (3 pz)",     "Pomodoro, aglio, basilico, EVOO",             "€ 5.00")
        ));
        catalog.put("🌮 Cucina del Mondo", List.of(
            new Product("🌮", "Tacos al Pastor",        "Maiale marinato, ananas, coriandolo",         "€ 9.00"),
            new Product("🌮", "Burrito Manzo",          "Manzo, riso, fagioli, guacamole",             "€ 11.00"),
            new Product("🌮", "Nachos con Guacamole",  "Tortilla chips, guacamole, jalapeño",         "€ 8.00"),
            new Product("🍜", "Ramen Tonkotsu",         "Brodo di maiale, noodles, uovo, nori",        "€ 14.00"),
            new Product("🍜", "Pad Thai",               "Noodles riso, gamberi, arachidi, lime",       "€ 13.00"),
            new Product("🍛", "Curry Pollo",            "Pollo, latte di cocco, riso basmati",         "€ 13.00"),
            new Product("🍛", "Curry Vegetariano",     "Ceci, spinaci, pomodoro, riso",               "€ 11.00"),
            new Product("🥟", "Gyoza (6 pz)",           "Ravioli giapponesi, maiale, cavolo",          "€ 8.00"),
            new Product("🥟", "Dim Sum (4 pz)",         "Vapore, gamberetti, erba cipollina",          "€ 9.00"),
            new Product("🫔", "Kebab nel Pane",         "Manzo-agnello, verdure, salsa yogurt",        "€ 8.00"),
            new Product("🧆", "Piatto Greco",           "Souvlaki, tzatziki, pita, insalata",          "€ 14.00"),
            new Product("🍱", "Bento Box",              "Riso, salmone teriyaki, edamame, miso",       "€ 16.00"),
            new Product("🌯", "Wrap Caesar",            "Pollo, lattuga, crouton, salsa Caesar",       "€ 9.00"),
            new Product("🌯", "Wrap Vegano",            "Hummus, falafel, verdure, tahini",            "€ 9.00")
        ));
    }

    // ─────────────────────────────────────────────────────────────
    //  CATEGORIES
    // ─────────────────────────────────────────────────────────────
    private void buildCategoryButtons() {
        catalog.forEach((category, products) -> {
            Label btn = new Label(category);
            btn.getStyleClass().add("cat-btn");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.setOnMouseClicked(e -> selectCategory(category));
            categoriesPane.getChildren().add(btn);
        });
    }

    private void selectCategory(String category) {
        selectedCategory = category;
        categoryTitleLabel.setText(category);

        // aggiorna stile bottoni
        categoriesPane.getChildren().forEach(n -> {
            n.getStyleClass().remove("cat-btn-active");
            if (n instanceof Label lbl && lbl.getText().equals(category)) {
                lbl.getStyleClass().add("cat-btn-active");
            }
        });

        // ricostruisci prodotti
        productsPane.getChildren().clear();
        productsScroll.setVvalue(0);

        List<Product> products = catalog.getOrDefault(category, List.of());
        for (Product p : products) {
            productsPane.getChildren().add(buildProductCard(p));
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  PRODUCT CARD
    // ─────────────────────────────────────────────────────────────
    private VBox buildProductCard(Product p) {
        Label emoji = new Label(p.emoji());
        emoji.getStyleClass().add("prod-emoji");

        Label name = new Label(p.name());
        name.getStyleClass().add("prod-name");
        name.setWrapText(true);

        Label desc = new Label(p.description());
        desc.getStyleClass().add("prod-desc");
        desc.setWrapText(true);

        Label price = new Label(p.price());
        price.getStyleClass().add("prod-price");

        VBox card = new VBox(10, emoji, name, desc, price);
        card.getStyleClass().add("prod-card");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(desc, Priority.ALWAYS);

        Animations.touchFeedback(card); // feedback touch pre-allocato, non si accumula

        return card;
    }
}
