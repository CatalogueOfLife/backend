# Use an official Maven runtime as a parent image
#FROM maven:3.8.3-openjdk-17 AS builder
#
## Set the working directory in the container
#WORKDIR /app
#
## Copy the project files into the container
#COPY pom.xml .
#COPY src ./src
#COPY matching-index /tmp/matching-index

# Build the Maven project and create a WAR file
#RUN mvn clean install spring-boot:repackage

FROM openjdk:17-jdk-slim

COPY index /data/matching-ws/index
COPY target/matching-ws-*-SNAPSHOT.jar /opt/gbif/$APP_ARTIFACT/app.jar

ARG DEBIAN_FRONTEND=noninteractive
ENV SERVER_PORT="8080"
ARG APT_KEY_DONT_WARN_ON_DANGEROUS_USAGE=DontWarn
ARG USER=matching
ARG APP_ARTIFACT=matching-ws

# Directories and perms
RUN mkdir -p /data/$APP_ARTIFACT && \
    groupadd -r $USER -g 1000 && useradd -r -g $USER -u 1000 -m $USER && \
    chown -R $USER:$USER /data/$APP_ARTIFACT

WORKDIR /opt/gbif/$APP_ARTIFACT

COPY target/matching-ws-*-SNAPSHOT-exec.jar /opt/gbif/$APP_ARTIFACT/app.jar

USER $USER
EXPOSE $SERVER_PORT

CMD ["java", "-jar", "app.jar"]