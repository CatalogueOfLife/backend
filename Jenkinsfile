pipeline {
  agent any
  tools {
    maven 'Maven 3.9.9'
    jdk 'LibericaJDK21'
  }
  options {
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '6'))
    skipStagesAfterUnstable()
    timestamps()
  }
  stages {
    stage('Maven build') {
      steps {
        withMaven(
          globalMavenSettingsConfig: 'org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig1387378707709',
          mavenOpts: '-Xmx2048m -Dorg.slf4j.simpleLogger.showDateTime=true -Dorg.slf4j.simpleLogger.dateTimeFormat=HH:mm:ss,SSS',
          mavenSettingsConfig: 'b043019e-79d8-48fd-8ecf-b20e3fb0a3cc',
          traceability: true
        ) {
          sh '''mvn clean -U -T 4 verify'''
        }
      }
    }
  }

  post {
    success {
      echo 'Pipeline executed successfully!'
    }
    failure {
      echo 'Pipeline execution failed!'
    }
  }
}