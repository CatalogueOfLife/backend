package life.catalogue.parser;


import life.catalogue.api.vocab.BioGeoRealm;

/**
 * Parses area standards
 */
public class RealmParser extends EnumParser<BioGeoRealm> {
  public static final RealmParser PARSER = new RealmParser();

  public RealmParser() {
    super("biogeorealm.csv", BioGeoRealm.class);
  }
  
}
