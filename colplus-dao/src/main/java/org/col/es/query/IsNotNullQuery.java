package org.col.es.query;

@SuppressWarnings("unused")
public class IsNotNullQuery extends AbstractQuery {

  private static class Field {
    final String field;

    Field(String field) {
      this.field = field;
    }
  }

  private final Field exists;

  public IsNotNullQuery(String field) {
    this.exists = new Field(field);
  }

  Field getExists() {
    return exists;
  }

}
