#!/bin/bash
# Modify PaymentController.java to replace submitOrder with payment service call

sed -i 's/OrderQueue.submitOrder(CartManager.get(), method,/final int orderId = com.app.model.OrderStateManager.get().getCurrentOrderId(); java.util.concurrent.Executors.newSingleThreadExecutor().submit(() -> { try { com.api.services.OrdersService.startPayment(orderId); javafx.application.Platform.runLater(() -> { com.app.model.OrderStateManager.get().transitionToPaymentStarted(); Navigator.goTo(Navigator.Screen.CONFIRM, String.valueOf(orderId)); }); } catch(Exception e) { e.printStackTrace(); javafx.application.Platform.runLater(() -> Navigator.goTo(Navigator.Screen.CONFIRM, String.valueOf(orderId))); } });/g' /home/acca/websites/TOTTEM/demo/src/main/java/com/app/controllers/PaymentController.java
sed -i 's/orderNumber -> Navigator.goTo(Navigator.Screen.CONFIRM, orderNumber));//g' /home/acca/websites/TOTTEM/demo/src/main/java/com/app/controllers/PaymentController.java
