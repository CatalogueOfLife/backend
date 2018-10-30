package org.col.es.query;

@SuppressWarnings("unused")
public class IsNotNullQuery extends AbstractQuery {

  private static class Field {
    private final String field;

    Field(String field) {
      this.field = field;
    }

    public String getField() {
      return field;
    }
  }

  private final Field exists;

  public IsNotNullQuery(String field) {
    this.exists = new Field(field);
  }

  public Field getExists() {
    return exists;
  }

}
