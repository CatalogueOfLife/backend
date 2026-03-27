package life.catalogue.common.datapackage;

import life.catalogue.api.datapackage.PackageDescriptor;
import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.api.vocab.*;
import life.catalogue.api.vocab.area.Country;
import life.catalogue.api.vocab.area.Gazetteer;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.io.Resources;
import life.catalogue.common.text.StringUtils;

import org.apache.commons.lang3.Strings;
import org.gbif.nameparser.api.NamePart;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.util.UnicodeUtils;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import de.undercouch.citeproc.csl.CSLType;

import javax.annotation.Nullable;

public class DataPackageBuilder {
  private static final Logger LOG = LoggerFactory.getLogger(DataPackageBuilder.class);

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


  /**
   * Vocabulary enums by term, then rowType.
   * If ColdpTerm.ID is given for the only rowType then it is assumed it applies to any rowType and is always the same enum.
   */
  private static final Map<ColdpTerm, Map<ColdpTerm, Class<? extends Enum<?>>>> enums;
  private enum NoEnum{};
  static {
    Map<ColdpTerm, Map<ColdpTerm, Class<? extends Enum<?>>>> map = new HashMap<>(Map.of(
        ColdpTerm.code, Map.of(ColdpTerm.ID, NomCode.class),
        ColdpTerm.country, Map.of(ColdpTerm.ID, Country.class),
        ColdpTerm.environment, Map.of(ColdpTerm.ID, Environment.class),
        ColdpTerm.format, Map.of(ColdpTerm.ID, TreatmentFormat.class),
        ColdpTerm.gazetteer, Map.of(ColdpTerm.ID, Gazetteer.class),
        ColdpTerm.nameStatus, Map.of(ColdpTerm.ID, NomStatus.class),
        ColdpTerm.rank, Map.of(ColdpTerm.ID, Rank.class),
        ColdpTerm.notho, Map.of(ColdpTerm.ID, NamePart.class),
        ColdpTerm.sex, Map.of(ColdpTerm.ID, Sex.class)
    ));
    map.put(ColdpTerm.type, Map.of(
        ColdpTerm.Reference, CSLType.class,
        ColdpTerm.NameRelation, NomRelType.class,
        ColdpTerm.TaxonConceptRelation, TaxonConceptRelType.class,
        ColdpTerm.SpeciesInteraction, SpeciesInteractionType.class,
        ColdpTerm.Media, NoEnum.class,
        ColdpTerm.SpeciesEstimate, EstimateType.class
    ));
    map.put(ColdpTerm.status, Map.of(
        ColdpTerm.Name, NomStatus.class,
        ColdpTerm.TypeMaterial, TypeStatus.class,
        ColdpTerm.Synonym, TaxonomicStatus.class,
        ColdpTerm.Taxon, TaxonomicStatus.class,
        ColdpTerm.NameUsage, TaxonomicStatus.class
    ));
    enums = Map.copyOf(map);
  }

  private static final Map<ColdpTerm, ForeignKey> foreignKeys = ImmutableMap.<ColdpTerm, ForeignKey>builder()
      .put(ColdpTerm.referenceID, new ForeignKey(ColdpTerm.referenceID, ColdpTerm.Reference, ColdpTerm.ID))
      .put(ColdpTerm.accordingToID, new ForeignKey(ColdpTerm.accordingToID, ColdpTerm.Reference, ColdpTerm.ID))
      .put(ColdpTerm.nameID, new ForeignKey(ColdpTerm.nameID, ColdpTerm.Name, ColdpTerm.ID))
      .put(ColdpTerm.relatedNameID, new ForeignKey(ColdpTerm.relatedNameID, ColdpTerm.Name, ColdpTerm.ID))
      .put(ColdpTerm.basionymID, new ForeignKey(ColdpTerm.basionymID, ColdpTerm.Name, ColdpTerm.ID))
      .put(ColdpTerm.taxonID, new ForeignKey(ColdpTerm.taxonID, ColdpTerm.Taxon, ColdpTerm.ID))
      .put(ColdpTerm.parentID, new ForeignKey(ColdpTerm.parentID, ColdpTerm.Taxon, ColdpTerm.ID))
      .build();

  private static final Set<ColdpTerm> required = ImmutableSet.of(ColdpTerm.ID, ColdpTerm.scientificName);

  private final Map<ColdpTerm, String> resourceDescriptions = new HashMap<>();
  private final Map<ColdpTerm, Map<ColdpTerm, String>> resourceFieldDescriptions = new HashMap<>();

  private String titleToName(String t) {
    if (StringUtils.hasContent(t)) {
      return UnicodeUtils.foldToAscii(t).replaceAll("\\s+", "-");
    }
    return null;
  }

