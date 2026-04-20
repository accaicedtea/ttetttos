#!/bin/bash
echo "Esecuzione di AudioWithRotatingCover in corso..."
mvn clean compile exec:java -Dexec.mainClass="com.app.exemples.AudioWithRotatingCover"
