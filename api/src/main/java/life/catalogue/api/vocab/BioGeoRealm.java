package life.catalogue.api.vocab;

/**
 * https://en.wikipedia.org/wiki/Biogeographic_realm
 */
public enum BioGeoRealm implements Area {
  Palearctic,
  Nearctic,
  Afrotropic,
  Neotropic,
  Australasia,
  Indomalaya,
  Oceania,
  Antarctic;

  @Override
  public Gazetteer getGazetteer() {
    return Gazetteer.REALM;
  }

  @Override
  public String getId() {
    return name();
  }

  @Override
  public String getName() {
    return name();
  }
}
