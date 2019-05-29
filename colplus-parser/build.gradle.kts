
description = "CoL backend services (Parser)"

dependencies {
    api(platform(rootProject))
    implementation(platform("io.dropwizard:dropwizard-bom"))
    compile(project(":colplus-api"))
    compile("org.gbif:gbif-api")
    compile("org.gbif:gbif-parsers")
    compile("org.gbif:name-parser")
    compile("org.gbif:name-parser-api")
    compile("com.google.guava:guava")
    compile("com.google.code.findbugs:jsr305")
    compile("org.apache.commons:commons-lang3")
    compile("io.dropwizard.metrics:metrics-core")
    compile("org.slf4j:slf4j-api")
    testCompile("junit:junit")
    testCompile("ch.qos.logback:logback-classic")
}
