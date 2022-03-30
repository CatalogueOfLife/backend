package life.catalogue.common.datapackage;

import life.catalogue.api.datapackage.PackageDescriptor;
import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.api.vocab.*;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.text.StringUtils;
import life.catalogue.common.text.UnicodeUtils;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import de.undercouch.citeproc.csl.CSLType;

public class DataPackageBuilder {
  private static final String MONOMIAL_PATTERN = "^[A-Z\\p{Lu}]\\p{L}+$";
  
  // only non string data types here
  private static final Map<Class, String> dataTypes = Map.of(
    String.class, Field.TYPE_STRING,
    Integer.class, Field.TYPE_INTEGER,
    Double.class, Field.TYPE_NUMBER,
    Number.class, Field.TYPE_NUMBER,
    Year.class, Field.TYPE_YEAR,
    Date.class, Field.TYPE_DATE,
    LocalDateTime.class, Field.TYPE_DATETIME,
    Boolean.class, Field.TYPE_BOOLEAN,
    URI.class, Field.TYPE_STRING,
    Enum.class, Field.TYPE_STRING
  );
  
  private static final Map<Class, String> dataFormats = Map.of(
      URI.class, Field.FORMAT_URI
  );
  
  private static final Set<ColdpTerm> monomials = ImmutableSet.copyOf(
    Arrays.stream(ColdpTerm.DENORMALIZED_RANKS)
      .filter(t -> t != ColdpTerm.species)
      .collect(Collectors.toSet())
  );

  /**
   * Vocabulary enums by term, then rowType.
   * If ColdpTerm.ID is given for the only rowType then it is assumed it applies to any rowType and is always the same enum.
   */
  private static final Map<ColdpTerm, Map<ColdpTerm, Class<? extends Enum>>> enums = Map.of(
    ColdpTerm.code, Map.of(ColdpTerm.ID, NomCode.class),
    ColdpTerm.country, Map.of(ColdpTerm.ID, Country.class),
    ColdpTerm.environment, Map.of(ColdpTerm.ID, Environment.class),
    ColdpTerm.format, Map.of(ColdpTerm.ID, TreatmentFormat.class),
    ColdpTerm.gazetteer, Map.of(ColdpTerm.ID, Gazetteer.class),
    ColdpTerm.nameStatus, Map.of(ColdpTerm.ID, NomStatus.class),
    ColdpTerm.rank, Map.of(ColdpTerm.ID, Rank.class),
    ColdpTerm.sex, Map.of(ColdpTerm.ID, Sex.class),

    ColdpTerm.type, Map.of(
      ColdpTerm.Reference, CSLType.class,
      ColdpTerm.NameRelation, NomRelType.class,
      ColdpTerm.TaxonConceptRelation, TaxonConceptRelType.class,
      ColdpTerm.SpeciesInteraction, SpeciesInteractionType.class,
      ColdpTerm.Media, MediaType.class,
      ColdpTerm.SpeciesEstimate, EstimateType.class
    ),
    ColdpTerm.status, Map.of(
      ColdpTerm.Name, NomStatus.class,
      ColdpTerm.TypeMaterial, TypeStatus.class,
      ColdpTerm.Synonym, TaxonomicStatus.class,
      ColdpTerm.Taxon, TaxonomicStatus.class,
      ColdpTerm.NameUsage, TaxonomicStatus.class,
      ColdpTerm.Distribution, DistributionStatus.class
    )
  );
  
  private static final Map<ColdpTerm, ForeignKey> foreignKeys = ImmutableMap.<ColdpTerm, ForeignKey>builder()
      .put(ColdpTerm.referenceID, new ForeignKey(ColdpTerm.referenceID, ColdpTerm.Reference, ColdpTerm.ID))
      .put(ColdpTerm.nameID, new ForeignKey(ColdpTerm.nameID, ColdpTerm.Name, ColdpTerm.ID))
      .put(ColdpTerm.relatedNameID, new ForeignKey(ColdpTerm.relatedNameID, ColdpTerm.Name, ColdpTerm.ID))
      .put(ColdpTerm.taxonID, new ForeignKey(ColdpTerm.taxonID, ColdpTerm.Taxon, ColdpTerm.ID))
      .put(ColdpTerm.parentID, new ForeignKey(ColdpTerm.parentID, ColdpTerm.Taxon, ColdpTerm.ID))
      .build();

