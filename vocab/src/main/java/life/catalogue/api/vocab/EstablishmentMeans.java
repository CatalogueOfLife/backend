package life.catalogue.api.vocab;

/**
 * TDWG vocabulary https://dwc.tdwg.org/em/
 *
 * Provides the controlled vocabulary for information about whether an organism or organisms have been introduced
 * to a given place and time through the direct or indirect activity of modern humans.
 */
public enum EstablishmentMeans {
  NATIVE("A taxon occurring within its natural range"),
  NATIVE_ENDEMIC(NATIVE, "A taxon with a natural distribution restricted to a single geographical area."),
  NATIVE_REINTRODUCED(NATIVE, "A taxon re-established by direct introduction by humans into an area which was once part of its natural range, but from where it had become extinct"),
  INTRODUCED("Establishment of a taxon by human agency into an area that is not part of its natural range."),
  INTRODUCED_ASSISTED_COLONISATION(INTRODUCED, "Establishment of a taxon specifically with the intention of creating a self-sustaining wild population in an area that is not part of the taxon's natural range"),
  VAGRANT("The temporary occurrence of a taxon far outside its natural or migratory range"),
  UNCERTAIN("The origin of the occurrence of the taxon in an area is obscure");

  private final EstablishmentMeans parent;
  private final String description;

  EstablishmentMeans(String description) {
    this(null, description);
  }
  EstablishmentMeans(EstablishmentMeans parent, String description) {
    this.parent = parent;
    this.description = description;
  }

  public EstablishmentMeans getParent() {
    return parent;
  }

  public String getDescription() {
    return description;
  }
}
