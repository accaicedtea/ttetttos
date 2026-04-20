# GUI Architecture - FXML-Based Component System

## Folder Structure

```
src/main/resources/com/app/
│
├── screens/                    ← Intere schermate
│   ├── SplashScreen.fxml       (Logo di boot, niente controller)
│   ├── WelcomeScreen.fxml      (Benvenuto kiosk)
│   ├── CartScreen.fxml         (Riepilogo carrello)
│   ├── PaymentScreen.fxml      (Metodo pagamento)
│   ├── ConfirmScreen.fxml      (Conferma ordine con numero)
│   └── MenuScreen.fxml         (Catalogo prodotti) [= ShopPage.fxml]
│
├── components/                 ← Componenti riutilizzabili
│   ├── ProductCard.fxml        + ProductCardController.java
│   ├── CartItemRow.fxml        + CartItemRowController.java
│   ├── AllergenBanner.fxml     + AllergenBannerController.java
│   ├── Toast.fxml             (Future: notifiche toast)
│   └── Chip.fxml              (Future: allergeni/ingredienti chip)
│
├── layouts/                    ← Layout comuni
│   ├── ShopHeader.fxml         (Header sistema: clock, rete, badge carrello)
│   ├── AppFooter.fxml          (Footer branding/info) [se usato]
│   └── ScreenHeader.fxml       (Template header standard screen)
│
└── styles/
    ├── _variables-light.css
    ├── _variables-dark.css
    ├── _base.css
    ├── light-theme.css
    ├── dark-theme.css
    ├── components/
    │   ├── _product-card.css
    │   ├── _cart-item-row.css
    │   └── _allergen-banner.css
    └── screens/
        ├── _menu.css
        ├── _cart.css
        ├── _payment.css
        ├── _confirm.css
        └── ...
```

## Component Patterns

### Pattern 1: FXML Component + Controller Factory

```
ProductCard.fxml
    ↓
    ProductCardController.create(Product) → VBox
    ↓ (aggiunge agli elementi)
    CartList.getChildren().add(...)
```

**Vantaggi:**
- Dichiarativo (FXML): descrivi COSA vedi
- Controller logico (Java): contiene COME funziona
- Riutilizzabile: `.create()` factory method
- Reattivo: property bindings in FXML

**File coinvolti:**
- `resources/com/app/components/ProductCard.fxml` (UI markup)
- `java/com/app/components/ProductCardController.java` (logica)

---

### Pattern 2: Nested Component (fx:include)

```xml
<VBox>
    <fx:include source="layouts/ShopHeader.fxml" />    
    <!-- Contenuto screen -->
    <fx:include source="components/AllergenBanner.fxml" />
</VBox>
```

**Quando usarlo:**
- Componenti usati in MOLTE schermate (header, footer)
- Componenti che NON cambiano logica tra schermate
- Componenti con propri listener/event handlers

---

## Current Components

### ✅ ProductCard
```
┌─────────────────────────────────────────┐
│  [Immagine 240×180]                     │
│                                         │
│  Nome Prodotto                          │
│  € 7,50                                 │
│                                         │
│  Breve descrizione...                   │
│                                         │
│  [Allergen1] [Allergen2] [Allergen3]   │
└─────────────────────────────────────────┘
```

**File:**
- `components/ProductCard.fxml`
- `components/ProductCardController.java`

**Uso:**
```java
Product p = repo.getProductById(123);
VBox card = ProductCardController.create(p);
gridPane.add(card, col, row);
```

---

### ✅ CartItemRow
```
┌────────────────────────────────────────────────┐
│ Nome Prodotto    [Chip1] [Chip2]  - 1 +  € 7,50 │
│ € 5,00 (unitario)                              │
└────────────────────────────────────────────────┘
```

**File:**
- `components/CartItemRow.fxml`
- `components/CartItemRowController.java`

**Uso:**
```java
CartItem item = CartManager.get().getItems().get(0);
HBox row = CartItemRowController.create(item);
cartList.getChildren().add(row);
```

**Logica:**
- Qty +/- buttons aggiornano `item.qtyProperty()`
- Remove button chiama `CartManager.removeItem(item)`
- Totale riga reagisce a qty changes via property listener

---

### ✅ AllergenBanner
```
┌────────────────────────────────────────────────┐
│ ⚠️  Attenzione: allergeni nel tuo ordine       │
│                                                 │
│  [Latte] [Glutine] [Arachidi]                  │
└────────────────────────────────────────────────┘
```

