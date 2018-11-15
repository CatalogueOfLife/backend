package org.col.admin.importer.coldp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.col.admin.importer.NormalizationFailedException;
import org.col.api.datapackage.ColTerm;
import org.col.csv.CsvReader;
import org.col.csv.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ColdpReader extends CsvReader {
  private static final Logger LOG = LoggerFactory.getLogger(ColdpReader.class);
  private static Set<ColTerm> ID_SCHEMAS = ImmutableSet.of(ColTerm.Reference, ColTerm.Name, ColTerm.Taxon);
  private static Set<ColTerm> NAMEID_SCHEMAS = ImmutableSet.of(ColTerm.Synonym, ColTerm.Taxon, ColTerm.NameRel);
  private static Set<ColTerm> TAXID_SCHEMAS = ImmutableSet.of(
      ColTerm.Synonym, ColTerm.Description, ColTerm.Distribution, ColTerm.Media, ColTerm.VernacularName
  );

  private ColdpReader(Path folder) throws IOException {
    super(folder, "col");
    validate();
  }

  public static ColdpReader from(Path folder) throws IOException {
    return new ColdpReader(folder);
  }

  private void validate() throws NormalizationFailedException.SourceInvalidException {
    // allow only COL row types
    for (Schema s : schemas.values()) {
      if (!(s.rowType instanceof ColTerm)) {
        LOG.info("Remove non COL rowType {} for file {}", s.rowType, s.file);
        schemas.remove(s.rowType);
      }
    }

    // mandatory terms.
    // Fail early, if missing ignore file alltogether!!!
    for (ColTerm t : ID_SCHEMAS) {
      require(t, ColTerm.ID);
    }
    for (ColTerm t : NAMEID_SCHEMAS) {
      require(t, ColTerm.nameID);
    }
    for (ColTerm t : TAXID_SCHEMAS) {
      require(t, ColTerm.taxonID);
    }
    require(ColTerm.Name, ColTerm.scientificName);
    require(ColTerm.Name, ColTerm.rank);
    require(ColTerm.NameRel, ColTerm.relatedNameID);
    require(ColTerm.NameRel, ColTerm.type);
    require(ColTerm.Description, ColTerm.description);
    require(ColTerm.Distribution, ColTerm.area);
    require(ColTerm.VernacularName, ColTerm.name);
    require(ColTerm.Media, ColTerm.url);
  
    requireSchema(ColTerm.Name);
  
    reportMissingSchemas(ColTerm.class);
  }

}
