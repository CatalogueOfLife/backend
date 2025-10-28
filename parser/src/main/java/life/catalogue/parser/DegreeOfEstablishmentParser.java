package life.catalogue.parser;


import life.catalogue.api.vocab.DegreeOfEstablishment;

/**
 * Parses TaxonomicStatus
 */
public class DegreeOfEstablishmentParser extends EnumParser<DegreeOfEstablishment> {
  public static final DegreeOfEstablishmentParser PARSER = new DegreeOfEstablishmentParser();

  public DegreeOfEstablishmentParser() {
    super("degreeofestablishment.csv", DegreeOfEstablishment.class);
  }

}
