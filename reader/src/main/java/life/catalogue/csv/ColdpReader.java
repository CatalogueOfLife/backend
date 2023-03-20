package life.catalogue.csv;


import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.io.PathUtils;

import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.nameparser.api.Rank;

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


/**
 *
 */
public class ColdpReader extends CsvReader {
  private static final Logger LOG = LoggerFactory.getLogger(ColdpReader.class);
  // Synonym and TypeMaterial also have IDs but do not require them as there is no foreign key to them
  private static final Set<ColdpTerm> ID_SCHEMAS = Set.of(ColdpTerm.NameUsage, ColdpTerm.Reference, ColdpTerm.Name, ColdpTerm.Taxon);
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
    REFID_SCHEMAS = Set.copyOf(refID);
    TAXID_SCHEMAS = Set.copyOf(taxID);
    NAMEID_SCHEMAS = Set.copyOf(nameID);
  }
  public static Map<Rank, ColdpTerm> RANK2COLDP = Map.ofEntries(
    Map.entry(Rank.KINGDOM, ColdpTerm.kingdom),
    Map.entry(Rank.PHYLUM, ColdpTerm.phylum),
    Map.entry(Rank.SUBPHYLUM, ColdpTerm.subphylum),
    Map.entry(Rank.CLASS, ColdpTerm.class_),
    Map.entry(Rank.SUBCLASS, ColdpTerm.subclass),
    Map.entry(Rank.ORDER, ColdpTerm.order),
    Map.entry(Rank.SUBORDER, ColdpTerm.suborder),
    Map.entry(Rank.SUPERFAMILY, ColdpTerm.superfamily),
    Map.entry(Rank.FAMILY, ColdpTerm.family),
    Map.entry(Rank.SUBFAMILY, ColdpTerm.subfamily),
    Map.entry(Rank.TRIBE, ColdpTerm.tribe),
    Map.entry(Rank.SUBTRIBE, ColdpTerm.subtribe),
    Map.entry(Rank.GENUS, ColdpTerm.genus),
    Map.entry(Rank.SUBGENUS, ColdpTerm.subgenus),
    Map.entry(Rank.SECTION, ColdpTerm.section)
  );

  private File bibtex;
  private File cslJson;
  private Path treatments;

  private ColdpReader(Path folder) throws IOException {
    super(folder, "col", "coldp");
    detectMappedClassification(ColdpTerm.Taxon, RANK2COLDP.entrySet().stream()
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
  
  protected void validate() throws SourceInvalidException {
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
    requireOneSchema(ColdpTerm.Name, ColdpTerm.NameUsage, ColdpTerm.Reference);

    require(ColdpTerm.NameRelation, ColdpTerm.relatedNameID);
    require(ColdpTerm.NameRelation, ColdpTerm.type);

    require(ColdpTerm.TaxonConceptRelation, ColdpTerm.relatedTaxonID);
    require(ColdpTerm.TaxonConceptRelation, ColdpTerm.type);

    require(ColdpTerm.SpeciesInteraction, ColdpTerm.type);

    // either require the scientificName or at least some parsed field
    Term nameRowType = findFirstSchema(ColdpTerm.Name, ColdpTerm.NameUsage);
    if (nameRowType != null && !hasData(nameRowType, ColdpTerm.scientificName)) {
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
  
      if (hasSchema(ColdpTerm.Synonym) || hasSchema(ColdpTerm.NameUsage)) {
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
