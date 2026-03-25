package com.app.model;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Strings di I18n con fallback hardcoded e override da TranslationManager.
 */
public final class I18n {

    private static final Map<String, Map<String, String>> STRINGS = new ConcurrentHashMap<>();
    private static String lang = "it";

    static {
        STRINGS.put("it", buildItaliano());
        STRINGS.put("en", buildEnglish());
        STRINGS.put("de", buildDeutsch());
        STRINGS.put("fr", buildFrancais());
        STRINGS.put("ar", buildAr());
    }

    public static void setLang(String l) {
        if (l != null && STRINGS.containsKey(l)) {
            lang = l;
        } else {
            lang = "it";
        }
    }

    public static String getLang() {
        return lang;
    }

    public static String t(String key) {
        if (key == null) return "";
        Map<String, String> translation = STRINGS.getOrDefault(lang, STRINGS.get("it"));
        return translation.getOrDefault(key, key);
    }

    public static void mergeTranslations(Map<String, Map<String, String>> m) {
        if (m == null) return;
        for (Map.Entry<String, Map<String, String>> e : m.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            STRINGS.compute(e.getKey(), (k, existing) -> {
                if (existing == null) {
                    return new ConcurrentHashMap<>(e.getValue());
                }
                existing.putAll(e.getValue());
                return existing;
            });
        }
    }

    private static Map<String, String> buildItaliano() {
        Map<String, String> m = new HashMap<>();
        m.put("welcome_title", "Benvenuto");
        m.put("welcome_subtitle", "Tocca per iniziare");
        m.put("choose_lang", "Scegli la lingua");
        m.put("start", "Inizia l'ordine");
        m.put("add_to_cart", "Aggiungi al carrello");
        m.put("added", "Aggiunto!");
        m.put("cart", "Carrello");
        m.put("cart_empty", "Il carrello è vuoto");
        m.put("total", "Totale");
        m.put("proceed", "Procedi al pagamento");
        m.put("payment_title", "Come vuoi pagare?");
        m.put("cash", "Contanti");
        m.put("cash_sub", "Paga alla cassa");
        m.put("card", "Carta");
        m.put("card_sub", "Bancomat/Credito");
        m.put("confirm_title", "Ordine confermato!");
        m.put("confirm_sub", "Il tuo numero è");
        m.put("confirm_msg", "Ritira il tuo ordine alla cassa. Grazie!");
        m.put("back", "Indietro");
        m.put("allergens", "Allergeni");
        m.put("ingredients", "Ingredienti");
        m.put("qty", "Qta");
        m.put("remove", "Rimuovi");
        m.put("allergen_warning", "Attenzione: allergeni nel tuo ordine");
        m.put("order_number", "Numero ordine");
        m.put("proceed_hint", "Puoi tornare indietro quando vuoi");
        m.put("compose_kumpir", "Componi il tuo kumpir");
        m.put("loading_menu", "Caricamento menu...");
        m.put("loading", "Caricamento...");
        m.put("error", "Errore");
        m.put("search_ingredient", "Cerca ingrediente");
        m.put("no_allergens", "Escludi allergeni");
        m.put("no_gluten", "Senza glutine");
        m.put("no_lactose", "Senza lattosio");
        m.put("add_to_order", "da aggiungere");
        m.put("choose_at_least_one", "Seleziona almeno un ingrediente");
        m.put("kumpir_added", "Kumpir aggiunto al carrello");
        m.put("cancel", "Annulla");
        return m;
    }

