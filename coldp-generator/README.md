# ColDP Archive Generator

Conversion tools to create [ColDP archives](https://github.com/CatalogueOfLife/coldp/) from various online sources not readily available otherwise.
The conversion is fully automated so it can run in a scheduler.

To build the jar (once) and then generate a WCVP archive with it: 

```
mvn package -pl coldp-generator -am 
java -jar coldp-generator/target/coldp-generator-1.0-SNAPSHOT.jar -s wcvp
```