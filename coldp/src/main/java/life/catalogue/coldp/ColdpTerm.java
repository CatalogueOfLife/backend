package life.catalogue.coldp;

import org.gbif.dwc.terms.AlternativeNames;
import org.gbif.dwc.terms.Term;

import java.net.URI;
import java.time.Year;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CoL terms covering all columns needed for the new CoL Data Package submission format:
 * https://github.com/CatalogueOfLife/datapackage-specs
 * <p>
 * To avoid dependency and clashes with DwC no terms are reused.
 */
public enum ColdpTerm implements Term, AlternativeNames {
  Reference((Class)null),
  ID,
  sourceID,
  citation,
  type(Enum.class),
  author,
  editor,
  title,
  containerAuthor,
  containerTitle("source"),
  issued(Date.class, "year"),
  accessed(Date.class),
  collectionTitle,
  collectionEditor,
  volume,
  issue,
  edition,
  page("details"),
  publisher,
  publisherPlace,
  version,
  isbn,
  issn,
  doi,
  link(URI.class),
  remarks,

  Name((Class) null),
  Taxon((Class) null),
  Synonym((Class) null),
  taxonID,

  NameUsage((Class) null),
  // ID,
  //sourceID,
  parentID,
  sequenceIndex(Integer.class),
  branchLength(Double.class),
  basionymID("originalNameID"),
  status(Enum.class),
  provisional(Boolean.class),
  scientificName,
  authorship,
  rank(Enum.class),
  notho(Enum.class),
  uninomial,
  genus,
  genericName, // alternative term to Name.genus
  infragenericEpithet,
  specificEpithet,
  infraspecificEpithet,
  cultivarEpithet,
  namePhrase,
  accordingToID,
  accordingToPage,
  accordingToPageLink,
  nameReferenceID("namePublishedInID"), // alternative term to Name.referenceID
  publishedInYear(Year.class, "namePublishedInYear"),
  publishedInPage("namePublishedInPage"),
  publishedInPageLink("namePublishedInPageLink"),
  code(Enum.class),
  nameStatus(Enum.class), // alternative term to Name.status
  scrutinizer,
  scrutinizerID,
  scrutinizerDate(Date.class),
  referenceID("publishedInID"),
  extinct(Boolean.class),
  temporalRangeStart,
  temporalRangeEnd,
  environment(Enum.class, "lifezone"),
  species,
  section,
  subgenus,
  //genus,
  subtribe,
  tribe,
  subfamily,
  family,
  superfamily,
  suborder,
  order,
  subclass,
  class_,
  subphylum,
  phylum,
  kingdom,
  nameRemarks, // alternative term to Name.remarks
  //link
  //remarks

  NameRelation((Class) null, "NameRel"),
  nameID,
  relatedNameID,
  //sourceID,
  //type,
  //referenceID,
  //remarks,

  TypeMaterial((Class) null),
  //ID,
  //nameID,
  //sourceID,
  //citation,
  //status,
  country(Enum.class),
  locality,
  latitude,
  longitude,
  altitude,
  sex(Enum.class),
  institutionCode,
  catalogNumber,
  associatedSequences,
  host,
  date,
  collector,
  //referenceID,
  //link,
  //remarks,

  TaxonConceptRelation((Class) null, "TaxonRelation"),
  //taxonID,
  relatedTaxonID,
  //sourceID,
  //type,
  //referenceID,
  //remarks,

  SpeciesInteraction((Class) null),
  //taxonID,
  //relatedTaxonID,
  //sourceID,
  relatedTaxonScientificName,
  //type,
  //referenceID,
  //remarks,

  Treatment((Class) null),
  //taxonID,
  //sourceID,
  document,
  format,

  Distribution((Class) null),
  //taxonID,
  //sourceID,
  areaID,
  area,
  gazetteer(Enum.class),
  //status,
  //referenceID,
  
  Media((Class) null),
  //taxonID,
  //sourceID,
  url(URI.class),
  //type,
  //format,
  //title,
  created(Date.class),
  creator,
  license(Enum.class),
  //link,
  
  VernacularName((Class) null),
  //taxonID,
  //sourceID,
  name,
  transliteration,
  language(Enum.class),
  //country,
  //sex(Enum.class),
  //referenceID

  SpeciesEstimate((Class) null),
  //taxonID,
  //sourceID,
  estimate(Integer.class),
  //type,
  //referenceID
  //remarks
  ;
  
  private final static Map<String, ColdpTerm> LOOKUP = Arrays.stream(values()).collect(Collectors.toMap(ColdpTerm::normalize, Function.identity()));
  
  /**
   * List of all higher rank terms, ordered by rank and starting with kingdom.
   */
  public static final ColdpTerm[] DENORMALIZED_RANKS = {ColdpTerm.kingdom,
      phylum, subphylum,
      class_, subclass,
      order, suborder,
      superfamily, family, subfamily,
      tribe,subtribe,
      genus, subgenus,
      section,
      species
  };

