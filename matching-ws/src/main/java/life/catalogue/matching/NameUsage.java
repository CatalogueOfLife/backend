package life.catalogue.matching;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** A simple class to represent a name usage ready to be indexed. */
@Data
@EqualsAndHashCode
@Builder
public class NameUsage {
  String id;
  String parentId;
  String scientificName;
  String authorship;
  String status;
  String rank;
  String nomenclaturalCode;
}
