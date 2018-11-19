package org.col.es.query;

public class IsNotNullQuery extends AbstractQuery {

  static class Field {
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
