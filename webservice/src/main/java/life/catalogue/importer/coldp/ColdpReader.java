package life.catalogue.importer.coldp;

import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.io.PathUtils;
import life.catalogue.common.tax.RankUtils;
import life.catalogue.csv.CsvReader;
import life.catalogue.csv.Schema;
import life.catalogue.importer.NormalizationFailedException;

import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

/**
 *
 */
public class ColdpReader extends CsvReader {
  private static final Logger LOG = LoggerFactory.getLogger(ColdpReader.class);
  // Synonym and TypeMaterial also have IDs but do not require them as there is no foreign key to them
  private static final Set<ColdpTerm> ID_SCHEMAS = ImmutableSet.of(ColdpTerm.NameUsage, ColdpTerm.Reference, ColdpTerm.Name, ColdpTerm.Taxon);
  private static final Set<ColdpTerm> NAMEID_SCHEMAS;
  private static final Set<ColdpTerm> TAXID_SCHEMAS;
  private static final Set<ColdpTerm> REFID_SCHEMAS;
  static {
    // make sure we are aware of ColTerms
    TermFactory.instance().registerTermEnum(ColdpTerm.class);

    // discover schemas with foreign key
    Set<ColdpTerm> refID = new HashSet<>();
    Set<ColdpTerm> taxID = new HashSet<>();
    Set<ColdpTerm> nameID = new HashSet<>();
    ColdpTerm.RESOURCES.forEach((s, terms) -> {
      if (terms.contains(ColdpTerm.referenceID)) {
        refID.add(s);
      }
      if (terms.contains(ColdpTerm.taxonID)) {
        taxID.add(s);
      }
      if (terms.contains(ColdpTerm.nameID)) {
        nameID.add(s);
      }
    });
    REFID_SCHEMAS = ImmutableSet.copyOf(refID);
    TAXID_SCHEMAS = ImmutableSet.copyOf(taxID);
    NAMEID_SCHEMAS = ImmutableSet.copyOf(nameID);
  }
  
  private File bibtex;
  private File cslJson;
  private Path treatments;

  private ColdpReader(Path folder) throws IOException {
    super(folder, "col", "coldp");
    detectMappedClassification(ColdpTerm.Taxon, RankUtils.RANK2COLDP.entrySet().stream()
         .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey))
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

    require(ColdpTerm.TaxonConceptRelation, ColdpTerm.relatedTaxonID);
    require(ColdpTerm.TaxonConceptRelation, ColdpTerm.type);

    require(ColdpTerm.SpeciesInteraction, ColdpTerm.type);

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
  
      requireOne(ColdpTerm.Distribution, ColdpTerm.area, ColdpTerm.areaID);
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
