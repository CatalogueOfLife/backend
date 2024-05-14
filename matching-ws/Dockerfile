# Use an official Maven runtime as a parent image
FROM maven:3.8.5-openjdk-17 AS builder

# Set the working directory in the container
WORKDIR /app

ARG CLB_DATASET_ID=""
ARG CLB_URL=""
ARG CLB_USER=""
ARG CLB_PASSWORD=""
ENV CLB_API_URL="https://api.checklistbank.org"
ENV BACKEND_BRANCH="matching-ws"

# Clone the backend repository
RUN git clone https://github.com/CatalogueOfLife/backend.git
WORKDIR /app/backend
RUN git checkout $BACKEND_BRANCH

# Build all the CLB modules
RUN mvn install -DskipTests

# Store git commit id and log
RUN curl -o /app/backend/git.json -H "Accept: application/vnd.github+json" "https://api.github.com/repos/catalogueoflife/backend/commits/$(git rev-parse HEAD)"

# Run a script to validate the arguments
RUN if [ -n "$CLB_DATASET_ID" ]; then \
        if [ -z "$CLB_URL" ] || [ -z "$CLB_USER" ] || [ -z "$CLB_PASSWORD" ]; then \
            echo "Error: CLB_URL, CLB_USER, and CLB_PASSWORD must be set when CLB_DATASET_ID is set." >&2; \
            exit 1; \
        fi \
    fi

# cache a copy of the dataset metadata from checklistbank
RUN if [ -n "$CLB_DATASET_ID" ]; then \
        curl -o /app/backend/dataset.json $CLB_API_URL/dataset/$CLB_DATASET_ID.json; \
    else \
        echo "{}" > /app/backend/dataset.json; \
    fi

# Build the Maven project and create a exec file
WORKDIR /app/backend/matching-ws
RUN mvn install spring-boot:repackage

# Copy the executable JAR file from the builder image to the new image
FROM openjdk:17.0.1-jdk-slim

# Set environment variables
ARG DEBIAN_FRONTEND=noninteractive
ENV SERVER_PORT=8080
ENV V1_ENABLED="false"
ENV USER=matching
ENV APP_ARTIFACT=matching-ws

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
COPY --from=builder /app/backend/git.json /opt/gbif/$APP_ARTIFACT/git.json
COPY --from=builder /app/backend/dataset.json /opt/gbif/$APP_ARTIFACT/dataset.json

# CSV export from checklistbank
RUN if [ -n "$CLB_DATASET_ID" ]; then \
        java -jar app.jar \
        --mode=EXPORT_CSV \
        --export.path=/data/$APP_ARTIFACT/exports \
        --clb.dataset.id=$CLB_DATASET_ID \
        --clb.url=$CLB_URL \
        --clb.user=$CLB_USER \
        --clb.password=$CLB_PASSWORD && \
        java -jar app.jar \
        --mode=INDEX_CSV \
        --export.path=/data/$APP_ARTIFACT/exports/$CLB_DATASET_ID \
        --index.path=/data/$APP_ARTIFACT/index; \
    fi

RUN chown -R $USER:$USER /opt/gbif/$APP_ARTIFACT

USER $USER
EXPOSE $SERVER_PORT

CMD java -jar app.jar --server.port=$SERVER_PORT --v1.enabled=$V1_ENABLED --working.dir=/opt/gbif/$APP_ARTIFACT