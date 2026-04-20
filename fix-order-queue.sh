#!/bin/bash
# Modify OrderQueue.java to add createOrderAsync and startPaymentAsync instead of submitOrder

sed -i 's/public static void submitOrder(CartManager cart, String method,/public static void createOrderAsync(CartManager cart, Runnable onSuccess, java.util.function.Consumer<String> onError) {/g' /home/acca/websites/TOTTEM/demo/src/main/java/com/app/model/OrderQueue.java

