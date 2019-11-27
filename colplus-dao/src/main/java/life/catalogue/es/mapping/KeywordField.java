package life.catalogue.es.mapping;

import java.util.LinkedHashMap;

/**
 * A {@code KeywordField} is a {@link SimpleField} with Elasticsearch data type "keyword". String and enum fields in API model classes will
 * always be mapped to Elasticsearch fields of type "keyword", meaning they will always at least be indexed as-is.
 */
public class KeywordField extends SimpleField {

  protected String analyzer;
  private LinkedHashMap<String, MultiField> fields;

  public KeywordField() {
    super(ESDataType.KEYWORD);
  }

  public KeywordField(Boolean index) {
    super(ESDataType.KEYWORD, index);
  }

  public LinkedHashMap<String, MultiField> getMultiFields() {
    return fields;
  }

  public void addMultiField(MultiField field) {
    if (fields == null) {
      fields = new LinkedHashMap<>();
    }
    fields.put(field.name, field);
  }

  public boolean hasMultiField(MultiField mf) {
    return fields != null && fields.containsKey(mf.name);
  }

}
