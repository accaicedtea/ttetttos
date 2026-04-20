package com.app.exemples;

import javafx.animation.RotateTransition;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.net.URL;

public class AudioWithRotatingCover extends Application {

    @Override
    public void start(Stage primaryStage) {
        // 1. Carica il video MP4 dalle risorse o usa un fallback remoto
        URL videoResource = getClass().getResource("/video/video.mp4");
        String videoUrl;
        if (videoResource != null) {
            videoUrl = videoResource.toString();
        } else {
            System.err.println("Video locale non trovato. Uso un video di test da internet...");
            // URL di test pubblico stabile (Big Buck Bunny)
            videoUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";
        }
        
        Media media = new Media(videoUrl);
        MediaPlayer mediaPlayer = new MediaPlayer(media);
        mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);

        // 2. Crea il MediaView per visualizzare il video
        javafx.scene.media.MediaView mediaView = new javafx.scene.media.MediaView(mediaPlayer);
        mediaView.setFitWidth(400);
        mediaView.setPreserveRatio(true);

        // 3. Mostra la GUI
        StackPane root = new StackPane(mediaView);
        Scene scene = new Scene(root, 400, 400);
        
        mediaPlayer.play();

        primaryStage.setTitle("Riproduzione Video in loop");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}