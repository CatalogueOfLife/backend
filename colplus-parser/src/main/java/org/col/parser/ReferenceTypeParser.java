package org.col.parser;


/**
 * Parses ACEF ReferenceType values
 */
public class ReferenceTypeParser extends EnumParser<ReferenceTypeParser.ReferenceType> {
  public static final ReferenceTypeParser PARSER = new ReferenceTypeParser();
  
  public enum ReferenceType {
    NomRef,
    TaxAccRef,
    ComNameRef
  }
  
  ;
  
  public ReferenceTypeParser() {
    super("referencetype.csv", ReferenceType.class);
  }
  
}
