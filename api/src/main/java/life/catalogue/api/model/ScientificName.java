package life.catalogue.api.model;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;

public interface ScientificName {

  String getScientificName();

  String getAuthorship();

  NomCode getCode();

  Authorship getCombinationAuthorship();

  Authorship getBasionymAuthorship();

}
