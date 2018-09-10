package org.col.es.mapping;

import java.util.LinkedHashMap;

/**
 * A {@code KeywordField} is a {@link SimpleField} with Elasticsearch data type
 * {@link ESDataType#KEYWORD keyword}. String fields in API model classes will always be mapped to
 * Elasticsearch document fields of type "keyword".
 */
public class KeywordField extends SimpleField {

  protected String analyzer;
  private LinkedHashMap<String, MultiField> fields;

  public KeywordField() {
    super(ESDataType.KEYWORD);
  }

  public LinkedHashMap<String, MultiField> getMultiFields() {
    return fields;
  }

  public void addMultiField(MultiField field) {
    if (fields == null) {
      fields = new LinkedHashMap<>(2);
    }
    fields.put(field.name, field);
  }

  public boolean hasMultiField(MultiField mf) {
    return fields != null && fields.containsKey(mf.name);
  }

}
