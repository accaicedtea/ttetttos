package com.util;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Carica bandiere SVG dalle risorse e le restituisce come nodi JavaFX
 * con dimensioni precise nel layout (usa un Pane wrapper + Scale transform).
 *
 * Risorse: src/main/resources/com/app/imgs/flags/*.svg
 */
public class FlagIcon {

    private static final Pattern VIEWBOX = Pattern.compile(
        "viewBox=\"([0-9.]+)\\s+([0-9.]+)\\s+([0-9.]+)\\s+([0-9.]+)\"");
    private static final Pattern PATH_FILL_FIRST = Pattern.compile(
        "<path[^>]*fill=\"([^\"]+)\"[^>]*d=\"([^\"]+)\"[^>]*/>");
    private static final Pattern PATH_D_FIRST = Pattern.compile(
        "<path[^>]*d=\"([^\"]+)\"[^>]*fill=\"([^\"]+)\"[^>]*/>");

    /**
     * Carica la bandiera e la scala esattamente a maxWidth x maxHeight.
     * Il nodo restituito ha dimensioni di layout corrette (non zero).
     */
    public static Node load(String lang, double maxWidth, double maxHeight) {
        String code = switch (lang) {
            case "it" -> "it";
            case "en" -> "gb";
            case "de" -> "de";
            case "fr" -> "fr";
            case "ar" -> "ar";
            default   -> "gb";
        };

        // Placeholder arabo (nessun SVG disponibile)
        if ("ar".equals(code)) {
            return buildArPlaceholder(maxWidth, maxHeight);
        }

        String resource = "/com/app/imgs/flags/" + code + ".svg";

        try (InputStream is = FlagIcon.class.getResourceAsStream(resource)) {
            if (is == null) return buildColorPlaceholder(maxWidth, maxHeight);

            String svg = readAll(is);

            // Leggi viewBox
            double viewW = maxWidth, viewH = maxHeight;
            Matcher vb = VIEWBOX.matcher(svg);
            if (vb.find()) {
                viewW = Double.parseDouble(vb.group(3));
                viewH = Double.parseDouble(vb.group(4));
            }

            // Costruisci il Group con tutti i path
            Group group = new Group();
            addPaths(group, svg);

            if (group.getChildren().isEmpty()) {
                return buildColorPlaceholder(maxWidth, maxHeight);
            }

            // Scala che fa entrare il flag esattamente in maxWidth x maxHeight
            double scale = Math.min(maxWidth / viewW, maxHeight / viewH);
            double scaledW = viewW * scale;
            double scaledH = viewH * scale;

            // Applica la scala come Transform (non setScaleX) così il layout
            // riconosce le dimensioni reali
            group.getTransforms().add(new Scale(scale, scale, 0, 0));

            // Pane wrapper con dimensioni esplicite — questo è ciò che
            // il layout vede. Il Group viene centrato dentro.
            Pane wrapper = new Pane(group);
            wrapper.setPrefSize(maxWidth, maxHeight);
            wrapper.setMinSize(maxWidth, maxHeight);
            wrapper.setMaxSize(maxWidth, maxHeight);

            // Centra il gruppo scalato nel wrapper
            group.setTranslateX((maxWidth  - scaledW) / 2.0);
            group.setTranslateY((maxHeight - scaledH) / 2.0);

            // Clip: nessun pixel esce dal wrapper
            Rectangle clip = new Rectangle(maxWidth, maxHeight);
            wrapper.setClip(clip);

            return wrapper;

        } catch (Exception e) {
            return buildColorPlaceholder(maxWidth, maxHeight);
        }
    }

    /** Overload con aspect ratio 4:3 */
    public static Node load(String lang, double size) {
        return load(lang, size, size * 0.75);
    }

    // ── Builder path SVG ─────────────────────────────────────────────

    private static void addPaths(Group group, String svg) {
        // Prova prima fill prima di d, poi d prima di fill
        boolean found = tryPattern(group, svg, PATH_FILL_FIRST, 1, 2);
        if (!found)     tryPattern(group, svg, PATH_D_FIRST,    2, 1);
    }

    private static boolean tryPattern(Group group, String svg,
                                       Pattern p, int fillGroup, int dGroup) {
        Matcher m = p.matcher(svg);
        boolean any = false;
        while (m.find()) {
            String fill = m.group(fillGroup);
            String d    = m.group(dGroup);
            SVGPath path = new SVGPath();
            path.setContent(d);
            try {
                path.setFill(Color.web(fill));
            } catch (Exception ignored) {
                path.setFill(Color.GRAY);
            }
            path.setStroke(null);
            group.getChildren().add(path);
            any = true;
        }
        return any;
    }

    // ── Placeholder ───────────────────────────────────────────────────

    private static Node buildArPlaceholder(double w, double h) {
        Pane p = new Pane();
        p.setPrefSize(w, h);
        p.setMinSize(w, h);
        p.setMaxSize(w, h);

        Rectangle bg = new Rectangle(w, h);
        bg.setArcWidth(8); bg.setArcHeight(8);
        bg.setFill(Color.web("#006400", 0.15));
        bg.setStroke(Color.web("#006400", 0.40));
        bg.setStrokeWidth(1.5);

        Text t = new Text("AR");
        t.setFill(Color.web("#00aa00"));
        t.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");
        // Centra il testo
        t.setLayoutX(w / 2 - 18);
        t.setLayoutY(h / 2 + 10);

        p.getChildren().addAll(bg, t);
        return p;
    }

    private static Node buildColorPlaceholder(double w, double h) {
        Pane p = new Pane();
        p.setPrefSize(w, h);
        p.setMinSize(w, h);
        p.setMaxSize(w, h);

        Rectangle bg = new Rectangle(w, h);
        bg.setArcWidth(8); bg.setArcHeight(8);
        bg.setFill(Color.web("#444466"));

        Text t = new Text("?");
        t.setFill(Color.web("#aaaacc"));
        t.setStyle("-fx-font-size: 32px; -fx-font-weight: bold;");
        t.setLayoutX(w / 2 - 10);
        t.setLayoutY(h / 2 + 12);

        p.getChildren().addAll(bg, t);
        return p;
    }

    // ── Utility ───────────────────────────────────────────────────────

    private static String readAll(InputStream is) {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}