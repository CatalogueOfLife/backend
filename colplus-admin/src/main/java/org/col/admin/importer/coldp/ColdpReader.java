package org.col.admin.importer.coldp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
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
  private static Set<ColTerm> REFID_SCHEMAS = ImmutableSet.of(
      ColTerm.Description, ColTerm.Distribution, ColTerm.VernacularName
  );

  private ColdpReader(Path folder) throws IOException {
    super(folder, "col");
  }

  public static ColdpReader from(Path folder) throws IOException {
    return new ColdpReader(folder);
  }
  
  protected void validate() throws NormalizationFailedException.SourceInvalidException {
    super.validate();
    
    // allow only COL row types
    filterSchemas(rowType -> rowType instanceof ColTerm);
    
    // mandatory terms.
    // Fail early, if missing ignore file alltogether!!!
    for (ColTerm t : ID_SCHEMAS) {
      require(t, ColTerm.ID);
    }
    for (ColTerm t : NAMEID_SCHEMAS) {
      require(t, ColTerm.nameID);
    }
    require(ColTerm.Name, ColTerm.scientificName);
    require(ColTerm.Name, ColTerm.rank);
    require(ColTerm.NameRel, ColTerm.relatedNameID);
    require(ColTerm.NameRel, ColTerm.type);
  
    requireSchema(ColTerm.Name);
  
    // reference dependencies
    if (!hasSchema(ColTerm.Reference)) {
      LOG.warn("No Reference mapped! Disallow referenceIDs");
      disallow(ColTerm.Name, ColTerm.publishedInID);
      disallow(ColTerm.NameRel, ColTerm.publishedInID);
      for (ColTerm rt : REFID_SCHEMAS) {
        disallow(rt, ColTerm.referenceID);
      }
    }

    Optional<Schema> taxonOpt = schema(ColTerm.Taxon);
    if (taxonOpt.isPresent()) {
      Schema taxon = taxonOpt.get();
      if (taxon.hasTerm(ColTerm.parentID)) {
        mappingFlags.setParentNameMapped(true);
      } else {
        mappingFlags.setParentNameMapped(false);
        LOG.warn("No taxon parentID mapped");
      }
  
      if (taxon.hasAnyTerm(ColTerm.HIGHER_RANKS)) {
        mappingFlags.setDenormedClassificationMapped(true);
        LOG.info("Use denormalized taxon classification");
      } else {
        mappingFlags.setDenormedClassificationMapped(false);
      }
  
      for (ColTerm t : TAXID_SCHEMAS) {
        require(t, ColTerm.taxonID);
      }
  
      require(ColTerm.Description, ColTerm.description);
      require(ColTerm.Distribution, ColTerm.area);
      require(ColTerm.VernacularName, ColTerm.name);
      require(ColTerm.Media, ColTerm.url);
  
      if (hasSchema(ColTerm.Synonym)) {
        mappingFlags.setAcceptedNameMapped(true);
      } else {
        LOG.warn("No Synonyms mapped!");
      }
      
    } else {
      LOG.warn("No Taxa mapped, only inserting names!");
      for (ColTerm t : TAXID_SCHEMAS) {
        schemas.remove(t);
      }
    }
    
    reportMissingSchemas(ColTerm.class);
  }

}
