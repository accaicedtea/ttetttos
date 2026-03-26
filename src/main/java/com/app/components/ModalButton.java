package com.app.components;

import javafx.scene.control.Button;

/**
 * Descriptor immutabile per un bottone del modal.
 *
 * Usato da {@link ModalDialog.Builder#button(ModalButton)} per
 * aggiungere bottoni con stile, testo e azione personalizzati.
 *
 * Usa i factory method statici per i pattern più comuni:
 * <pre>
 *   ModalButton.confirm("Elimina", () -> doDelete())
 *   ModalButton.cancel("Annulla")
 *   ModalButton.primary("OK", () -> onOk())
 *   ModalButton.danger("Elimina", () -> onDelete())
 *   ModalButton.ghost("Chiudi", null)
 * </pre>
 */
public final class ModalButton {

    /** Stile CSS del bottone. */
    public enum Style {
        PRIMARY,   // bottone principale (accent color)
        DANGER,    // azione distruttiva (rosso)
        SUCCESS,   // azione positiva (verde)
        GHOST,     // secondario / annulla (outline)
        WARNING,   // azione da confermare (giallo)
    }

    private final String   label;
    private final Style    style;
    private final Runnable action;      // null = chiude solo il modal
    private final boolean  closesModal; // se false il modal rimane aperto dopo il click

    private ModalButton(String label, Style style, Runnable action, boolean closesModal) {
        this.label       = label;
        this.style       = style;
        this.action      = action;
        this.closesModal = closesModal;
    }

    // ── Factory methods ──────────────────────────────────────────────

    public static ModalButton primary(String label, Runnable action)  { return new ModalButton(label, Style.PRIMARY, action,  true);  }
    public static ModalButton danger (String label, Runnable action)  { return new ModalButton(label, Style.DANGER,  action,  true);  }
    public static ModalButton success(String label, Runnable action)  { return new ModalButton(label, Style.SUCCESS, action,  true);  }
    public static ModalButton ghost  (String label, Runnable action)  { return new ModalButton(label, Style.GHOST,   action,  true);  }
    public static ModalButton warning(String label, Runnable action)  { return new ModalButton(label, Style.WARNING, action,  true);  }

    /** Bottone che chiude il modal senza eseguire azioni. */
    public static ModalButton cancel(String label)                    { return new ModalButton(label, Style.GHOST,   null,    true);  }

    /** Bottone che esegue l'azione ma NON chiude il modal (es. validazione in corso). */
    public static ModalButton persistent(String label, Style style, Runnable action) {
        return new ModalButton(label, style, action, false);
    }

    // ── Getters ──────────────────────────────────────────────────────

    public String   getLabel()       { return label;       }
    public Style    getStyle()       { return style;       }
    public Runnable getAction()      { return action;      }
    public boolean  closesModal()    { return closesModal; }

    /** Costruisce il nodo JavaFX Button con le classi CSS appropriate. */
    Button buildNode() {
        Button btn = new Button(label);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.getStyleClass().add("modal-btn");
        btn.getStyleClass().add(switch (style) {
            case PRIMARY -> "modal-btn-primary";
            case DANGER  -> "modal-btn-danger";
            case SUCCESS -> "modal-btn-success";
            case WARNING -> "modal-btn-warning";
            case GHOST   -> "modal-btn-ghost";
        });
        return btn;
    }
}
