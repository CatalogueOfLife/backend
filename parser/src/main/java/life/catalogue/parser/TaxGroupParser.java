package life.catalogue.parser;


import life.catalogue.api.vocab.TaxGroup;

/**
 * Parses TaxGroup from various known taxon names.
 * This parser does not throw unparsable exceptions but instead returns an empty optional.
 */
public class TaxGroupParser extends EnumParser<TaxGroup> {
  public static final TaxGroupParser PARSER = new TaxGroupParser();

  public TaxGroupParser() {
    super("taxgroup.csv", false, TaxGroup.class);
  }

}
