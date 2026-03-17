package com.example.model;

import java.util.Map;

/**
 * Stringhe UI multilingua.
 * Lingue supportate: it, en, de, fr, ar
 *
 * Uso:  I18n.t("add_to_cart")   →  "Aggiungi al carrello"
 *       I18n.setLang("en")
 *       I18n.t("add_to_cart")   →  "Add to cart"
 */
public class I18n {

    private static String lang = "it";

    public static void setLang(String l) { lang = l; }
    public static String getLang()       { return lang; }

    private static final Map<String, Map<String, String>> STRINGS = Map.of(

        "it", Map.ofEntries(
            Map.entry("welcome_title",    "Benvenuto"),
            Map.entry("welcome_subtitle", "Tocca per iniziare"),
            Map.entry("choose_lang",      "Scegli la lingua"),
            Map.entry("start",            "Inizia l'ordine"),
            Map.entry("menu_title",       "Il Nostro Menu"),
            Map.entry("add_to_cart",      "Aggiungi al carrello"),
            Map.entry("added",            "Aggiunto!"),
            Map.entry("cart",             "Carrello"),
            Map.entry("cart_empty",       "Il carrello è vuoto"),
            Map.entry("total",            "Totale"),
            Map.entry("proceed",          "Procedi al pagamento"),
            Map.entry("proceed_hint",     "Premi per confermare e pagare"),
            Map.entry("payment_title",    "Come vuoi pagare?"),
            Map.entry("cash",             "Contanti"),
            Map.entry("cash_sub",         "Paga alla cassa"),
            Map.entry("card",             "Carta"),
            Map.entry("card_sub",         "Bancomat / Credito"),
            Map.entry("confirm_title",    "Ordine confermato!"),
            Map.entry("confirm_sub",      "Il tuo numero è"),
            Map.entry("confirm_msg",      "Ritira il tuo ordine alla cassa.\nGrazie!"),
            Map.entry("new_order",        "Nuovo ordine"),
            Map.entry("back",             "Indietro"),
            Map.entry("allergens",        "Allergeni"),
            Map.entry("ingredients",      "Ingredienti"),
            Map.entry("qty",              "Qtà"),
            Map.entry("remove",           "Rimuovi"),
            Map.entry("allergen_warning", "Attenzione: allergeni nel tuo ordine"),
            Map.entry("order_number",    "Numero ordine")
        ),

        "en", Map.ofEntries(
            Map.entry("welcome_title",    "Welcome"),
            Map.entry("welcome_subtitle", "Tap to start"),
            Map.entry("choose_lang",      "Choose your language"),
            Map.entry("start",            "Start order"),
            Map.entry("menu_title",       "Our Menu"),
            Map.entry("add_to_cart",      "Add to cart"),
            Map.entry("added",            "Added!"),
            Map.entry("cart",             "Cart"),
            Map.entry("cart_empty",       "Your cart is empty"),
            Map.entry("total",            "Total"),
            Map.entry("proceed",          "Proceed to payment"),
            Map.entry("proceed_hint",     "Tap to confirm and pay"),
            Map.entry("payment_title",    "How do you want to pay?"),
            Map.entry("cash",             "Cash"),
            Map.entry("cash_sub",         "Pay at the counter"),
            Map.entry("card",             "Card"),
            Map.entry("card_sub",         "Debit / Credit"),
            Map.entry("confirm_title",    "Order confirmed!"),
            Map.entry("confirm_sub",      "Your number is"),
            Map.entry("confirm_msg",      "Pick up your order at the counter.\nThank you!"),
            Map.entry("new_order",        "New order"),
            Map.entry("back",             "Back"),
            Map.entry("allergens",        "Allergens"),
            Map.entry("ingredients",      "Ingredients"),
            Map.entry("qty",              "Qty"),
            Map.entry("remove",           "Remove"),
            Map.entry("allergen_warning", "Warning: allergens in your order"),
            Map.entry("order_number",    "Order number")
        ),

        "de", Map.ofEntries(
            Map.entry("welcome_title",    "Willkommen"),
            Map.entry("welcome_subtitle", "Tippen zum Starten"),
            Map.entry("choose_lang",      "Sprache wählen"),
            Map.entry("start",            "Bestellung starten"),
            Map.entry("menu_title",       "Unsere Speisekarte"),
            Map.entry("add_to_cart",      "In den Warenkorb"),
            Map.entry("added",            "Hinzugefügt!"),
            Map.entry("cart",             "Warenkorb"),
            Map.entry("cart_empty",       "Warenkorb ist leer"),
            Map.entry("total",            "Gesamt"),
            Map.entry("proceed",          "Zur Kasse"),
            Map.entry("proceed_hint",     "Tippen, um zu bestätigen und zu bezahlen"),
            Map.entry("payment_title",    "Wie möchten Sie bezahlen?"),
            Map.entry("cash",             "Bar"),
            Map.entry("cash_sub",         "An der Kasse zahlen"),
            Map.entry("card",             "Karte"),
            Map.entry("card_sub",         "EC / Kreditkarte"),
            Map.entry("confirm_title",    "Bestellung bestätigt!"),
            Map.entry("confirm_sub",      "Ihre Nummer ist"),
            Map.entry("confirm_msg",      "Holen Sie Ihre Bestellung an der Kasse ab.\nDanke!"),
            Map.entry("new_order",        "Neue Bestellung"),
            Map.entry("back",             "Zurück"),
            Map.entry("allergens",        "Allergene"),
            Map.entry("ingredients",      "Zutaten"),
            Map.entry("qty",              "Mge"),
            Map.entry("remove",           "Entfernen"),
            Map.entry("allergen_warning", "Achtung: Allergene in Ihrer Bestellung"),
            Map.entry("order_number",    "Bestellnummer")
        ),

        "fr", Map.ofEntries(
            Map.entry("welcome_title",    "Bienvenue"),
            Map.entry("welcome_subtitle", "Appuyez pour commencer"),
            Map.entry("choose_lang",      "Choisissez votre langue"),
            Map.entry("start",            "Commencer la commande"),
            Map.entry("menu_title",       "Notre Menu"),
            Map.entry("add_to_cart",      "Ajouter au panier"),
            Map.entry("added",            "Ajouté!"),
            Map.entry("cart",             "Panier"),
            Map.entry("cart_empty",       "Votre panier est vide"),
            Map.entry("total",            "Total"),
            Map.entry("proceed",          "Procéder au paiement"),
            Map.entry("proceed_hint",     "Appuyez pour confirmer et payer"),
            Map.entry("payment_title",    "Comment voulez-vous payer?"),
            Map.entry("cash",             "Espèces"),
            Map.entry("cash_sub",         "Payer à la caisse"),
            Map.entry("card",             "Carte"),
            Map.entry("card_sub",         "Débit / Crédit"),
            Map.entry("confirm_title",    "Commande confirmée!"),
            Map.entry("confirm_sub",      "Votre numéro est"),
            Map.entry("confirm_msg",      "Récupérez votre commande à la caisse.\nMerci!"),
            Map.entry("new_order",        "Nouvelle commande"),
            Map.entry("back",             "Retour"),
            Map.entry("allergens",        "Allergènes"),
            Map.entry("ingredients",      "Ingrédients"),
            Map.entry("qty",              "Qté"),
            Map.entry("remove",           "Supprimer"),
            Map.entry("allergen_warning", "Attention: allergènes dans votre commande"),
            Map.entry("order_number",    "Numéro de commande")
        ),

        "ar", Map.ofEntries(
            Map.entry("welcome_title",    "مرحباً"),
            Map.entry("welcome_subtitle", "المس للبدء"),
            Map.entry("choose_lang",      "اختر لغتك"),
            Map.entry("start",            "ابدأ الطلب"),
            Map.entry("menu_title",       "قائمتنا"),
            Map.entry("add_to_cart",      "أضف إلى السلة"),
            Map.entry("added",            "تمت الإضافة!"),
            Map.entry("cart",             "السلة"),
            Map.entry("cart_empty",       "السلة فارغة"),
            Map.entry("total",            "الإجمالي"),
            Map.entry("proceed",          "المتابعة للدفع"),
            Map.entry("proceed_hint",     "اضغط للتأكيد والدفع"),
            Map.entry("payment_title",    "كيف تريد الدفع؟"),
            Map.entry("cash",             "نقداً"),
            Map.entry("cash_sub",         "الدفع عند الكاشير"),
            Map.entry("card",             "بطاقة"),
            Map.entry("card_sub",         "بطاقة الخصم / الائتمان"),
            Map.entry("confirm_title",    "تم تأكيد الطلب!"),
            Map.entry("confirm_sub",      "رقمك هو"),
            Map.entry("confirm_msg",      "استلم طلبك من الكاشير.\nشكراً!"),
            Map.entry("new_order",        "طلب جديد"),
            Map.entry("back",             "رجوع"),
            Map.entry("allergens",        "مسببات الحساسية"),
            Map.entry("ingredients",      "المكونات"),
            Map.entry("qty",              "الكمية"),
            Map.entry("remove",           "إزالة"),
            Map.entry("allergen_warning", "تحذير: مواد مسببة للحساسية في طلبك"),
            Map.entry("order_number",    "رقم الطلب")
        )
    );


