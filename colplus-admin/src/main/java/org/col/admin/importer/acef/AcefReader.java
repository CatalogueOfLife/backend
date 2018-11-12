package org.col.admin.importer.acef;

import java.io.IOException;
import java.nio.file.Path;

import org.col.admin.importer.NormalizationFailedException;
import org.col.csv.CsvReader;
import org.col.csv.Schema;
import org.gbif.dwc.terms.AcefTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class AcefReader extends CsvReader {
  private static final Logger LOG = LoggerFactory.getLogger(AcefReader.class);
  
  private AcefReader(Path folder) throws IOException {
    super(folder, "acef");
    validate();
  }
  
  public static AcefReader from(Path folder) throws IOException {
    return new AcefReader(folder);
  }
  
  private void validate() throws NormalizationFailedException.SourceInvalidException {
    // allow only ACEF row types
    for (Schema s : schemas.values()) {
      if (!(s.rowType instanceof AcefTerm)) {
        LOG.info("Remove non ACEF rowType {} for file {}", s.rowType, s.file);
        schemas.remove(s.rowType);
      }
    }
    
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
    if (!hasData(AcefTerm.AcceptedSpecies)) {
      throw new NormalizationFailedException.SourceInvalidException(AcefTerm.AcceptedSpecies + " file required but missing from " + folder);
    }
    
    for (AcefTerm t : AcefTerm.values()) {
      if (t.isClass()) {
        if (!hasData(t)) {
          LOG.info("{} missing from ACEF in {}", t.name(), folder);
        }
      }
    }
  }
  
}