  private static final Set<ColdpTerm> required = ImmutableSet.of(ColdpTerm.ID, ColdpTerm.scientificName);
  
  private String titleToName(String t) {
    if (StringUtils.hasContent(t)) {
      return UnicodeUtils.ascii(t).replaceAll("\\s+", "-");
    }
    return null;
  }
  
  public DataPackage build(PackageDescriptor pd) {
    DataPackage p = new DataPackage();
    p.setTitle(pd.getTitle());
    p.setName(titleToName(pd.getTitle()));
    
    if (pd.getResources() == null || pd.getResources().isEmpty()) {
      // use all as default!
      pd.setResources(ColdpTerm.RESOURCES.keySet().stream()
          .map(t -> t.name().toLowerCase() + ".tsv")
          .collect(Collectors.toList())
      );
    }
    
    for (String res : pd.getResources()) {
      Resource r = new Resource();
      r.setPath(resourceUrl(pd.getBase(), res));
      if (res.toLowerCase().endsWith("csv")) {
        r.setDialect(Dialect.CSV);
      } else {
        r.setDialect(Dialect.TSV);
      }
      r.setSchema(buildSchema(res));
      p.getResources().add(r);
    }
    return p;
  }
  
  private static String resourceUrl(String baseURL, String resource) {
    return resource;
  }
  
  private Schema buildSchema(String resource) {
    ColdpTerm rowType = ColdpTerm.find(FilenameUtils.getBaseName(resource), true);
    if (rowType == null) {
      throw new UnknownEntityException("Unknown entity " + resource);

    } else if (!rowType.isClass()) {
      throw new UnknownEntityException(resource + " is not a class term");
    }
    
    Schema s = new Schema();
    s.setRowType(rowType);
    
    for (ColdpTerm t : ColdpTerm.RESOURCES.get(rowType)) {
      String type = dataTypes.get(t.getType());
      String format = dataFormats.getOrDefault(t.getType(), Field.FORMAT_DEFAULT);
      Map<String, Object> constraints = new HashMap<>();
      if (enums.containsKey(t)) {
        constraints.put(Field.CONSTRAINT_KEY_ENUM, enumValues(rowType, t));
      }
      if (required.contains(t)) {
        constraints.put(Field.CONSTRAINT_KEY_REQUIRED, true);
      }
      if (ColdpTerm.ID == t) {
        s.setPrimaryKey(t.simpleName());
        //constraints.put(Field.CONSTRAINT_KEY_UNIQUE, true);
      }
      if (foreignKeys.containsKey(t)) {
        s.getForeignKeys().add(foreignKeys.get(t));
      }
      if (monomials.contains(t)) {
        constraints.put(Field.CONSTRAINT_KEY_PATTERN, MONOMIAL_PATTERN);
      }
      s.getFields().add(new Field(t.simpleName(), type, format, null, null, constraints));
    }
    return s;
  }
  
  private List<String> enumValues(ColdpTerm rowType, ColdpTerm t) {
    // special cases
    switch (t) {
      case country:
        return enumValues(Country.class, Country::getIso2LetterCode);
    }
    
    Class<? extends Enum> enumClass;
    var resources = enums.get(t);
    if (resources.size() == 1 && resources.containsKey(ColdpTerm.ID)) {
      enumClass = resources.get(ColdpTerm.ID);
    } else {
      enumClass = resources.get(rowType);
    }

    return enumValues(enumClass, PermissiveEnumSerde::enumValueName);
  }
  
  private static <T extends Enum> List<String> enumValues(Class<T> eClass, Function<T, String> mapper) {
    return Arrays.stream(eClass.getEnumConstants())
        .map(mapper)
        .collect(Collectors.toList());
  }
}