    private static Map<String, String> buildEnglish() {
        Map<String, String> m = new HashMap<>();
        m.put("welcome_title", "Welcome");
        m.put("welcome_subtitle", "Tap to start");
        m.put("choose_lang", "Choose your language");
        m.put("start", "Start order");
        m.put("add_to_cart", "Add to cart");
        m.put("added", "Added!");
        m.put("cart", "Cart");
        m.put("cart_empty", "Cart is empty");
        m.put("total", "Total");
        m.put("proceed", "Proceed to payment");
        m.put("payment_title", "How do you want to pay?");
        m.put("cash", "Cash");
        m.put("cash_sub", "Pay at cashier");
        m.put("card", "Card");
        m.put("card_sub", "Debit/Credit");
        m.put("confirm_title", "Order confirmed!");
        m.put("confirm_sub", "Your number is");
        m.put("confirm_msg", "Pick up your order at cashier. Thanks!");
        m.put("back", "Back");
        m.put("allergens", "Allergens");
        m.put("ingredients", "Ingredients");
        m.put("qty", "Qty");
        m.put("remove", "Remove");
        m.put("allergen_warning", "Allergen warning in your order");
        m.put("order_number", "Order number");
        m.put("proceed_hint", "You can go back anytime");
        m.put("compose_kumpir", "Build your kumpir");
        m.put("loading_menu", "Loading menu...");
        m.put("loading", "Loading...");
        m.put("error", "Error");
        m.put("search_ingredient", "Search ingredient");
        m.put("no_allergens", "Exclude allergens");
        m.put("no_gluten", "Gluten-free");
        m.put("no_lactose", "Lactose-free");
        m.put("add_to_order", "to add to order");
        m.put("choose_at_least_one", "Please choose at least one ingredient");
        m.put("kumpir_added", "Kumpir added to cart");
        m.put("cancel", "Cancel");
        return m;
    }

    private static Map<String, String> buildDeutsch() {
        Map<String, String> m = new HashMap<>();
        m.put("welcome_title", "Willkommen");
        m.put("welcome_subtitle", "Tippen zum Start");
        m.put("choose_lang", "Wähle deine Sprache");
        m.put("start", "Bestellung starten");
        m.put("add_to_cart", "In den Warenkorb");
        m.put("added", "Hinzugefügt!");
        m.put("cart", "Warenkorb");
        m.put("cart_empty", "Warenkorb ist leer");
        m.put("total", "Gesamt");
        m.put("proceed", "Zur Zahlung");
        m.put("payment_title", "Wie möchten Sie bezahlen?");
        m.put("cash", "Bargeld");
        m.put("cash_sub", "Bezahlen an der Kasse");
        m.put("card", "Karte");
        m.put("card_sub", "Debit/Kredit");
        m.put("confirm_title", "Bestellung bestätigt!");
        m.put("confirm_sub", "Ihre Nummer ist");
        m.put("confirm_msg", "Bitte holen Sie Ihre Bestellung an der Kasse ab. Danke!");
        m.put("back", "Zurück");
        m.put("allergens", "Allergene");
        m.put("ingredients", "Zutaten");
        m.put("qty", "Menge");
        m.put("remove", "Entfernen");
        m.put("allergen_warning", "Allergene in Ihrer Bestellung");
        m.put("order_number", "Bestellnummer");
        m.put("proceed_hint", "Sie können jederzeit zurück");
        m.put("compose_kumpir", "Erstelle deinen Kumpir");
        m.put("loading_menu", "Menü wird geladen...");
        m.put("loading", "Wird geladen...");
        m.put("error", "Fehler");
        m.put("search_ingredient", "Zutat suchen");
        m.put("no_allergens", "Allergene ausschließen");
        m.put("no_gluten", "Glutenfrei");
        m.put("no_lactose", "Laktosefrei");
        m.put("add_to_order", "zum Bestellen");
        m.put("choose_at_least_one", "Wähle mindestens eine Zutat");
        m.put("kumpir_added", "Kumpir zum Warenkorb hinzugefügt");
        m.put("cancel", "Abbrechen");
        return m;
    }

