package org.col.es.ddl;

/**
 * A {@code DateField} is a {@link SimpleField} with Elasticsearch data type {@link ESDataType#DATE date}.
 */
public class DateField extends SimpleField {

  private String format = "yyyy-MM-dd'T'HH:mm:ssZ";

  public DateField() {
    super(ESDataType.DATE);
  }

  public DateField(Boolean indexed) {
    super(ESDataType.DATE, indexed);
  }

  public String getFormat() {
    return format;
  }

  public void setFormat(String format) {
    this.format = format;
  }

}
