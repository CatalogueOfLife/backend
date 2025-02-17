# Use an official Maven runtime as a parent image
FROM maven:3.8.5-openjdk-11 AS builder

# Set the working directory in the container
WORKDIR /app

ARG CLB_DATASET_ID=""
ARG CLB_URL=""
ARG CLB_USER=""
ARG CLB_PASSWORD=""
ENV CLB_API_URL="https://api.checklistbank.org"
ARG GIT_BRANCH=""

# Local builds - run from root of backend
#COPY .. /app/backend
#WORKDIR /app/backend

# Run a script to validate the arguments
RUN if [ -z "$GIT_BRANCH" ]; then \
        echo "Error: GIT_BRANCH is not set." >&2; \
        exit 1; \
    fi

# Clone the backend repository
# Remove unnecessary 'rm -Rf backend' as it is redundant (git clone will overwrite if needed)
RUN git clone --depth 1 -b $GIT_BRANCH https://github.com/CatalogueOfLife/backend.git /app/backend

WORKDIR /app/backend/matching-ws

# Combine mvn commands into a single step to minimize layers
RUN mvn package -DskipTests -DskipITs

# Store git commit id and log
RUN curl -o /app/backend/git.json -H "Accept: application/vnd.github+json" "https://api.github.com/repos/catalogueoflife/backend/commits/$(git rev-parse HEAD)"

RUN mkdir -p /data/$APP_ARTIFACT

# Run a script to validate the arguments
RUN if [ -n "$CLB_DATASET_ID" ]; then \
        if [ -z "$CLB_URL" ] || [ -z "$CLB_USER" ] || [ -z "$CLB_PASSWORD" ]; then \
            echo "Error: CLB_URL, CLB_USER, and CLB_PASSWORD must be set when CLB_DATASET_ID is set." >&2; \
            exit 1; \
        fi \
    fi

# Cache a copy of the dataset metadata from checklistbank for tracking
RUN if [ -n "$CLB_DATASET_ID" ]; then \
        curl -o /app/backend/dataset.json $CLB_API_URL/dataset/$CLB_DATASET_ID.json; \
    else \
        echo "{}" > /app/backend/dataset.json; \
    fi

# CSV export from checklistbank and build index
RUN if [ -n "$CLB_DATASET_ID" ]; then \
        java $JVM_OPTIONS -jar /app/backend/matching-ws/target/matching-ws-1.0-SNAPSHOT-exec.jar  \
        --spring.cloud.bootstrap.location=/app/backend/matching-ws/src/main/resources/bootstrap.yaml \
        --mode=INDEX \
        --index.path=/data/$APP_ARTIFACT/index \
        --export.path=/data/$APP_ARTIFACT/exports \
        --clb.dataset.id=$CLB_DATASET_ID \
        --clb.identifier.dataset.ids=$CLB_IDENTIFIER_DATASET_IDS \
        --clb.iucn.dataset.id=$CLB_IUCN_DATASET_ID \
        --clb.url=$CLB_URL \
        --clb.user=$CLB_USER \
        --clb.password=$CLB_PASSWORD && \
        tar zcvf /data/$APP_ARTIFACT/exports.tgz /data/$APP_ARTIFACT/exports && \
        rm -Rf /data/$APP_ARTIFACT/exports; \
    fi

RUN echo "spring.cloud.zookeeper.discovery.metadata.timestamp=$(date +%s%3N)" > /tmp/timestamp.properties

# Copy the executable JAR file from the builder image to the new image
FROM openjdk:11-jdk-slim-sid

# Set environment variables
ARG DEBIAN_FRONTEND=noninteractive
ENV SERVER_PORT=8080
ENV SERVER_ADMIN_PORT=8081
ENV RPC_PORT=9090
ENV JVM_OPTIONS="-Xms1g -Xmx4g"
ENV SERVLET_CONTEXT_PATH="/"
ENV USER=matching
ENV APP_ARTIFACT=matching-ws

