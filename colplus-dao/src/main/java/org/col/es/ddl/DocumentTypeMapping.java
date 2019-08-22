package org.col.es.ddl;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Models an Elasticsearch document type mapping. Serializing it to JSON
 */
@JsonPropertyOrder({"dynamic", "properties"})
public class DocumentTypeMapping extends ComplexField {

  private final String dynamic = "strict"; // Always use strict typing

  DocumentTypeMapping() {
    super();
  }

  /**
   * Returns the value of the type mapping's "dynamic" property. This method will always return "strict", since we prefer strict typing.
   */
  public String getDynamic() {
    return dynamic;
  }

}
