package life.catalogue.csv;

import org.gbif.dwc.terms.AcefTerm;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class AcefReader extends CsvReader {

  private AcefReader(Path folder) throws IOException {
    super(folder, "acef", "acef");
    validate();
    detectMappedClassification(AcefTerm.AcceptedSpecies, Map.of(
        AcefTerm.Kingdom, Rank.KINGDOM,
        AcefTerm.Phylum, Rank.PHYLUM,
        AcefTerm.Class, Rank.CLASS,
        AcefTerm.Order, Rank.ORDER,
        AcefTerm.Superfamily, Rank.SUPERFAMILY,
        AcefTerm.Family, Rank.FAMILY,
        AcefTerm.Genus, Rank.GENUS
      )
    );
  }
  
  public static AcefReader from(Path folder) throws IOException {
    return new AcefReader(folder);
  }
  
  protected void validate() throws SourceInvalidException {
    super.validate();
    // allow only ACEF row types
    filterSchemas(rowType -> rowType instanceof AcefTerm);

    // mandatory terms.
    // Fail early, if missing ignore file alltogether!!!
    require(AcefTerm.CommonNames, AcefTerm.AcceptedTaxonID);
    require(AcefTerm.CommonNames, AcefTerm.CommonName);
    require(AcefTerm.Distribution, AcefTerm.AcceptedTaxonID);
    require(AcefTerm.Distribution, AcefTerm.DistributionElement);
    require(AcefTerm.Synonyms, AcefTerm.AcceptedTaxonID);
    require(AcefTerm.Synonyms, AcefTerm.Genus);
    require(AcefTerm.Synonyms, AcefTerm.SpeciesEpithet);
    require(AcefTerm.AcceptedSpecies, AcefTerm.AcceptedTaxonID);
    require(AcefTerm.AcceptedSpecies, AcefTerm.Genus);
    require(AcefTerm.AcceptedSpecies, AcefTerm.SpeciesEpithet);
    require(AcefTerm.AcceptedInfraSpecificTaxa, AcefTerm.AcceptedTaxonID);
    require(AcefTerm.AcceptedInfraSpecificTaxa, AcefTerm.ParentSpeciesID);
    require(AcefTerm.AcceptedInfraSpecificTaxa, AcefTerm.InfraSpeciesEpithet);
    require(AcefTerm.AcceptedInfraSpecificTaxa, AcefTerm.InfraSpeciesMarker);
    
    // require at least the main accepted species file
    requireSchema(AcefTerm.AcceptedSpecies);
  
    reportMissingSchemas(AcefTerm.class);
  }
  
}
