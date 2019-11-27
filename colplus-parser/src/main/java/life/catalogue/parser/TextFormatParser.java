package life.catalogue.parser;


import life.catalogue.api.vocab.TextFormat;

/**
 * Parses area standards
 */
public class TextFormatParser extends EnumParser<TextFormat> {
  public static final TextFormatParser PARSER = new TextFormatParser();
  
  public TextFormatParser() {
    super("textformat.csv", TextFormat.class);
  }
  
}
