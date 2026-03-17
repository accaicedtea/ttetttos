package com.util;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility per caricare piccoli SVG (bandiere) dalle risorse.
 * <p>
 * Le bandiere sono salvate in src/main/resources/com/example/imgs/flags/*.svg,
 * e vengono renderizzate in un {@link javafx.scene.Group} con {@link SVGPath}.
 */
public class FlagIcon {

    private static final Pattern VIEWBOX = Pattern.compile("viewBox=\"([0-9.]+)\\s+([0-9.]+)\\s+([0-9.]+)\\s+([0-9.]+)\"");
    private static final Pattern PATH = Pattern.compile("<path[^>]*fill=\"([^\"]+)\"[^>]*d=\"([^\"]+)\"[^>]*/>");

    /**
     * Carica la bandiera associata al codice lingua e la scala a una dimensione massima.
     *
     * @param lang  codice lingua (it/en/de/fr/ar)
     * @param size  dimensione in pixel del lato maggiorato (max width/height)
     */
    public static Node load(String lang, double maxWidth, double maxHeight) {
        String code = switch (lang) {
            case "it" -> "it";
            case "en" -> "gb";
            case "de" -> "de";
            case "fr" -> "fr";
            case "ar" -> "ar"; // non abbiamo una SVG: useremo un placeholder testuale
            default -> "gb"; // fallback
        };

        String resource = "/com/example/imgs/flags/" + code + ".svg";
        // Special case: lingua araba (non abbiamo SVG nel repo) → badge semplice
        if ("ar".equals(code)) {
            javafx.scene.shape.Rectangle bg = new javafx.scene.shape.Rectangle(maxWidth, maxHeight);
            bg.setArcWidth(6);
            bg.setArcHeight(6);
            bg.setFill(Color.web("#006400", 0.12));
            javafx.scene.text.Text t = new javafx.scene.text.Text("AR");
            t.setFill(Color.web("#006400"));
            t.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
            t.setTranslateX(maxWidth * 0.25);
            t.setTranslateY(maxHeight * 0.60);
            Group g = new Group(bg, t);
            g.setScaleX(0.9);
            g.setScaleY(0.9);
            return g;
        }

        try (InputStream is = FlagIcon.class.getResourceAsStream(resource)) {
            if (is == null) return createPlaceholder(Math.max(maxWidth, maxHeight));
            String svg = readAll(is);

            double viewW = 0, viewH = 0;
            Matcher vb = VIEWBOX.matcher(svg);
            if (vb.find()) {
                viewW = Double.parseDouble(vb.group(3));
                viewH = Double.parseDouble(vb.group(4));
            }
            if (viewW <= 0 || viewH <= 0) {
                viewW = maxWidth;
                viewH = maxHeight;
            }

            Group group = new Group();

            // Aggiungi i path SVG (senza sfondo)
            Matcher m = PATH.matcher(svg);
            while (m.find()) {
                String fill = m.group(1);
                String d    = m.group(2);
                SVGPath p = new SVGPath();
                p.setContent(d);
                try {
                    p.setFill(Color.web(fill));
                } catch (Exception ignored) {
                    p.setFill(Color.BLACK);
                }
                group.getChildren().add(p);
            }

            // Scala e centra il flag rispetto al box
            double scale = Math.min(maxWidth / viewW, maxHeight / viewH);
            group.setScaleX(scale);
            group.setScaleY(scale);
            group.setTranslateX((maxWidth - viewW * scale) / 2);
            group.setTranslateY((maxHeight - viewH * scale) / 2);
            return group;
        } catch (Exception e) {
            return createPlaceholder(Math.max(maxWidth, maxHeight));
        }
    }

    public static Node load(String lang, double size) {
        return load(lang, size, size * 0.75); // default 4:3 aspect ratio
    }

    private static Node createPlaceholder(double size) {
        // fallback: piccolo cerchio grigio
        javafx.scene.shape.Circle c = new javafx.scene.shape.Circle(size / 2.0, Color.web("#cccccc"));
        c.setRadius(size / 2.0);
        return c;
    }

    private static String readAll(InputStream is) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
