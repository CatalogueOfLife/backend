package life.catalogue.api.datapackage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.gbif.dwc.terms.AlternativeNames;
import org.gbif.dwc.terms.Term;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * CoL terms covering all columns needed for the new CoL Data Package submission format:
 * https://github.com/CatalogueOfLife/datapackage-specs
 * <p>
 * To avoid dependency and clashes with DwC no terms are reused.
 */
public enum ColdpTerm implements Term, AlternativeNames {
  Reference(true),
  ID,
  citation,
  author,
  title,
  year,
  source,
  details,
  doi,
  link,
  
  Name(true),
  //ID,
  originalNameID,
  scientificName,
  authorship,
  rank,
  uninomial,
  genus,
  specificEpithet,
  infragenericEpithet,
  infraspecificEpithet,
  cultivarEpithet,
  publishedInID,
  publishedInPage,
  publishedInYear,
  code,
  status,
  //link,
  remarks,

  NameRelation(true, "NameRel"),
  nameID,
  relatedNameID,
  type,
  //publishedInID,
  //remarks,

  TypeMaterial(true),
  //nameID,
  //citation,
  //status,
  locality,
  country,
  latitude,
  longitude,
  altitude,
  host,
  date,
  collector,
  referenceID,
  //link,
  //remarks,

  Taxon(true),
  // ID,
  parentID,
  //nameID,
  namePhrase,
  accordingToID,
  provisional,
  //referenceID,
  scrutinizer,
  scrutinizerDate,
  extinct,
  temporalRangeStart,
  temporalRangeEnd,
  lifezone,
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
  //link
  //remarks

  Synonym(true),
  //ID
  taxonID,
  //nameID
  //appendedPhrase,
  //accordingToID,
  //status
  //referenceID,
  //link
  //remarks

  NameUsage(true),
  nameStatus, // alternative term to Name.status
  genericName, // alternative term to Name.genus

  TaxonRelation(true, "TaxonRel"),
  //taxonID,
  relatedTaxonID,
  //type,
  //referenceID,
  //remarks,

  Treatment(true),
  //taxonID,
  document,
  format,

  Distribution(true),
  //taxonID,
  area,
  gazetteer,
  //status,
  //referenceID,
  
  Media(true),
  //taxonID,
  url,
  //type,
  //format,
  //title,
  created,
  creator,
  license,
  //link,
  
  VernacularName(true),
  //taxonID,
  name,
  transliteration,
  language,
  //country,
  sex
  //referenceID
  ;
  
  private static Map<String, ColdpTerm> LOOKUP = Maps.uniqueIndex(Arrays.asList(values()), ColdpTerm::normalize);
  
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

  public static Map<ColdpTerm, List<ColdpTerm>> RESOURCES = ImmutableMap.<ColdpTerm, List<ColdpTerm>>builder()
      .put(Reference, ImmutableList.of(
          ID,
          citation,
          author,
          title,
          year,
          source,
          doi,
          link)
      ).put(Name, ImmutableList.of(
          ID,
          originalNameID,
          scientificName,
          authorship,
          rank,
          uninomial,
          genus,
          specificEpithet,
          infraspecificEpithet,
          cultivarEpithet,
      namePhrase,
          publishedInID,
          publishedInPage,
          publishedInYear,
          code,
          status,
          link,
          remarks)
      ).put(NameRelation, ImmutableList.of(
          nameID,
          relatedNameID,
          type,
          publishedInID,
          remarks)
      ).put(TypeMaterial, ImmutableList.of(
          nameID,
          citation,
          status,
          referenceID,
          link,
          remarks)
      ).put(Taxon, ImmutableList.of(
          ID,
          parentID,
          nameID,
      namePhrase,
          accordingToID,
          provisional,
          referenceID,
          scrutinizer,
          scrutinizerDate,
          extinct,
          temporalRangeStart,
          temporalRangeEnd,
          lifezone,
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
          link,
          remarks)
      ).put(Synonym, ImmutableList.of(
          ID,
          taxonID,
          nameID,
      namePhrase,
          accordingToID,
          status,
          referenceID,
          link,
          remarks)
      ).put(NameUsage, ImmutableList.of(
          ID,
          parentID,
          originalNameID,
          scientificName,
          authorship,
          rank,
          uninomial,
          genericName,
          specificEpithet,
          infraspecificEpithet,
          cultivarEpithet,
      namePhrase,
          publishedInID,
          publishedInPage,
          publishedInYear,
          code,
          nameStatus,
      namePhrase,
          accordingToID,
          status,
          referenceID,
          scrutinizer,
          scrutinizerDate,
          extinct,
          temporalRangeStart,
          temporalRangeEnd,
          lifezone,
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
          link,
          remarks)
      ).put(TaxonRelation, ImmutableList.of(
          taxonID,
          relatedTaxonID,
          type,
          referenceID,
          remarks)
      ).put(Treatment, ImmutableList.of(
          taxonID,
          document,
          format)
      ).put(Distribution, ImmutableList.of(
          taxonID,
          area,
          gazetteer,
          status,
          referenceID)
      ).put(Media, ImmutableList.of(
          taxonID,
          url,
          type,
          format,
          title,
          created,
          creator,
          license,
          link)
      ).put(VernacularName, ImmutableList.of(
          taxonID,
          name,
          transliteration,
          language,
          country,
          sex,
          referenceID)
      ).build();
  
  private static final String PREFIX = "col";
  private static final String NS = "http://catalogueoflife.org/terms/";
  private static final URI NS_URI = URI.create(NS);
  
  private final boolean isClass;
  private final String[] alternatives;
  
  ColdpTerm() {
    this.alternatives = new String[0];
    this.isClass = false;
  }
  
  ColdpTerm(boolean isClass, String... alternatives) {
    this.alternatives = alternatives;
    this.isClass = isClass;
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
    return isClass;
  }
  
  
  private static String normalize(String x, boolean isClass) {
    x = x.replaceAll("[-_ ]+", "").toLowerCase();
    return isClass ? Character.toUpperCase(x.charAt(0)) + x.substring(1) : x;
  }
  
  private static String normalize(ColdpTerm t) {
    return normalize(t.name(), t.isClass);
  }
  
  public static ColdpTerm find(String name, boolean isClass) {
    return LOOKUP.getOrDefault(normalize(name, isClass), null);
  }
}
