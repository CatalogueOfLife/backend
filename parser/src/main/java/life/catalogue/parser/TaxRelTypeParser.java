package life.catalogue.parser;

import life.catalogue.api.vocab.TaxRelType;

/**
 *
 */
public class TaxRelTypeParser extends EnumParser<TaxRelType> {
  public static final TaxRelTypeParser PARSER = new TaxRelTypeParser();

  public TaxRelTypeParser() {
    super("taxreltype.csv", TaxRelType.class);
  }
  
}
