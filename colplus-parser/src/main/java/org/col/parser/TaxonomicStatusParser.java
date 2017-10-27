package org.col.parser;


import org.col.api.vocab.TaxonomicStatus;

/**
 * Parses TaxonomicStatus
 */
public class TaxonomicStatusParser extends EnumParser<TaxonomicStatus> {
  public static final TaxonomicStatusParser PARSER = new TaxonomicStatusParser();

  public TaxonomicStatusParser() {
    super("taxstatus.csv", TaxonomicStatus.class);
  }

}
