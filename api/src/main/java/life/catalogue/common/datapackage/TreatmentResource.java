package life.catalogue.common.datapackage;

public class TreatmentResource extends Resource {
  private static final String DESCRIPTION = """
      Treatment documents are stored as individual files in the treatments/ subdirectory, one file per taxon. 
      Files are named using the taxonID of the associated taxon with a format-specific extension: 
      .html (HTML), .txt (plain text or Markdown), .xml (TaxPub or TaxonX), or .pdf (PDF). 
      There is no tabular treatment file; the directory itself is the resource.
      """;

  public TreatmentResource() {
    setName("treatment");
    setPath("treatments/");
    setEncoding(null);
  }

  public String getDescription() {
    return DESCRIPTION;
  }

  @Override
  public String getProfile() {
    return null;
  }

  @Override
  public String getFormat() {
    return null;
  }
}
