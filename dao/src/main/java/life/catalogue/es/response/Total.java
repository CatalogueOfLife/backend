package life.catalogue.es.response;

/**
 * Object containing the document count. New in ES7; before it was just an integer.
 */
public class Total {

  private int value;
  private String relation;

  public int getValue() {
    return value;
  }

  public String getRelation() {
    return relation;
  }
}
