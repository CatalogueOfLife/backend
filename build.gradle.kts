plugins {
    `java-platform`
    `maven-publish`
    id("com.gradle.build-scan") version "2.1"
    id("com.gorylenko.gradle-git-properties") version "1.5.2" apply false
    id("pl.allegro.tech.build.axion-release") version "1.9.3" apply false
    id("com.github.hauner.jarTest") version "1.0" apply false
}

buildScan {
    termsOfServiceUrl   = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"
}

allprojects {
    group   = "org.col"
    version = "1.0-SNAPSHOT"
}

dependencies {
    constraints {
        api(project(":colplus-api"))
        api(project(":colplus-dao"))
        api(project(":colplus-parser"))

        // external BOMs
        api("io.dropwizard:dropwizard-bom:1.3.12")
        // regular deps
        api("ch.qos.logback:logback-classic:1.2.3")
        api("com.esotericsoftware:kryo:4.0.1")
        api("com.google.code.findbugs:jsr305:3.0.2")
        api("com.google.guava:guava:24.1.1-jre")
        api("com.ibm.icu:icu4j:63.1")
        api("com.twelvemonkeys.imageio:imageio-bmp:3.4.1")
        api("com.twelvemonkeys.imageio:imageio-icns:3.4.1")
        api("com.twelvemonkeys.imageio:imageio-jpeg:3.4.1")
        api("com.twelvemonkeys.imageio:imageio-pict:3.4.1")
        api("com.twelvemonkeys.imageio:imageio-psd:3.4.1")
        api("com.twelvemonkeys.imageio:imageio-tiff:3.4.1")
        api("com.univocity:univocity-parsers:2.7.6")
        api("com.zaxxer:HikariCP:3.2.0")
        api("commons-io:commons-io:2.6")
        api("de.javakaffee:kryo-serializers:0.42")
        api("de.undercouch:citeproc-java:1.0.1")
        //api("io.dropwizard.metrics:metrics-core:4.0.5")
        api("io.github.java-diff-utils:java-diff-utils:4.0")
        api("io.jsonwebtoken:jjwt:0.7.0")
        api("it.unimi.dsi:fastutil:8.2.1")
        api("javax.validation:validation-api:2.0.0.Final")
        api("javax.ws.rs:javax.ws.rs-api:2.0.1")
        api("junit:junit:4.12")
        api("net.logstash.logback:logstash-logback-encoder:5.1")
        api("org.apache.commons:commons-collections4:4.3")
        api("org.apache.commons:commons-compress:1.18")
        api("org.apache.commons:commons-lang3:3.8.1")
        api("org.apache.commons:commons-text:1.2")
        api("org.apache.httpcomponents:httpclient:4.5.7")
        api("org.apache.tika:tika-core:1.20")
        api("org.assertj:assertj-core:3.11.1")
        api("org.citationstyles:locales:1.0")
        api("org.citationstyles:styles:1.0")
        api("org.codehaus.woodstox:stax2-api:3.1.4")
        api("org.codehaus.woodstox:woodstox-core-asl:4.4.1")
        //api("org.eclipse.jetty:jetty-util")#
        api("org.elasticsearch.client:elasticsearch-rest-client:6.5.1")
        api("org.gbif:dwc-api:1.21")
        api("org.gbif:gbif-api:0.68")
        api("org.gbif:gbif-parsers:0.35")
        api("org.gbif:name-parser-api:3.1.7-SNAPSHOT")
        api("org.gbif:name-parser:3.1.7-SNAPSHOT")
        api("org.hashids:hashids:1.0.3")
        api("org.imgscalr:imgscalr-lib:4.2")
        api("org.javers:javers-core:3.10.0")
        api("org.mapdb:mapdb:3.0.5")
        api("org.mockito:mockito-core:2.12.0")
        api("org.mybatis:mybatis:3.4.6")
        api("org.neo4j:graph-algorithms-algo:3.4.0.0")
        api("org.neo4j:neo4j:3.4.3")
        api("org.neo4j:neo4j-cypher:3.4.3")
        api("org.neo4j:neo4j-kernel:3.4.3")
        api("org.neo4j:neo4j-shell:3.4.3")
        api("org.neo4j:neo4j-slf4j:3.4.3")
        api("org.postgresql:postgresql:42.2.5")
        api("org.slf4j:jcl-over-slf4j:1.7.26")
        api("org.slf4j:jul-to-slf4j:1.7.26")
        api("org.slf4j:slf4j-api:1.7.26")
        api("pl.allegro.tech:embedded-elasticsearch:2.8.0")
    }
}

publishing {
    publications {
        create<MavenPublication>("colplus") {
            from(components["javaPlatform"])
        }
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "com.gorylenko.gradle-git-properties")
    apply(plugin = "pl.allegro.tech.build.axion-release")
    // build test jars
    apply(plugin = "com.github.hauner.jarTest")


    repositories {
        mavenLocal()
        mavenCentral()
        maven(url = "http://repository.gbif.org/content/groups/gbif")
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "1.8"
        options.encoding = "UTF-8"
    }

    tasks.register<Jar>("sourcesJar") {
        archiveClassifier.set("sources")
        from(project.the<SourceSetContainer>()["main"].allJava)
    }

    publishing {
        publications {
            create<MavenPublication>(project.name) {
                from(components["java"])
                artifact(tasks["sourcesJar"])
                artifact(tasks["jarTest"]) // from jarTest plugin
                //artifact(tasks["testsJar"])
            }
        }
        repositories {
            maven(url = "http://repository.gbif.org/content/groups/gbif")
        }
    }

}
