package life.catalogue.es.ddl;

import java.util.LinkedHashMap;

/**
 * A {@code KeywordField} is a {@link SimpleField} with Elasticsearch data type "keyword". Stringy types in the domain model are always
 * mapped to the keyword datatype, even if they must not actually be indexed in an as-is manner (using the {@link Analyzer#KEYWORD keyword
 * analyzer}). Alternative ways of indexing a string field (i.e. using ngrams) are expressed through Elasticserch's "multifield" feature. If
 * a string field is not to be indexed as-is, but only, say, using ngrams, its "index" property is set to "no" (not indexed) and it really
 * just becomes a ghostly hook for attaching the multifields.
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
