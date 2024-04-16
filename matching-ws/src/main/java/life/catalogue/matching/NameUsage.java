package life.catalogue.matching;

import lombok.Data;

/** A simple class to represent a name usage ready to be indexed. */
@Data
public class NameUsage {
  String id;
  String parentId;
  String scientificName;
  String authorship;
  String status;
  String rank;
  String nomenclaturalCode;
}