ENV APP_NAME=matching-ws
ENV APP_VERSION="1.0-SNAPSHOT"
ENV ZK_ENABLED="false"
ENV ZK_ARTIFACT_ID="matching-ws"
ENV ZK_GROUP_ID="org.catalogueoflife"
ENV ZK_STOP_SECRET="stop"
ENV ZK_CONNECT_STRING=""
ENV ZK_DISCOVERY_ROOT=""
ENV SERVICE_URL=""
ENV SPRING_BOOT_ADMIN_URL=""
ENV SPRING_BOOT_ADMIN_AUTO_DEREGISTRATION="true"
ENV SPRING_BOOT_ADMIN_TAG_ENV=""
ENV SPRING_BOOT_DISCOVERY_METADATA_HOST=""
ENV SPRING_BOOT_DISCOVERY_METADATA_PATH=""

# Directories and perms
RUN mkdir -p /data/$APP_ARTIFACT && \
    groupadd -r $USER -g 1000 && useradd -r -g $USER -u 1000 -m $USER && \
    chown -R $USER:$USER /data/$APP_ARTIFACT

# Set the working directory in the container
WORKDIR /opt/gbif/$APP_ARTIFACT

# Copy the executable JAR file from the builder image to the new image
COPY --from=builder /app/backend/matching-ws/target/matching-ws-*-exec.jar /opt/gbif/$APP_ARTIFACT/app.jar
COPY --from=builder /app/backend/matching-ws/src/main/resources/bootstrap.yaml /opt/gbif/$APP_ARTIFACT/bootstrap.yaml
COPY --from=builder /app/backend/git.json /opt/gbif/$APP_ARTIFACT/git.json
COPY --from=builder /app/backend/dataset.json /opt/gbif/$APP_ARTIFACT/dataset.json
COPY --from=builder /data/$APP_ARTIFACT /data/$APP_ARTIFACT
COPY --from=builder /tmp/timestamp.properties /tmp/timestamp.properties

EXPOSE $SERVER_PORT $SERVER_ADMIN_PORT $RPC_PORT

USER $USER

CMD java $JVM_OPTIONS \
    -jar app.jar \
    --server.servlet.context-path=$SERVLET_CONTEXT_PATH \
    --server.port=$SERVER_PORT \
    --working.dir=/opt/gbif/$APP_ARTIFACT/ \
    --spring.application.name=$APP_NAME \
    --spring.cloud.zookeeper.enabled=$ZK_ENABLED \
    --spring.cloud.zookeeper.config.enabled=$ZK_ENABLED \
    --spring.cloud.zookeeper.connect-string=$ZK_CONNECT_STRING \
    --spring.cloud.zookeeper.discovery.enabled=$ZK_ENABLED \
    --spring.cloud.zookeeper.discovery.root=$ZK_DISCOVERY_ROOT \
    --spring.cloud.zookeeper.discovery.metadata.version=$APP_VERSION \
    --spring.cloud.zookeeper.discovery.metadata.groupId=$ZK_GROUP_ID \
    --spring.cloud.zookeeper.discovery.metadata.stopSecret=$ZK_STOP_SECRET  \
    --spring.cloud.zookeeper.discovery.metadata.artifactId=$ZK_ARTIFACT_ID \
    --spring.cloud.zookeeper.discovery.metadata.host=$SPRING_BOOT_DISCOVERY_METADATA_HOST \
    --spring.cloud.zookeeper.discovery.metadata.path=$SPRING_BOOT_DISCOVERY_METADATA_PATH \
    --spring.cloud.zookeeper.discovery.metadata.httpPort=$SERVER_PORT \
    --spring.cloud.zookeeper.discovery.metadata.httpAdminPort=$SERVER_ADMIN_PORT \
    --spring.cloud.zookeeper.discovery.metadata.zkHost=$ZK_CONNECT_STRING \
    --spring.cloud.zookeeper.discovery.metadata.zkPath=$ZK_DISCOVERY_ROOT \
    --spring.boot.admin.client.url=$SPRING_BOOT_ADMIN_URL \
    --spring.boot.admin.client.instance.service-url=$SERVICE_URL \
    --spring.boot.admin.client.auto-deregistration=$SPRING_BOOT_ADMIN_AUTO_DEREGISTRATION \
    --spring.boot.admin.client.instance.metadata.tags.env=$SPRING_BOOT_ADMIN_TAG_ENV \
    --spring.boot.admin.client.instance.metadata.management.port=$SERVER_ADMIN_PORT \
    --spring.config.additional-location=/tmp/timestamp.properties \
    --mode=RUN

