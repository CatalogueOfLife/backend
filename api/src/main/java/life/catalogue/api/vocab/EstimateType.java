package life.catalogue.api.vocab;

public enum EstimateType {

  DESCRIBED_SPECIES_LIVING("Estimate number of all extant species described with a linnean name."),
  
  DESCRIBED_SPECIES_EXTINCT("Estimate number of all extinct species incl fossils described with a linnean name."),
  
  ESTIMATED_SPECIES("Estimate number of all species including both described and yet to be discovered species.");


  private final String description;

  EstimateType(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
