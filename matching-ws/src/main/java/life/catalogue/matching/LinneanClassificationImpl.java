package life.catalogue.matching;

import lombok.Data;

@Data
public class LinneanClassificationImpl implements LinneanClassification {
  String kingdom;
  String phylum;
  String clazz;
  String order;
  String family;
  String genus;
  String subgenus;
  String species;
}