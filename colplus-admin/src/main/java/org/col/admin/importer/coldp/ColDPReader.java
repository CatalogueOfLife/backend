package org.col.admin.importer.coldp;

import java.io.IOException;
import java.nio.file.Path;

import org.col.admin.importer.NormalizationFailedException;
import org.col.api.datapackage.ColTerm;
import org.col.csv.CsvReader;
import org.col.csv.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An archive reader for the CoL Data Package format.
 * https://github.com/Sp2000/coldp
 */
public class ColDPReader extends CsvReader {
  private static final Logger LOG = LoggerFactory.getLogger(ColDPReader.class);

  private ColDPReader(Path folder) throws IOException {
    super(folder, "col");
    validate();
  }

  public static ColDPReader from(Path folder) throws IOException {
    return new ColDPReader(folder);
  }

  private void validate() throws NormalizationFailedException.SourceInvalidException {
    // allow only COL row types
    for (Schema s : schemas.values()) {
      if (!(s.rowType instanceof ColTerm)) {
        LOG.info("Remove non COL rowType {} for file {}", s.rowType, s.file);
        schemas.remove(s.rowType);
      }
    }
  
    // required files
    for (ColTerm rt : new ColTerm[]{ColTerm.Name}) {
      if (!hasData(rt)) {
        throw new NormalizationFailedException.SourceInvalidException(rt + " file required but missing from " + folder);
      }
    }
  
    // mandatory terms.
    // Fail early, if missing ignore file alltogether!!!
    require(ColTerm.Name, ColTerm.ID);
    require(ColTerm.Name, ColTerm.scientificName);
    require(ColTerm.Name, ColTerm.rank);
  
    require(ColTerm.Taxon, ColTerm.ID);
    require(ColTerm.Reference, ColTerm.ID);
    
    for (ColTerm t : ColTerm.values()) {
      if (t.isClass()) {
        if (!hasData(t)) {
          LOG.info("{} missing from ColDP in {}", t.name(), folder);
        }
      }
    }
  }

}
