package life.catalogue.parser;


import life.catalogue.api.vocab.TreatmentFormat;

/**
 * Parses area standards
 */
public class TreatmentFormatParser extends EnumParser<TreatmentFormat> {
  public static final TreatmentFormatParser PARSER = new TreatmentFormatParser();
  
  public TreatmentFormatParser() {
    super("treatmentformat.csv", TreatmentFormat.class);
  }
  
}