    /**
     * Merge traduzioni caricate da AI/cache in quelle hardcoded.
     * Le chiavi AI sovrascrivono quelle di fallback.
     */
    public static void mergeTranslations(java.util.Map<String, java.util.Map<String, String>> external) {
        // STRINGS è immutabile (Map.of), usa una mappa mutabile per il merge
        for (var langEntry : external.entrySet()) {
            String langKey = langEntry.getKey();
            java.util.Map<String, String> existing = STRINGS.get(langKey);
            java.util.Map<String, String> override  = langEntry.getValue();
            if (existing == null || override == null) continue;
            // Crea una nuova mappa mutabile con fallback + override
            java.util.Map<String, String> merged = new java.util.HashMap<>(existing);
            merged.putAll(override);
            OVERRIDE_STRINGS.put(langKey, merged);
        }
    }

    // Mappa di override (popolata da mergeTranslations)
    private static final java.util.Map<String, java.util.Map<String, String>> OVERRIDE_STRINGS
            = new java.util.HashMap<>();

    /** Restituisce la stringa tradotta nella lingua corrente (AI override se disponibile). */
    public static String t(String key) {
        // Prima controlla le traduzioni AI
        Map<String, String> overrideMap = OVERRIDE_STRINGS.get(lang);
        if (overrideMap != null && overrideMap.containsKey(key)) return overrideMap.get(key);
        // Fallback hardcoded
        Map<String, String> map = STRINGS.get(lang);
        if (map == null) map = STRINGS.get("it");
        return map.getOrDefault(key, key);
    }
}