    private static Map<String, String> buildFrancais() {
        Map<String, String> m = new HashMap<>();
        m.put("welcome_title", "Bienvenue");
        m.put("welcome_subtitle", "Touchez pour commencer");
        m.put("choose_lang", "Choisissez votre langue");
        m.put("start", "Commencer la commande");
        m.put("add_to_cart", "Ajouter au panier");
        m.put("added", "Ajouté!");
        m.put("cart", "Panier");
        m.put("cart_empty", "Le panier est vide");
        m.put("total", "Total");
        m.put("proceed", "Procéder au paiement");
        m.put("payment_title", "Comment voulez-vous payer?");
        m.put("cash", "Espèces");
        m.put("cash_sub", "Payer à la caisse");
        m.put("card", "Carte");
        m.put("card_sub", "Débit/Crédit");
        m.put("confirm_title", "Commande confirmée!");
        m.put("confirm_sub", "Votre numéro est");
        m.put("confirm_msg", "Récupérez votre commande à la caisse. Merci!");
        m.put("back", "Retour");
        m.put("allergens", "Allergènes");
        m.put("ingredients", "Ingrédients");
        m.put("qty", "Qté");
        m.put("remove", "Supprimer");
        m.put("allergen_warning", "Attention aux allergènes dans votre commande");
        m.put("order_number", "Numéro de commande");
        m.put("proceed_hint", "Vous pouvez revenir en arrière à tout moment");
        m.put("compose_kumpir", "Composez votre kumpir");
        m.put("loading_menu", "Chargement du menu...");
        m.put("loading", "Chargement...");
        m.put("error", "Erreur");
        m.put("search_ingredient", "Rechercher un ingrédient");
        m.put("no_allergens", "Exclure les allergènes");
        m.put("no_gluten", "Sans gluten");
        m.put("no_lactose", "Sans lactose");
        m.put("add_to_order", "à ajouter à la commande");
        m.put("choose_at_least_one", "Veuillez choisir au moins un ingrédient");
        m.put("kumpir_added", "Kumpir ajouté au panier");
        m.put("cancel", "Annuler");
        return m;
    }

    private static Map<String, String> buildAr() {
        Map<String, String> m = new HashMap<>();
        m.put("welcome_title", "مرحبا");
        m.put("welcome_subtitle", "اضغط للبدء");
        m.put("choose_lang", "اختر لغتك");
        m.put("start", "ابدأ الطلب");
        m.put("add_to_cart", "أضف إلى السلة");
        m.put("added", "تم الإضافة!");
        m.put("cart", "السلة");
        m.put("cart_empty", "السلة فارغة");
        m.put("total", "المجموع");
        m.put("proceed", "المتابعة للدفع");
        m.put("payment_title", "كيف تريد الدفع؟");
        m.put("cash", "نقداً");
        m.put("cash_sub", "الدفع عند الصندوق");
        m.put("card", "بطاقة");
        m.put("card_sub", "بطاقة ائتمان/مدى");
        m.put("confirm_title", "تم تأكيد الطلب!");
        m.put("confirm_sub", "رقمك هو");
        m.put("confirm_msg", "استلم طلبك من عند الصندوق. شكراً!");
        m.put("back", "رجوع");
        m.put("allergens", "مسببات الحساسية");
        m.put("ingredients", "المكونات");
        m.put("qty", "الكمية");
        m.put("remove", "إزالة");
        m.put("allergen_warning", "تحذير: مسببات حساسية في طلبك");
        m.put("order_number", "رقم الطلب");
        m.put("proceed_hint", "يمكنك العودة في أي وقت");
        m.put("compose_kumpir", "أنشئ kumpir خاصتك");
        m.put("loading_menu", "جارٍ تحميل القائمة...");
        m.put("loading", "جاري التحميل...");
        m.put("error", "خطأ");
        m.put("search_ingredient", "ابحث عن مكون");
        m.put("no_allergens", "استبعاد مسببات الحساسية");
        m.put("no_gluten", "خالٍ من الغلوتين");
        m.put("no_lactose", "خالٍ من اللاكتوز");
        m.put("add_to_order", "للإضافة إلى الطلب");
        m.put("choose_at_least_one", "يرجى اختيار مكون واحد على الأقل");
        m.put("kumpir_added", "تمت إضافة kumpir إلى العربة");
        m.put("cancel", "إلغاء");
        return m;
    }
}
