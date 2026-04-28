package com.app.webview;

import java.net.URL;
import java.util.ResourceBundle;

import com.app.controllers.BaseController;
import com.util.Navigator;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

/**
 * WebView controller per mostrare il carousel di presentazione.
 * Carica http://127.0.0.1:5500/tests/htmltest/carousel_single_box.html fullscreen.
 * Un click in qualsiasi punto naviga a Welcome.
 */
public class FXMLDocumentController extends BaseController implements Initializable {

    @FXML
    private Label label;
    @FXML
    private WebView mywebview;

    private WebEngine engine;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Nascondi label e button
        if (label != null) label.setVisible(false);

        engine = mywebview.getEngine();
        
        // Carica il carousel dalle risorse del progetto
        //URL carouselResource = getClass().getResource("/com/app/html/carousel_single_box.html");
        URL carouselResource = getClass().getResource("/com/app/html/fichissimo.html");
        
        if (carouselResource != null) {
            String carouselUrl = carouselResource.toExternalForm();
            System.out.println("[Webview] Caricamento carousel da: " + carouselUrl);
            engine.load(carouselUrl);
        } else {
            System.err.println("[Webview] ERRORE: fichissimo.html non trovato nelle risorse!");
        }

        // Click handler: qualsiasi click va a Welcome
        mywebview.setOnMouseClicked(event -> {
            System.out.println("[Webview] Click rilevato, navigazione a Welcome");
            Navigator.goTo(Navigator.Screen.WELCOME);
        });
    }
}
