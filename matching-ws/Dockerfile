# Use an official Maven runtime as a parent image
FROM maven:3.8.5-openjdk-17 AS builder

# Set the working directory in the container
WORKDIR /app

RUN git clone https://github.com/CatalogueOfLife/backend.git
WORKDIR /app/backend
RUN git checkout matching-ws
RUN mvn clean install -DskipTests
WORKDIR /app/backend/matching-ws

# Build the Maven project and create a exec file
RUN mvn clean install spring-boot:repackage

FROM openjdk:17-jdk-slim

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
COPY --from=builder /app/backend/matching-ws/target/matching-ws-*-SNAPSHOT-exec.jar /opt/gbif/$APP_ARTIFACT/app.jar
RUN rm -Rf /app/backend

USER $USER
EXPOSE $SERVER_PORT

CMD ["java", "-jar", "app.jar"]