  public static String bundledColdpSpecs() {
    try {
      return Resources.toString("coldp/readme.html");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public DataPackageBuilder docs() {
    return docs(bundledColdpSpecs());
  }

  public DataPackageBuilder docs(String html) {
    var doc = Jsoup.parse(html);
    for (var rt : ColdpTerm.RESOURCES.keySet()) {
      readEntity(doc, rt);
    }
    // updates for NameUsage which shares docs with Taxon/Synonym/Name
    // and has special name clash variations
    var fields = resourceFieldDescriptions.get(ColdpTerm.NameUsage);
    var nFields = resourceFieldDescriptions.get(ColdpTerm.Name);
    var tFields = resourceFieldDescriptions.get(ColdpTerm.Taxon);
    var sFields = resourceFieldDescriptions.get(ColdpTerm.Synonym);
    for (var ft : ColdpTerm.RESOURCES.get(ColdpTerm.NameUsage)) {
      String docs;
      switch (ft) {
        case genericName:
          docs = nFields.get(ColdpTerm.genus);
          break;
        default:
          docs = getFirstDescription(ft, nFields, tFields, sFields);
          if (docs == null && ft.name().startsWith("name")) {
            var nt = ColdpTerm.find(Strings.CS.removeStart(ft.name(), "name"), false);
            if (nt != null) {
              docs = getFirstDescription(nt, nFields);
            }
          }
      }
      fields.put(ft, docs);
    }
    return this;
  }

  private String getFirstDescription(ColdpTerm term, Map<ColdpTerm, String>... descriptions) {
    for (Map<ColdpTerm, String> d : descriptions) {
      if (d.containsKey(term)) {
        return d.get(term);
      }
    }
    return null;
  }

  private void readEntity(Document doc, ColdpTerm rt) {
    var anch = "#" + rt.simpleName().toLowerCase();
    var anchs = doc.select(anch);
    if (anchs.isEmpty()) {
      LOG.warn("No entity anchor found for {}", rt);
      return;
    }
    var entityH2 = anchs.getFirst();
    resourceDescriptions.put(rt, getDescription(entityH2));
    var fields = new HashMap<ColdpTerm, String>();
    resourceFieldDescriptions.put(rt, fields);

    var sib = entityH2.nextElementSibling();
    while (sib != null) {
      if (sib.nodeName().equals("h2")) {
        // next class, get out
        return;
      }
      // field
      if (sib.nodeName().equals("h4")) {
        String fieldName = sib.text();
        ColdpTerm ft = ColdpTerm.find(fieldName, false);
        fields.put(ft, getDescription(sib));
      }
      sib = sib.nextElementSibling();
    }
  }

  private String getDescription(Element heading) {
    StringBuilder sb = new StringBuilder();
    var sib = heading.nextElementSibling();
    while (sib != null && (sib.nodeName().equals("p") || sib.nodeName().equals("ul"))) {
      var text = sib.text();
      if (!text.startsWith("type:") && !text.startsWith("added in")) {
        if (sb.length()>1) {
          sb.append(" ");
        }
        sb.append(text.trim());
      }
      sib = sib.nextElementSibling();
    }
    return sb.toString();
  }
  
  public DataPackage build(PackageDescriptor pd) {
    DataPackage p = new DataPackage();
    p.setTitle(pd.getTitle());
    p.setName(titleToName(pd.getTitle()));

    var tr = new TreatmentResource();
    if (pd.getResources() == null || pd.getResources().isEmpty()) {
      // use all as default!
      pd.setResources(ColdpTerm.RESOURCES.keySet().stream()
          .map(t -> {
            if (t == ColdpTerm.Treatment) {
              return tr.getName();
            } else {
              return t.name().toLowerCase() + ".tsv";
            }
          })
          .collect(Collectors.toList())
      );
    }
    
    for (String res : pd.getResources()) {
      Resource r;
      if (res.equalsIgnoreCase(tr.getName())) {
        r = tr;
      } else {
        r = new Resource();
        r.setPath(resourceUrl(pd.getBase(), res));
        if (res.toLowerCase().endsWith("csv")) {
          r.setDialect(Dialect.CSV);
        } else {
          r.setDialect(Dialect.TSV);
        }
        var s = buildSchema(res);
        r.setSchema(s);
        r.setDescription(resourceDescriptions.get(s.getRowType()));
      }
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

    var fieldDescriptions = resourceFieldDescriptions.get(rowType);

    for (ColdpTerm ft : ColdpTerm.RESOURCES.get(rowType)) {
      String type = dataTypes.get(ft.getType());
      String format = dataFormats.getOrDefault(ft.getType(), Field.FORMAT_DEFAULT);
      Map<String, Object> constraints = new HashMap<>();
      if (enums.containsKey(ft)) {
        List<String> values;
        if (ft == ColdpTerm.status && rowType == ColdpTerm.Synonym) {
          // we dont want all values for synonyms
          values = enumValues(TaxonomicStatus.class, PermissiveEnumSerde::enumValueName, TaxonomicStatus::isSynonym);
        } else {
          values = enumValues(rowType, ft);
        }
        if (!values.isEmpty()) {
          constraints.put(Field.CONSTRAINT_KEY_ENUM, values);
        }
      }
      if (required.contains(ft)) {
        constraints.put(Field.CONSTRAINT_KEY_REQUIRED, true);
      }
      if (ColdpTerm.ID == ft) {
        s.setPrimaryKey(ft.simpleName());
        constraints.put(Field.CONSTRAINT_KEY_UNIQUE, true);
      }
      if (foreignKeys.containsKey(ft)) {
        s.getForeignKeys().add(foreignKeys.get(ft));
      }
      s.getFields().add(new Field(ft.simpleName(), type, format, ft.simpleName(), fieldDescriptions == null ? null : fieldDescriptions.get(ft), constraints));
    }
    return s;
  }
  
  private List<String> enumValues(ColdpTerm rowType, ColdpTerm t) {
    // special cases
    switch (t) {
      case country:
        return enumValues(Country.class, Country::getIso2LetterCode, null);
    }
    
    Class<? extends Enum> enumClass;
    var resources = enums.get(t);
    if (resources.size() == 1 && resources.containsKey(ColdpTerm.ID)) {
      enumClass = resources.get(ColdpTerm.ID);
    } else {
      enumClass = resources.get(rowType);
    }

    return enumValues(enumClass, PermissiveEnumSerde::enumValueName, null);
  }
  
  private static <T extends Enum> List<String> enumValues(Class<T> eClass, Function<T, String> mapper, @Nullable Predicate<T> filter) {
    return Arrays.stream(eClass.getEnumConstants())
        .filter(e -> filter == null || filter.test(e))
        .map(mapper)
        .collect(Collectors.toList());
  }
}
