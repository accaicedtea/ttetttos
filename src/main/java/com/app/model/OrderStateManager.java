package com.app.model;

/**
 * OrderStateManager — singleton per tracciare lo stato atomico dell'ordine.
 * 
 * Flusso di stato:
 * 1. IN_ATTESA: Quando viene creato l'ordine (CartController → proceed)
 * 2. PAGAMENTO_AVVIATO: Quando l'utente seleziona il metodo di pagamento
 * 3. PAGATO: Quando il sistema riceve la conferma di pagamento
 * 4. STAMPATO: (Opzionale) Quando lo scontrino è stato stampato
 */
public class OrderStateManager {
    private static final OrderStateManager INSTANCE = new OrderStateManager();
    
    private int currentOrderId = -1;
    private String currentOrderState = null; // IN_ATTESA, PAGAMENTO_AVVIATO, PAGATO, STAMPATO
    
    private OrderStateManager() {}
    
    public static OrderStateManager get() {
        return INSTANCE;
    }
    
    // ── State Management ──────────────────────────────────────────
    
    /**
     * Crea un nuovo ordine nello stato IN_ATTESA.
     * Chiamato da OrderQueue.submitOrder() quando viene creato l'ordine.
     */
    public void createOrder(int orderId) {
        this.currentOrderId = orderId;
        this.currentOrderState = "IN_ATTESA";
        System.out.println("[OrderStateManager] Order created: " + orderId + " (IN_ATTESA)");
    }
    
    /**
     * Transizione a PAGAMENTO_AVVIATO.
     * Chiamato da PaymentController quando l'utente seleziona il metodo di pagamento.
     */
    public void transitionToPaymentStarted() {
        if (currentOrderId < 0) {
            System.err.println("[OrderStateManager] No order to transition!");
            return;
        }
        this.currentOrderState = "PAGAMENTO_AVVIATO";
        System.out.println("[OrderStateManager] Order " + currentOrderId + " → PAGAMENTO_AVVIATO");
    }
    
    /**
     * Transizione a PAGATO.
     * Chiamato da ConfirmController quando riceve la conferma di pagamento.
     */
    public void transitionToPaid() {
        if (currentOrderId < 0) {
            System.err.println("[OrderStateManager] No order to transition!");
            return;
        }
        this.currentOrderState = "PAGATO";
        System.out.println("[OrderStateManager] Order " + currentOrderId + " → PAGATO");
    }
    
    /**
     * Transizione a STAMPATO.
     * Opzionale - per quando lo scontrino viene effettivamente stampato.
     */
    public void transitionToPrinted() {
        if (currentOrderId < 0) {
            System.err.println("[OrderStateManager] No order to transition!");
            return;
        }
        this.currentOrderState = "STAMPATO";
        System.out.println("[OrderStateManager] Order " + currentOrderId + " → STAMPATO");
    }
    
    /**
     * Resetta lo stato dopo il completamento dell'ordine.
     */
    public void reset() {
        this.currentOrderId = -1;
        this.currentOrderState = null;
        System.out.println("[OrderStateManager] Order state reset");
    }
    
    // ── Getters ───────────────────────────────────────────────────
    
    public int getCurrentOrderId() {
        return currentOrderId;
    }
    
    public String getCurrentOrderState() {
        return currentOrderState;
    }
    
    public boolean hasActiveOrder() {
        return currentOrderId >= 0;
    }
}
