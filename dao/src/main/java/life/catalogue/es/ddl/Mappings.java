package life.catalogue.es.ddl;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The outer-most object of an Elasticsearch document type mapping; the structure associated with the "mappings" field
 * in an index definition. Serializing this object yields a valid document type mapping.
 */
@JsonPropertyOrder({"dynamic", "properties"})
public class Mappings extends ComplexField {

  private final String dynamic = "strict";

  Mappings() {
    super();
  }

  /**
   * Returns the value of the type mapping's "dynamic" property. Will always return "strict", since we use strict typing.
   */
  public String getDynamic() {
    return dynamic;
  }

}
