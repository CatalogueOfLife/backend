package life.catalogue.parser;


import life.catalogue.api.vocab.TaxonomicStatus;

/**
 * Parses TaxonomicStatus
 */
public class TaxonomicStatusParser extends EnumNoteParser<TaxonomicStatus> {
  public static final TaxonomicStatusParser PARSER = new TaxonomicStatusParser();
  static final String HOMOTYPIC_NOTE = "homotypic";
  
  public TaxonomicStatusParser() {
    super("taxstatus.csv", TaxonomicStatus.class);
  }
  
  public static boolean isHomotypic(EnumNote<?> note) {
    return note != null && note.note != null && note.note.equalsIgnoreCase(HOMOTYPIC_NOTE);
  }
  
}
