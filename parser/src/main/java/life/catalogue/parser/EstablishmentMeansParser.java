package life.catalogue.parser;


import life.catalogue.api.vocab.EstablishmentMeans;

public class EstablishmentMeansParser extends EnumParser<EstablishmentMeans> {
  public static final EstablishmentMeansParser PARSER = new EstablishmentMeansParser();

  public EstablishmentMeansParser() {
    super("establishmentmeans.csv", EstablishmentMeans.class);
  }

}
