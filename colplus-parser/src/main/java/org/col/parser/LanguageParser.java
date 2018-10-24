package org.col.parser;

import org.col.api.vocab.Language;
import org.col.api.util.VocabularyUtils;

/**
 * CoL language parser wrapping the GBIF language parser
 */
public class LanguageParser extends GbifParserBased<Language, org.gbif.api.vocabulary.Language> {
  public static final Parser<Language> PARSER = new LanguageParser();

  public LanguageParser() {
    super(Language.class, org.gbif.common.parsers.LanguageParser.getInstance());
  }

  @Override
  Language convertFromGbif(org.gbif.api.vocabulary.Language value) {
    switch (value) {
      case UNKNOWN:
        return null;
      default:
        if (value.getIso2LetterCode() == null) {
          return null;
        }
        return VocabularyUtils.convertEnum(Language.class, value);
    }
  }

}
