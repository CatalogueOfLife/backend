# Use an official Maven runtime as a parent image
FROM maven:3.8.5-openjdk-17 AS builder

# Set the working directory in the container
WORKDIR /app

ENV BACKEND_BRANCH="matching-ws"

# Clone the backend repository
RUN git clone https://github.com/CatalogueOfLife/backend.git
WORKDIR /app/backend
RUN git checkout $BACKEND_BRANCH

# Build all the CLB modules
RUN mvn install -DskipTests

# Store git commit id and log
RUN git rev-parse HEAD > /app/backend/commit_id
# Download and install jq
RUN curl -o /usr/local/bin/jq https://github.com/jqlang/jq/releases/download/jq-1.7.1/jq-linux64 && \
    chmod +x /usr/local/bin/jq
RUN git log --all --pretty=format:"%h%x09%an%x09%ad%x09%s" --date=short --no-merges | \
  jq -R '[ inputs | split("\t") | { hash: .[0], author: .[1], date: .[2], message: .[3] }]' > /app/backend/commit_log.json

RUN curl -o /app/backend/dataset.json https://api.checklistbank.org/dataset/53147.json

# Build the Maven project and create a exec file
WORKDIR /app/backend/matching-ws
RUN mvn install spring-boot:repackage

# Copy the executable JAR file from the builder image to the new image
FROM openjdk:17.0.1-jdk-slim

# Set environment variables
ARG DEBIAN_FRONTEND=noninteractive
ENV SERVER_PORT=8080
ARG USER=matching
ARG APP_ARTIFACT=matching-ws

# Set environment variables
ARG CLB_DATASET_ID=""
ARG CLB_URL=""
ARG CLB_USER=""
ARG CLB_PASSWORD=""
ARG V1_ENABLED="false"

# Directories and perms
RUN mkdir -p /data/$APP_ARTIFACT && \
    groupadd -r $USER -g 1000 && useradd -r -g $USER -u 1000 -m $USER && \
    chown -R $USER:$USER /data/$APP_ARTIFACT

# Set the working directory in the container
WORKDIR /opt/gbif/$APP_ARTIFACT

# Copy the executable JAR file from the builder image to the new image
COPY --from=builder /app/backend/matching-ws/target/matching-ws-*-SNAPSHOT-exec.jar /opt/gbif/$APP_ARTIFACT/app.jar
COPY --from=builder /app/backend/commit_id /opt/gbif/$APP_ARTIFACT/commit_id
COPY --from=builder /app/backend/commit_log.json /opt/gbif/$APP_ARTIFACT/commit_log.json
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

USER $USER
EXPOSE $SERVER_PORT

CMD ["java", "-jar", "app.jar", "--server.port=$SERVER_PORT", "--v1.enabled=$V1_ENABLED"]