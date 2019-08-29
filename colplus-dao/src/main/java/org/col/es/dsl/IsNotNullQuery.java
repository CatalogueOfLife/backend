package org.col.es.dsl;

public class IsNotNullQuery extends AbstractQuery {

  static class Field {
    final String field;

    Field(String field) {
      this.field = field;
    }
  }

  final Field exists;

  public IsNotNullQuery(String field) {
    this.exists = new Field(field);
  }

}
