# CLB release-in-a-box - the application image.
# Thin on purpose: the multi GB release data is a separate volume, so one image serves any release.
#
#   docker build -f bundle/Dockerfile -t clb-bundle .
#
# Build the shaded jar first with: mvn -DskipTests clean install
# a JDK, not a JRE: chronicle map, behind the names index, compiles classes at runtime and needs jdk.compiler
FROM eclipse-temurin:25-jdk

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

# the shaded jar's Main-Class is WsServer, so the bundle app is named explicitly - and as the jar is not started with
# -jar its manifest grants nothing, so the module flags the servers get from deploy's jvm.args are all given here.
# Chronicle analytics is off: a bundle is meant to run offline and must not report anywhere.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", \
            "--enable-native-access=ALL-UNNAMED", \
            "--sun-misc-unsafe-memory-access=allow", \
            "--add-opens=java.base/java.lang=ALL-UNNAMED", \
            "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED", \
            "--add-opens=java.base/java.io=ALL-UNNAMED", \
            "--add-opens=java.base/java.util=ALL-UNNAMED", \
            "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED", \
            "--add-opens=jdk.compiler/com.sun.tools.javac=ALL-UNNAMED", \
            "--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", \
            "-Dchronicle.analytics.disable=true", \
            "-cp", "/app/app.jar", "life.catalogue.WsBundleServer", "server", "/conf/config.yml"]
