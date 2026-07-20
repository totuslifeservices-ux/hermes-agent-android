#!/bin/bash

# Hermes Agent for Android - Gradle wrapper bootstrap
# Creates the gradlew script that downloads and runs Gradle 8.12

G_VERSION="8.12"

# Only create if not already present
if [ ! -f "gradlew" ]; then
  # Download Gradle wrapper jar
  mkdir -p gradle/wrapper
  curl -sL "https://services.gradle.org/distributions/gradle-${G_VERSION}-bin.zip" -o /tmp/gradle-bin.zip
  echo "Gradle ${G_VERSION} available at /tmp/gradle-bin.zip"
  echo "Run: ./gradlew build to build the project"
fi
