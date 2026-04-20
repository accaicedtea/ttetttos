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
        if (key == null)
            return "";
        Map<String, String> translation = STRINGS.getOrDefault(lang, STRINGS.get("it"));
        return translation.getOrDefault(key, key);
    }

    public static void mergeTranslations(Map<String, Map<String, String>> m) {
        if (m == null)
            return;
        for (Map.Entry<String, Map<String, String>> e : m.entrySet()) {
            if (e.getKey() == null || e.getValue() == null)
                continue;
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
        m.put("welcome_title", "BENVENUTO");
        m.put("welcome_subtitle", "TOCCA PER INIZIARE");
        m.put("choose_lang", "SCEGLI LA LINGUA");
        m.put("start", "INIZIA L'ORDINE");
        m.put("add_to_cart", "AGGIUNGI AL CARRELLO");
        m.put("added", "AGGIUNTO!");
        m.put("cart", "CARRELLO");
        m.put("cart_empty", "IL CARRELLO È VUOTO");
        m.put("total", "TOTALE");
        m.put("proceed", "PROCEDI AL PAGAMENTO");
        m.put("payment_title", "COME VUOI PAGARE?");
        m.put("cash", "CONTANTI");
        m.put("cash_sub", "PAGA ALLA CASSA");
        m.put("card", "CARTA");
        m.put("card_sub", "BANCOMAT/CREDITO");
        m.put("confirm_title", "ORDINE CONFERMATO!");
        m.put("confirm_sub", "IL TUO NUMERO È");
        m.put("confirm_msg", "RITIRA IL TUO ORDINE ALLA CASSA. GRAZIE!");
        m.put("back", "INDIETRO");
        m.put("allergens", "ALLERGENI");
        m.put("ingredients", "INGREDIENTI");
        m.put("qty", "QTA");
        m.put("remove", "RIMUOVI");
        m.put("allergen_warning", "ATTENZIONE: ALLERGENI NEL TUO ORDINE");
        m.put("order_number", "NUMERO ORDINE");
        m.put("proceed_hint", "PUOI TORNARE INDIETRO QUANDO VUOI");
        m.put("compose_kumpir", "COMPONI IL TUO KUMPIR");
        m.put("loading_menu", "CARICAMENTO MENU...");
        m.put("loading", "CARICAMENTO...");
        m.put("error", "ERRORE");
        m.put("search_ingredient", "CERCA INGREDIENTE");
        m.put("no_allergens", "ESCLUDI ALLERGENI");
        m.put("no_gluten", "SENZA GLUTINE");
        m.put("no_lactose", "SENZA LATTOSIO");
        m.put("add_to_order", "DA AGGIUNGERE");
        m.put("choose_at_least_one", "SELEZIONA ALMENO UN INGREDIENTE");
        m.put("kumpir_added", "KUMPIR AGGIUNTO AL CARRELLO");
        m.put("cancel", "ANNULLA");
        m.put("confirm", "CONFERMA");
        return m;
    }

    private static Map<String, String> buildEnglish() {
        Map<String, String> m = new HashMap<>();
        m.put("welcome_title", "WELCOME");
        m.put("welcome_subtitle", "TAP TO START");
        m.put("choose_lang", "CHOOSE YOUR LANGUAGE");
        m.put("start", "START ORDER");
        m.put("add_to_cart", "ADD TO CART");
        m.put("added", "ADDED!");
        m.put("cart", "CART");
        m.put("cart_empty", "CART IS EMPTY");
        m.put("total", "TOTAL");
        m.put("proceed", "PROCEED TO PAYMENT");
        m.put("payment_title", "HOW DO YOU WANT TO PAY?");
        m.put("cash", "CASH");
        m.put("cash_sub", "PAY AT CASHIER");
        m.put("card", "CARD");
        m.put("card_sub", "DEBIT/CREDIT");
        m.put("confirm_title", "ORDER CONFIRMED!");
        m.put("confirm_sub", "YOUR NUMBER IS");
        m.put("confirm_msg", "PICK UP YOUR ORDER AT CASHIER. THANKS!");
        m.put("back", "BACK");
        m.put("allergens", "ALLERGENS");
        m.put("ingredients", "INGREDIENTS");
        m.put("qty", "QTY");
        m.put("remove", "REMOVE");
        m.put("allergen_warning", "ALLERGEN WARNING IN YOUR ORDER");
        m.put("order_number", "ORDER NUMBER");
        m.put("proceed_hint", "YOU CAN GO BACK ANYTIME");
        m.put("compose_kumpir", "BUILD YOUR KUMPIR");
        m.put("loading_menu", "LOADING MENU...");
        m.put("loading", "LOADING...");
        m.put("error", "ERROR");
        m.put("search_ingredient", "SEARCH INGREDIENT");
        m.put("no_allergens", "EXCLUDE ALLERGENS");
        m.put("no_gluten", "GLUTEN-FREE");
        m.put("no_lactose", "LACTOSE-FREE");
        m.put("add_to_order", "TO ADD TO ORDER");
        m.put("choose_at_least_one", "PLEASE CHOOSE AT LEAST ONE INGREDIENT");
        m.put("kumpir_added", "KUMPIR ADDED TO CART");
        m.put("cancel", "CANCEL");
        m.put("confirm", "CONFIRM");
        return m;
    }

    private static Map<String, String> buildDeutsch() {
        Map<String, String> m = new HashMap<>();
        m.put("welcome_title", "WILLKOMMEN");
        m.put("welcome_subtitle", "TIPPEN ZUM START");
        m.put("choose_lang", "WÄHLE DEINE SPRACHE");
        m.put("start", "BESTELLUNG STARTEN");
        m.put("add_to_cart", "IN DEN WARENKORB");
        m.put("added", "HINZUGEFÜGT!");
        m.put("cart", "WARENKORB");
        m.put("cart_empty", "WARENKORB IST LEER");
        m.put("total", "GESAMT");
        m.put("proceed", "ZUR ZAHLUNG");
        m.put("payment_title", "WIE MÖCHTEN SIE BEZAHLEN?");
        m.put("cash", "BARGELD");
        m.put("cash_sub", "BEZAHLEN AN DER KASSE");
        m.put("card", "KARTE");
        m.put("card_sub", "DEBIT/KREDIT");
        m.put("confirm_title", "BESTELLUNG BESTÄTIGT!");
        m.put("confirm_sub", "IHRE NUMMER IST");
        m.put("confirm_msg", "BITTE HOLEN SIE IHRE BESTELLUNG AN DER KASSE AB. DANKE!");
        m.put("back", "ZURÜCK");
        m.put("allergens", "ALLERGENE");
        m.put("ingredients", "ZUTATEN");
        m.put("qty", "MENGE");
        m.put("remove", "ENTFERNEN");
        m.put("allergen_warning", "ALLERGENE IN IHRER BESTELLUNG");
        m.put("order_number", "BESTELLNUMMER");
        m.put("proceed_hint", "SIE KÖNNEN JEDERZEIT ZURÜCK");
        m.put("compose_kumpir", "ERSTELLE DEINEN KUMPIR");
        m.put("loading_menu", "MENÜ WIRD GELADEN...");
        m.put("loading", "WIRD GELADEN...");
        m.put("error", "FEHLER");
        m.put("search_ingredient", "ZUTAT SUCHEN");
        m.put("no_allergens", "ALLERGENE AUSSCHLIESSEN");
        m.put("no_gluten", "GLUTENFREI");
        m.put("no_lactose", "LAKTOSEFREI");
        m.put("add_to_order", "ZUM BESTELLEN");
        m.put("choose_at_least_one", "WÄHLE MINDESTENS EINE ZUTAT");
        m.put("kumpir_added", "KUMPIR ZUM WARENKORB HINZUGEFÜGT");
        m.put("cancel", "ABBRECHEN");
        m.put("confirm", "BESTÄTIGEN");
        return m;
    }

    private static Map<String, String> buildFrancais() {
        Map<String, String> m = new HashMap<>();
        m.put("welcome_title", "BIENVENUE");
        m.put("welcome_subtitle", "TOUCHEZ POUR COMMENCER");
        m.put("choose_lang", "CHOISISSEZ VOTRE LANGUE");
        m.put("start", "COMMENCER LA COMMANDE");
        m.put("add_to_cart", "AJOUTER AU PANIER");
        m.put("added", "AJOUTÉ!");
        m.put("cart", "PANIER");
        m.put("cart_empty", "LE PANIER EST VIDE");
        m.put("total", "TOTAL");
        m.put("proceed", "PROCÉDER AU PAIEMENT");
        m.put("payment_title", "COMMENT VOULEZ-VOUS PAYER?");
        m.put("cash", "ESPÈCES");
        m.put("cash_sub", "PAYER À LA CAISSE");
        m.put("card", "CARTE");
        m.put("card_sub", "DÉBIT/CRÉDIT");
        m.put("confirm_title", "COMMANDE CONFIRMÉE!");
        m.put("confirm_sub", "VOTRE NUMÉRO EST");
        m.put("confirm_msg", "RÉCUPÉREZ VOTRE COMMANDE À LA CAISSE. MERCI!");
        m.put("back", "RETOUR");
        m.put("allergens", "ALLERGÈNES");
        m.put("ingredients", "INGRÉDIENTS");
        m.put("qty", "QTÉ");
        m.put("remove", "SUPPRIMER");
        m.put("allergen_warning", "ATTENTION AUX ALLERGÈNES DANS VOTRE COMMANDE");
        m.put("order_number", "NUMÉRO DE COMMANDE");
        m.put("proceed_hint", "VOUS POUVEZ REVENIR EN ARRIÈRE À TOUT MOMENT");
        m.put("compose_kumpir", "COMPOSEZ VOTRE KUMPIR");
        m.put("loading_menu", "CHARGEMENT DU MENU...");
        m.put("loading", "CHARGEMENT...");
        m.put("error", "ERREUR");
        m.put("search_ingredient", "RECHERCHER UN INGRÉDIENT");
        m.put("no_allergens", "EXCLURE LES ALLERGÈNES");
        m.put("no_gluten", "SANS GLUTEN");
        m.put("no_lactose", "SANS LACTOSE");
        m.put("add_to_order", "À AJOUTER À LA COMMANDE");
        m.put("choose_at_least_one", "VEUILLEZ CHOISIR AU MOINS UN INGRÉDIENT");
        m.put("kumpir_added", "KUMPIR AJOUTÉ AU PANIER");
        m.put("cancel", "ANNULER");
        m.put("confirm", "CONFIRMER");
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
        m.put("confirm", "تأكيد");
        return m;
    }
}