**File:**
- `components/AllergenBanner.fxml`
- `components/AllergenBannerController.java`

**Uso:**
```java
AllergenBannerController banner = (AllergenBannerController) fxmlLoader.getController();
banner.setAllergens(CartManager.get().getAllAllergens());
```

---

## Controller Architecture

### Base: BaseController
```java
class BaseController {
    // Utility metodi per tutti i controller:
    protected String t(String key)               // Traduzione i18n
    protected void setVisible(Node n, bool v)    // Show/hide con managed
    protected void applyTouchFeedback(Button b)  // Feedback tattile
    protected void makeInertiaScrollable(SP)     // Scroll con inerzia
    // ...
}
```

### Screens: extend BaseController

```java
class ShopPageController extends BaseController {
    @FXML initialize() { ... }  // Carica FXML, setup binding
    private void loadMenu()     // Dato asincrono
    private void showProducts() // Anima grid
}

class CartController extends BaseController {
    @FXML initialize() { ... }
    private void buildCartList() {
        cart.getItems().forEach(item -> 
            cartList.add(CartItemRowController.create(item))
        );
    }
}

class PaymentController extends BaseController {
    @FXML initialize() { ... }
    private void proceedWith(String method) // cash|card
}
```

### Components: Standalone Controllers

```java
class ProductCardController extends VBox {
    // NO @FXML initialize()
    // NO scene graph setup (fatto in FXML)
    
    public void setProduct(Product p) { ... }
    public static VBox create(Product p) { ... }
}

class CartItemRowController extends HBox {
    // Logica reattiva su CartItem
    // Binding tra UI e item.qtyProperty()
}

class AllergenBannerController extends HBox {
    // Mostra/nascondi allergeni
}
```

---

## Navigation Flow

```
SPLASH (1s)
    ↓
WELCOME [btn: ENTER]
    ↓
MENU (ShopPage)
    ├─→ Quick Cart Button → CART ← [btn: Proceed]
    │
PAYMENT [Payment Method]
    ├─→ CASH → CONFIRM [queue]
    └─→ CARD → CONFIRM [payment gateway]
        
CONFIRM [Order Number]
    └─→ Auto-timeout → WELCOME
```

**Back buttons:**
- MENU: (nessuno, root screen)
- CART: Back → MENU
- PAYMENT: Back → CART
- CONFIRM: (niente back, auto-timeout)

---

## Future Improvements

### 1. Toast Notifications
```xml
<!-- components/Toast.fxml -->
<AnchorPane styleClass="toast-overlay">
    <Label fx:id="message" />
</AnchorPane>
```

```java
class ToastController {
    public static void show(String msg, Duration duration) { ... }
}

// Uso:
ToastController.show("Item added to cart", Duration.seconds(2));
```

### 2. Chip Component (Allergen/Ingredient Badge)
```xml
<!-- components/Chip.fxml -->
<Label text="Allergen Name" styleClass="chip" />
```

```java
class ChipController {
    public static Label create(String text, ChipType type) { ... }
}
```

### 3. Modal Dialog (Already Java-based, keep as-is)

### 4. Quick Cart Button Animation
```java
// ShopPageController.quickCartBtn
// Pulse animation when items in cart
```

---

## I18n Keys (Translation)

```properties
# keys per controller:
cart=Carrello
proceed=Procedi
total=Totale
remove_item=Rimuovi
remove_item_confirm=Sicuro di rimuovere?
cart_empty=Carrello vuoto
```

---

## Styling Pattern

Each component has dedicated CSS:

```css
/* _product-card.css */
.product-card {
    -fx-spacing: 0;
    -fx-background-color: -color-surface;
}
.product-card-image { ... }
.product-card-name { ... }
.product-card-price { ... }

/* Utilizzato in ProductCard.fxml */
<VBox styleClass="product-card">
```

---

## Compile & Run

```bash
mvn clean compile          # ✓ Build success
mvn javafx:run             # ✓ Run app
```

All FXML files are automatically processed by Maven compiler plugin.

---

## Summary

| Layer | Pattern | Example |
|-------|---------|---------|
| **FXML** | Declarative markup | `ProductCard.fxml` |
| **Controller** | Java logic | `ProductCardController.java` |
| **Factory** | `.create()` static | `ProductCardController.create(p)` |
| **Binding** | Property observables | `item.qtyProperty().addListener(...)` |
| **Styles** | Scoped CSS | `_product-card.css` |
| **I18n** | Key-based strings | `t("cart")` |

