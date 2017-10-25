package org.col.parser;

import org.gbif.api.vocabulary.TaxonomicStatus;
import org.gbif.common.parsers.TaxStatusParser;

/**
 * Parses integers throwing UnparsableException in case the value is not empty but unparsable.
 */
public class SynonymStatusParser extends GbifParserBased<Boolean, TaxonomicStatus> {
  public static final SynonymStatusParser PARSER = new SynonymStatusParser();

  public SynonymStatusParser() {
    super(Boolean.class, TaxStatusParser.getInstance());
  }

  @Override
  Boolean convertFromGbif(TaxonomicStatus value) {
    return value.isSynonym();
  }

}
