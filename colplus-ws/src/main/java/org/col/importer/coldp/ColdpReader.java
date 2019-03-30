package org.col.importer.coldp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.col.api.datapackage.ColTerm;
import org.col.common.io.PathUtils;
import org.col.csv.CsvReader;
import org.col.csv.Schema;
import org.col.importer.NormalizationFailedException;
import org.gbif.dwc.terms.TermFactory;
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
  static {
    // make sure we are aware of ColTerms
    TermFactory.instance().registerTermEnum(ColTerm.class);
  }
  
  private File bibtex;
  private File cslJson;

  private ColdpReader(Path folder) throws IOException {
    super(folder, "col", "coldp");
  }

  public static ColdpReader from(Path folder) throws IOException {
    return new ColdpReader(folder);
  }
  
  @Override
  protected void discoverMoreSchemas(Path dir) throws IOException {
    // spot bibtex & csl-json
    for (Path df : listFiles(dir)) {
      if (PathUtils.getFilename(df).equalsIgnoreCase("reference.bib")) {
        bibtex  = df.toFile();
        LOG.info("BibTeX file found: {}", bibtex.getAbsolutePath());
      
      } else if (PathUtils.getFilename(df).equalsIgnoreCase("reference.json")) {
        cslJson = df.toFile();
        LOG.info("CSL-JSON file found: {}", cslJson.getAbsolutePath());
      }
    }
  }
  
  public boolean hasExtendedReferences() {
    return bibtex != null || cslJson != null;
  }

  private boolean hasReferences() {
    return hasExtendedReferences() || hasSchema(ColTerm.Reference);
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
    if (!hasReferences()) {
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
  
  public File getBibtexFile() {
    return bibtex;
  }
  
  public File getCslJsonFile() {
    return cslJson;
  }
}
