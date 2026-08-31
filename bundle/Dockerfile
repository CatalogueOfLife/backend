# CLB release-in-a-box - the application image.
# Thin on purpose: the multi GB release data is a separate volume, so one image serves any release.
#
#   docker build -f bundle/Dockerfile -t clb-bundle .
#
# Build the shaded jar first with: mvn -DskipTests clean install
FROM eclipse-temurin:25-jre

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app
# the build produces sources/tests/original jars next to the shaded one, so pick the shaded one explicitly
COPY webservice/target/webservice-*.jar /app/jars/
RUN set -eu; \
    jar=$(ls /app/jars/*.jar | grep -Ev -- '-(sources|tests|javadoc)\.jar$' | head -1); \
    test -n "$jar"; \
    mv "$jar" /app/app.jar; \
    rm -rf /app/jars

# 8080 = API, 8081 = dropwizard admin (healthcheck, metrics)
EXPOSE 8080 8081

# the shaded jar's Main-Class is WsServer, so the bundle app is named explicitly
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "--enable-native-access=ALL-UNNAMED", \
            "-cp", "/app/app.jar", "life.catalogue.WsBundleServer", "server", "/conf/config.yml"]
