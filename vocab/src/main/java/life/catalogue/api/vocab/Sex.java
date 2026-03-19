package life.catalogue.api.vocab;

public enum Sex {

  FEMALE('♀', "Female (♀) is the sex of an organism, or a part of an organism, " +
    "which produces mobile ova (egg cells)."),

  MALE('♂', "Male (♂) refers to the sex of an organism, or part of an organism, " +
    "which produces small mobile gametes, called spermatozoa."),

  HERMAPHRODITE('⚥',
    "One organism having both male and female sexual characteristics and organs; " +
      "at birth an unambiguous assignment of male or female cannot be made.");

  public final char symbol;
  private final String description;

  Sex(char symbol, String description) {
    this.symbol = symbol;
    this.description = description;
  }

  public char getSymbol() {
    return symbol;
  }

  public String getDescription() {
    return description;
  }
}
