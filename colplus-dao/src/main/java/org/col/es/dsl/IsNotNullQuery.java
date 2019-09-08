package org.col.es.dsl;

@SuppressWarnings("unused")
public class IsNotNullQuery extends AbstractQuery {

  private static class Field {
    private final String field;

    Field(String field) {
      this.field = field;
    }
  }

  private final Field exists;

  public IsNotNullQuery(String field) {
    this.exists = new Field(field);
  }

}
