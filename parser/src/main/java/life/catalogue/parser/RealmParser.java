package life.catalogue.parser;


import life.catalogue.api.vocab.BioGeoRealm;
import life.catalogue.api.vocab.Gazetteer;

/**
 * Parses area standards
 */
public class RealmParser extends EnumParser<BioGeoRealm> {
  public static final RealmParser PARSER = new RealmParser();

  public RealmParser() {
    super("biogeorealm.csv", BioGeoRealm.class);
  }
  
}
