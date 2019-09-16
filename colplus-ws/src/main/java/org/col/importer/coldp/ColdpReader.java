package org.col.importer.coldp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import jersey.repackaged.com.google.common.collect.ImmutableMap;
import org.col.api.datapackage.ColdpTerm;
import org.col.common.io.PathUtils;
import org.col.csv.CsvReader;
import org.col.csv.Schema;
import org.col.importer.NormalizationFailedException;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ColdpReader extends CsvReader {
  private static final Logger LOG = LoggerFactory.getLogger(ColdpReader.class);
  private static Set<ColdpTerm> ID_SCHEMAS = ImmutableSet.of(ColdpTerm.Reference, ColdpTerm.Name, ColdpTerm.Taxon);
  private static Set<ColdpTerm> NAMEID_SCHEMAS = ImmutableSet.of(ColdpTerm.Synonym, ColdpTerm.Taxon, ColdpTerm.NameRel);
  private static Set<ColdpTerm> TAXID_SCHEMAS = ImmutableSet.of(
      ColdpTerm.Synonym, ColdpTerm.Description, ColdpTerm.Distribution, ColdpTerm.Media, ColdpTerm.VernacularName
  );
  private static Set<ColdpTerm> REFID_SCHEMAS = ImmutableSet.of(
      ColdpTerm.Description, ColdpTerm.Distribution, ColdpTerm.VernacularName
  );
  static {
    // make sure we are aware of ColTerms
    TermFactory.instance().registerTermEnum(ColdpTerm.class);
  }
  
  private File bibtex;
  private File cslJson;

  private ColdpReader(Path folder) throws IOException {
    super(folder, "col", "coldp");
    detectMappedClassification(ColdpTerm.Taxon, ImmutableMap.<Term, Rank>builder()
        .put(ColdpTerm.kingdom, Rank.KINGDOM)
        .put(ColdpTerm.phylum, Rank.PHYLUM)
        .put(ColdpTerm.subphylum, Rank.SUBPHYLUM)
        .put(ColdpTerm.class_, Rank.CLASS)
        .put(ColdpTerm.subclass, Rank.SUBCLASS)
        .put(ColdpTerm.order, Rank.ORDER)
        .put(ColdpTerm.suborder, Rank.SUBORDER)
        .put(ColdpTerm.superfamily, Rank.SUPERFAMILY)
        .put(ColdpTerm.family, Rank.FAMILY)
        .put(ColdpTerm.subfamily, Rank.SUBFAMILY)
        .put(ColdpTerm.tribe, Rank.TRIBE)
        .put(ColdpTerm.subtribe, Rank.SUBTRIBE)
        .put(ColdpTerm.genus, Rank.GENUS)
        .put(ColdpTerm.subgenus, Rank.SUBGENUS)
        .build()
    );
  
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
    return hasExtendedReferences() || hasSchema(ColdpTerm.Reference);
  }
  
  protected void validate() throws NormalizationFailedException.SourceInvalidException {
    super.validate();
    
    // allow only COL row types
    filterSchemas(rowType -> rowType instanceof ColdpTerm);
    
    // mandatory terms.
    // Fail early, if missing ignore file alltogether!!!
    for (ColdpTerm t : ID_SCHEMAS) {
      require(t, ColdpTerm.ID);
    }
    for (ColdpTerm t : NAMEID_SCHEMAS) {
      require(t, ColdpTerm.nameID);
    }
    // either require the scientificName or at least some parsed field
    if (!hasData(ColdpTerm.Name, ColdpTerm.scientificName)) {
      LOG.warn("No scientificName mapped! Require parsed name fields");
      // genus & specificEpithet must exist otherwise!
      require(ColdpTerm.Name, ColdpTerm.genus, ColdpTerm.specificEpithet);
    }
    require(ColdpTerm.NameRel, ColdpTerm.relatedNameID);
    require(ColdpTerm.NameRel, ColdpTerm.type);
  
    requireSchema(ColdpTerm.Name);
  
    // reference dependencies
    if (!hasReferences()) {
      LOG.warn("No Reference mapped! Disallow referenceIDs");
      disallow(ColdpTerm.Name, ColdpTerm.publishedInID);
      disallow(ColdpTerm.NameRel, ColdpTerm.publishedInID);
      for (ColdpTerm rt : REFID_SCHEMAS) {
        disallow(rt, ColdpTerm.referenceID);
      }
    }

    Optional<Schema> taxonOpt = schema(ColdpTerm.Taxon);
    if (taxonOpt.isPresent()) {
      Schema taxon = taxonOpt.get();
      if (taxon.hasTerm(ColdpTerm.parentID)) {
        mappingFlags.setParentNameMapped(true);
      } else {
        mappingFlags.setParentNameMapped(false);
        LOG.warn("No taxon parentID mapped");
      }
  
      if (taxon.hasAnyTerm(ColdpTerm.HIGHER_RANKS)) {
        mappingFlags.setDenormedClassificationMapped(true);
        LOG.info("Use denormalized taxon classification");
      } else {
        mappingFlags.setDenormedClassificationMapped(false);
      }
  
      for (ColdpTerm t : TAXID_SCHEMAS) {
        require(t, ColdpTerm.taxonID);
      }
  
      require(ColdpTerm.Description, ColdpTerm.description);
      require(ColdpTerm.Distribution, ColdpTerm.area);
      require(ColdpTerm.VernacularName, ColdpTerm.name);
      require(ColdpTerm.Media, ColdpTerm.url);
  
      if (hasSchema(ColdpTerm.Synonym)) {
        mappingFlags.setAcceptedNameMapped(true);
      } else {
        LOG.warn("No Synonyms mapped!");
      }
      
    } else {
      LOG.warn("No Taxa mapped, only inserting names!");
      for (ColdpTerm t : TAXID_SCHEMAS) {
        schemas.remove(t);
      }
    }
    
    reportMissingSchemas(ColdpTerm.class);
  }
  
  public File getBibtexFile() {
    return bibtex;
  }
  
  public File getCslJsonFile() {
    return cslJson;
  }
}
