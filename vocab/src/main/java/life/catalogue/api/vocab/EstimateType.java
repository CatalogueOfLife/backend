package life.catalogue.api.vocab;

public enum EstimateType {

  SPECIES_LIVING("Number of living species which are recognised by science at present time on a global scale and have been described by a Linnean name. Estimates do not include fossil species."),
  
  SPECIES_EXTINCT("Number of extinct species which are recognised by science at present time on a global scale and have been described by a Linnean name. Estimates do not include living species."),
  
  ESTIMATED_SPECIES("Number of living species which are estimated to exist on a global scale, including both already described and yet to be discovered species.");


  private final String description;

  EstimateType(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
