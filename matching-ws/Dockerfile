# Use an official Maven runtime as a parent image
FROM maven:3.8.7-openjdk-18-slim AS builder

# Set the working directory in the container
WORKDIR /app

ENV BACKEND_BRANCH="matching-ws"

# Clone the backend repository
RUN git clone https://github.com/CatalogueOfLife/backend.git
WORKDIR /app/backend
RUN git checkout $BACKEND_BRANCH

# Build all the CLB modules
RUN mvn clean install -DskipTests

# Build the Maven project and create a exec file
WORKDIR /app/backend/matching-ws
RUN mvn clean install spring-boot:repackage

# Copy the executable JAR file from the builder image to the new image
FROM openjdk:17.0.1-jdk-slim

# Set environment variables
ARG DEBIAN_FRONTEND=noninteractive
ENV SERVER_PORT="8080"
ARG USER=matching
ARG APP_ARTIFACT=matching-ws

# Set environment variables
ARG CLB_DATASET_ID=""
ARG CLB_URL=""
ARG CLB_USER=""
ARG CLB_PASSWORD=""

# Directories and perms
RUN mkdir -p /data/$APP_ARTIFACT && \
    groupadd -r $USER -g 1000 && useradd -r -g $USER -u 1000 -m $USER && \
    chown -R $USER:$USER /data/$APP_ARTIFACT

# Set the working directory in the container
WORKDIR /opt/gbif/$APP_ARTIFACT

# Copy the executable JAR file from the builder image to the new image
COPY --from=builder /app/backend/matching-ws/target/matching-ws-*-SNAPSHOT-exec.jar /opt/gbif/$APP_ARTIFACT/app.jar

# CSV export from checklistbank
RUN if [ -n "$CLB_DATASET_ID" ]; then \
    java -jar /opt/gbif/$APP_ARTIFACT/app.jar \
    --mode=EXPORT_CSV \
    --export.path=/data/$APP_ARTIFACT/exports \
    --clb.dataset.id=$CLB_DATASET_ID \
    --clb.url=$CLB_URL \
    --clb.user=$CLB_USER \
    --clb.password=$CLB_PASSWORD; \
    fi

# Generate the index from the exported CSV
RUN if [ -n "$CLB_DATASET_ID" ]; then \
    java -jar /opt/gbif/$APP_ARTIFACT/app.jar \
    --mode=INDEX_CSV \
    --export.path=/data/matching-ws/exports/$CLB_DATASET_ID \
    --index.path=/data/matching-ws/index; \
    fi

USER $USER
EXPOSE $SERVER_PORT

CMD ["java", "-jar", "app.jar"]