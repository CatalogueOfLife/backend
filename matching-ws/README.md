## Matching index and web services

The matching index and web services are implemented in Java using Spring Boot.

Build executable jar file
```bash
mvn clean install spring-boot:repackage
```

Build docker image
```bash
docker buildx build \
--platform linux/amd64 . -t matching-ws:v1 
```

Locally running docker image
```bash
sudo docker run -d --platform linux/arm64 \
--name matching-ws \
-v /tmp/matching-ws/index:/data/matching/index matching-ws:v1
```

### Usage

```bash 
java -jar target/matching-ws-1.0-SNAPSHOT-exec.jar 
--mode=INDEX_CSV --index.path=/tmp/my-worms-index/ --export.path=/data/matching-ws/export/2011
```

