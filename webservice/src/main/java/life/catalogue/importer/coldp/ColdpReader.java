package life.catalogue.importer.coldp;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import life.catalogue.api.datapackage.ColdpTerm;
import life.catalogue.common.io.PathUtils;
import life.catalogue.csv.CsvReader;
import life.catalogue.csv.Schema;
import life.catalogue.importer.NormalizationFailedException;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 *
 */
public class ColdpReader extends CsvReader {
  private static final Logger LOG = LoggerFactory.getLogger(ColdpReader.class);
  // Synonym and TypeMaterial also have IDs but do not require them as there is no foreign key to them
  private static Set<ColdpTerm> ID_SCHEMAS = ImmutableSet.of(ColdpTerm.NameUsage, ColdpTerm.Reference, ColdpTerm.Name, ColdpTerm.Taxon);
  private static Set<ColdpTerm> NAMEID_SCHEMAS = ImmutableSet.of(ColdpTerm.Synonym, ColdpTerm.Taxon, ColdpTerm.NameRelation, ColdpTerm.TypeMaterial);
  private static Set<ColdpTerm> TAXID_SCHEMAS = ImmutableSet.of(
    ColdpTerm.Treatment, ColdpTerm.Synonym, ColdpTerm.Distribution, ColdpTerm.Media, ColdpTerm.VernacularName, ColdpTerm.TaxonRelation
  );
  private static Set<ColdpTerm> REFID_SCHEMAS = ImmutableSet.of(
    ColdpTerm.Name,
    ColdpTerm.NameRelation,
    ColdpTerm.NameUsage,
    ColdpTerm.Distribution,
    ColdpTerm.Synonym,
    ColdpTerm.Taxon,
    ColdpTerm.TaxonRelation,
    ColdpTerm.Treatment,
    ColdpTerm.TypeMaterial,
    ColdpTerm.VernacularName
  );
  static {
    // make sure we are aware of ColTerms
    TermFactory.instance().registerTermEnum(ColdpTerm.class);
  }
  
  private File bibtex;
  private File cslJson;
  private Path treatments;

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
    Path treat = dir.resolve("treatments");
    if (Files.isDirectory(treat)) {
      treatments = treat;
      LOG.info("Treatments folder found: {}", treatments);
    }
  }
  
  public boolean hasExtendedReferences() {
    return bibtex != null || cslJson != null;
  }

  public boolean hasTreatments() {
    return treatments != null;
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
    Term nameRowType = requireOneSchema(ColdpTerm.Name, ColdpTerm.NameUsage);

    require(ColdpTerm.NameRelation, ColdpTerm.relatedNameID);
    require(ColdpTerm.NameRelation, ColdpTerm.type);

    require(ColdpTerm.TaxonRelation, ColdpTerm.relatedTaxonID);
    require(ColdpTerm.TaxonRelation, ColdpTerm.type);

    // either require the scientificName or at least some parsed field
    if (!hasData(nameRowType, ColdpTerm.scientificName)) {
      LOG.warn("No scientificName mapped! Require parsed name fields");
      // genus & specificEpithet must exist otherwise!
      if (nameRowType.equals(ColdpTerm.NameUsage)) {
        requireOne(nameRowType, ColdpTerm.uninomial, ColdpTerm.genericName);
      } else {
        requireOne(nameRowType, ColdpTerm.uninomial, ColdpTerm.genus);
      }
    }

    // reference dependencies
    if (!hasReferences()) {
      LOG.warn("No Reference mapped! Disallow all referenceIDs");
      disallow(ColdpTerm.NameUsage, ColdpTerm.nameReferenceID);
      for (ColdpTerm rt : REFID_SCHEMAS) {
        disallow(rt, ColdpTerm.referenceID);
      }
    }

    Optional<Schema> taxonOpt = schema(ColdpTerm.Taxon)
      .or(() -> schema(ColdpTerm.NameUsage));

    if (taxonOpt.isPresent()) {
      Schema taxon = taxonOpt.get();
      if (taxon.hasTerm(ColdpTerm.parentID)) {
        mappingFlags.setParentNameMapped(true);
      } else {
        mappingFlags.setParentNameMapped(false);
        LOG.warn("No taxon parentID mapped");
      }
  
      if (taxon.hasAnyTerm(ColdpTerm.DENORMALIZED_RANKS)) {
        mappingFlags.setDenormedClassificationMapped(true);
        LOG.info("Use denormalized taxon classification");
      } else {
        mappingFlags.setDenormedClassificationMapped(false);
      }
  
      for (ColdpTerm t : TAXID_SCHEMAS) {
        require(t, ColdpTerm.taxonID);
      }
  
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

  public Iterable<Path> getTreatments() throws IOException {
    return PathUtils.listFiles(treatments, Set.of("txt", "html", "xml"));
  }
}