  public static Map<ColdpTerm, List<ColdpTerm>> RESOURCES = Map.ofEntries(
    Map.entry(Reference, List.of(
      ID,
      sourceID,
      citation,
      type,
      author,
      editor,
      title,
      containerAuthor,
      containerTitle,
      issued,
      accessed,
      collectionTitle,
      collectionEditor,
      volume,
      issue,
      edition,
      page,
      publisher,
      publisherPlace,
      version,
      isbn,
      issn,
      doi,
      link,
      remarks
    )), Map.entry(Name, List.of(
      ID,
      sourceID,
      basionymID,
      scientificName,
      authorship,
      rank,
      notho,
      uninomial,
      genus,
      infragenericEpithet,
      specificEpithet,
      infraspecificEpithet,
      cultivarEpithet,
      code,
      status,
      referenceID,
      publishedInYear,
      publishedInPage,
      publishedInPageLink,
      link,
      remarks
    )), Map.entry(NameRelation, List.of(
      nameID,
      relatedNameID,
      sourceID,
      type,
      referenceID,
      remarks
    )), Map.entry(TypeMaterial, List.of(
      ID,
      nameID,
      sourceID,
      citation,
      status,
      referenceID,
      country,
      locality,
      latitude,
      longitude,
      altitude,
      sex,
      host,
      associatedSequences,
      date,
      collector,
      institutionCode,
      catalogNumber,
      link,
      remarks
    )), Map.entry(Taxon, List.of(
      ID,
      sourceID,
      parentID,
      nameID,
      namePhrase,
      accordingToID,
      accordingToPage,
      accordingToPageLink,
      provisional,
      scrutinizer,
      scrutinizerID,
      scrutinizerDate,
      extinct,
      temporalRangeStart,
      temporalRangeEnd,
      environment,
      referenceID,
      species,
      section,
      subgenus,
      genus,
      subtribe,
      tribe,
      subfamily,
      family,
      superfamily,
      suborder,
      order,
      subclass,
      class_,
      subphylum,
      phylum,
      kingdom,
      sequenceIndex,
      branchLength,
      link,
      remarks
    )), Map.entry(Synonym, List.of(
      ID,
      sourceID,
      taxonID,
      nameID,
      namePhrase,
      accordingToID,
      accordingToPage,
      accordingToPageLink,
      status,
      referenceID,
      link,
      remarks
    )), Map.entry(NameUsage, List.of(
      ID,
      sourceID,
      parentID,
      basionymID,
      status,
      scientificName,
      authorship,
      rank,
      notho,
      uninomial,
      genericName,
      infragenericEpithet,
      specificEpithet,
      infraspecificEpithet,
      cultivarEpithet,
      namePhrase,
      nameReferenceID,
      publishedInYear,
      publishedInPage,
      publishedInPageLink,
      code,
      nameStatus,
      accordingToID,
      accordingToPage,
      accordingToPageLink,
      referenceID,
      scrutinizer,
      scrutinizerID,
      scrutinizerDate,
      extinct,
      temporalRangeStart,
      temporalRangeEnd,
      environment,
      species,
      section,
      subgenus,
      genus,
      subtribe,
      tribe,
      subfamily,
      family,
      superfamily,
      suborder,
      order,
      subclass,
      class_,
      subphylum,
      phylum,
      kingdom,
      sequenceIndex,
      branchLength,
      link,
      nameRemarks,
      remarks
    )), Map.entry(SpeciesInteraction, List.of(
      taxonID,
      relatedTaxonID,
      sourceID,
      relatedTaxonScientificName,
      type,
      referenceID,
      remarks
    )), Map.entry(TaxonConceptRelation, List.of(
      taxonID,
      relatedTaxonID,
      sourceID,
      type,
      referenceID,
      remarks
    )), Map.entry(Treatment, List.of(
      taxonID,
      sourceID,
      document,
      format
    )), Map.entry(Distribution, List.of(
      taxonID,
      sourceID,
      areaID,
      area,
      gazetteer,
      status,
      referenceID,
      remarks
    )), Map.entry(Media, List.of(
      taxonID,
      sourceID,
      url,
      type,
      format,
      title,
      created,
      creator,
      license,
      link
    )), Map.entry(VernacularName, List.of(
      taxonID,
      sourceID,
      name,
      transliteration,
      language,
      country,
      area,
      sex,
      referenceID
    )), Map.entry(SpeciesEstimate, List.of(
      taxonID,
      sourceID,
      estimate,
      type,
      referenceID,
      remarks)
    ));

  private static final String PREFIX = "col";
  private static final String NS = "http://catalogueoflife.org/terms/";
  private static final URI NS_URI = URI.create(NS);

  private final Class<?> dataType;
  private final String[] alternatives;
  
  ColdpTerm() {
    this(String.class);
  }

  ColdpTerm(String... alternatives) {
    this.dataType = String.class;
    this.alternatives = alternatives;
  }

  ColdpTerm(Class<?> type) {
    this.dataType = type;
    this.alternatives = new String[0];
  }

  ColdpTerm(Class<?> type, String... alternatives) {
    this.dataType = type;
    this.alternatives = alternatives;
  }
  
  
  @Override
  public String prefix() {
    return PREFIX;
  }
  
  @Override
  public URI namespace() {
    return NS_URI;
  }
  
  @Override
  public String simpleName() {
    if (this == class_) {
      return "class";
    }
    return name();
  }
  
  @Override
  public String toString() {
    return prefixedName();
  }
  
  @Override
  public String[] alternativeNames() {
    return this.alternatives;
  }
  
  @Override
  public boolean isClass() {
    return dataType == null;
  }

  public Class<?> getType() {
    return dataType;
  }

  private static String normalize(String x, boolean isClass) {
    x = x.replaceAll("[-_ ]+", "").toLowerCase();
    return isClass ? Character.toUpperCase(x.charAt(0)) + x.substring(1) : x;
  }
  
  private static String normalize(ColdpTerm t) {
    return normalize(t.name(), t.isClass());
  }
  
  public static ColdpTerm find(String name, boolean isClass) {
    return LOOKUP.getOrDefault(normalize(name, isClass), null);
  }
}
