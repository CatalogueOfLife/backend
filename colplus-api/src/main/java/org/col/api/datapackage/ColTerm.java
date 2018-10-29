package org.col.api.datapackage;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.gbif.dwc.terms.AlternativeNames;
import org.gbif.dwc.terms.Term;

/**
 * CoL terms covering all columns needed for the new CoL Data Package submission format:
 * https://github.com/CatalogueOfLife/datapackage-specs
 *
 * To avoid dependency and clashes with DwC no terms are reused.
 */
public enum ColTerm implements Term, AlternativeNames {
  Reference(true),
  ID,
  citation,
  author,
  title,
  year,
  source,
  
  Name(true),
  //ID,
  scientificName,
  authorship,
  rank,
  genus,
  specificEpithet,
  infraspecificEpithet,
  publishedInID,
  publishedInPage,
  code,
  status,
  link,
  remarks,
  
  NameRel(true),
  nameID,
  relatedNameID,
  type,
  //publishedInID,
  //remarks,
  
  
  Taxon(true),
  // ID,
  parentID,
  //nameID,
  referenceID,
  provisional,
  accordingTo,
  accordingToID,
  accordingToDate,
  fossil,
  recent,
  lifezone,
  //link,
  subgenus,
  //genus,
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
      
  Synonym(true),
  taxonID,
  //nameID,
  //status,
  //remarks
  
  
  Description(true),
  //taxonID,
  category,
  description,
  language,
  //referenceID,
  
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
  format,
  //title,
  created,
  creator,
  license,
  //link,
  
  VernacularName(true),
  //taxonID,
  name,
  transliteration,
  //language,
  country,
  lifeStage,
  sex
  //referenceID
  ;
  
  private static Map<String, ColTerm> LOOKUP = Maps.uniqueIndex(Arrays.asList(values()), ColTerm::normalize);

  public static Map<ColTerm, List<ColTerm>> RESOURCES = ImmutableMap.<ColTerm, List<ColTerm>>builder()
      .put(Reference, ImmutableList.of(
          ID,
          citation,
          author,
          title,
          year,
          source)
      ).put(Name, ImmutableList.of(
          ID,
          scientificName,
          authorship,
          rank,
          genus,
          specificEpithet,
          infraspecificEpithet,
          publishedInID,
          publishedInPage,
          code,
          status,
          link,
          remarks)
      ).put(NameRel, ImmutableList.of(
          nameID,
          relatedNameID,
          type,
          publishedInID,
          remarks)
      ).put(Taxon, ImmutableList.of(
          ID,
          parentID,
          nameID,
          referenceID,
          provisional,
          accordingTo,
          accordingToID,
          accordingToDate,
          fossil,
          recent,
          lifezone,
          link,
          subgenus,
          genus,
          subfamily,
          family,
          superfamily,
          suborder,
          order,
          subclass,
          class_,
          subphylum,
          phylum,
          kingdom)
      ).put(Synonym, ImmutableList.of(
          taxonID,
          nameID,
          status,
          remarks)
      ).put(Description, ImmutableList.of(
          taxonID,
          category,
          description,
          language,
          referenceID)
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
          lifeStage,
          sex,
          referenceID)
      ).build();

  private static final String PREFIX = "col";
  private static final String NS = "http://rs.col.plus/terms/";
  private static final URI NS_URI = URI.create(NS);
  
  private final boolean isClass;
  private final String[] alternatives;
  
  ColTerm() {
    this.alternatives = new String[0];
    this.isClass = false;
  }

  ColTerm(boolean isClass, String... alternatives) {
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
  
  private static String normalize(ColTerm t) {
    return normalize(t.name(), t.isClass);
  }
  
  public static ColTerm find(String name, boolean isClass) {
    return LOOKUP.getOrDefault(normalize(name, isClass), null);
  }
}
