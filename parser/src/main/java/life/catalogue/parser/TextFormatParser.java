package life.catalogue.parser;


import life.catalogue.api.vocab.TreatmentFormat;

/**
 * Parses area standards
 */
public class TextFormatParser extends EnumParser<TreatmentFormat> {
  public static final TextFormatParser PARSER = new TextFormatParser();
  
  public TextFormatParser() {
    super("textformat.csv", TreatmentFormat.class);
  }
  
}
