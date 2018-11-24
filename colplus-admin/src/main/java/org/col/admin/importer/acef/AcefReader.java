package org.col.admin.importer.acef;

import java.io.IOException;
import java.nio.file.Path;

import org.col.admin.importer.NormalizationFailedException;
import org.col.csv.CsvReader;
import org.gbif.dwc.terms.AcefTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class AcefReader extends CsvReader {
  private static final Logger LOG = LoggerFactory.getLogger(AcefReader.class);
  
  private AcefReader(Path folder) throws IOException {
    super(folder, "acef", "acef");
    validate();
  }
  
  public static AcefReader from(Path folder) throws IOException {
    return new AcefReader(folder);
  }
  
  protected void validate() throws NormalizationFailedException.SourceInvalidException {
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
