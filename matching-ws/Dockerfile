# Stage 1: Build Stage
FROM maven:3.8.7-openjdk-18-slim AS builder

# Set the working directory in the container
WORKDIR /app

# Clone the backend repository and build the project
RUN git clone https://github.com/CatalogueOfLife/backend.git && \
    cd backend && \
    git checkout matching-ws && \
    mvn clean install -DskipTests && \
    cd matching-ws && \
    mvn clean install spring-boot:repackage

# Stage 2: Final Stage
FROM openjdk:17.0.1-jdk-slim

# Set environment variables
ENV SERVER_PORT=8080 \
    USER=matching \
    APP_ARTIFACT=matching-ws

# Directories and permissions
RUN mkdir -p /data/$APP_ARTIFACT && \
    groupadd -r $USER -g 1000 && \
    useradd -r -g $USER -u 1000 -m $USER && \
    chown -R $USER:$USER /data/$APP_ARTIFACT

# Set the working directory in the container
WORKDIR /opt/gbif/$APP_ARTIFACT

# Copy the executable JAR file from the builder image to the new image
COPY --from=builder /app/backend/matching-ws/target/matching-ws-*-SNAPSHOT-exec.jar app.jar

# CSV export from checklistbank and generate the index from the exported CSV if CLB_DATASET_ID is provided
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

USER $USER
EXPOSE $SERVER_PORT

CMD ["java", "-jar", "app.jar"]
