package life.catalogue.parser;

import life.catalogue.api.vocab.TaxonConceptRelType;

/**
 *
 */
public class TaxonConceptRelTypeParser extends EnumParser<TaxonConceptRelType> {
  public static final TaxonConceptRelTypeParser PARSER = new TaxonConceptRelTypeParser();

  public TaxonConceptRelTypeParser() {
    super("taxonconceptreltype.csv", TaxonConceptRelType.class);
  }
  
}
