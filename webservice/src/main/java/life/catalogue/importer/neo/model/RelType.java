package life.catalogue.importer.neo.model;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.api.vocab.SpeciesInteractionType;
import life.catalogue.api.vocab.TaxonConceptRelType;
import org.neo4j.graphdb.RelationshipType;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

/**
 *
 */
public enum RelType implements RelationshipType {

  /**
   * Taxon/Synonym -> Name
   */
  HAS_NAME("name"),

  /**
   * Taxon -> Taxon
   */
  PARENT_OF("par"),
  
  /**
   * Synonym -> Taxon
   * with optional property "homotypic" of any non null value
   * to indicate a homotypic synonym which results in sharing the same homotypic group key at the end
   */
  SYNONYM_OF("syn"),

  // NAME RELATIONS

  /**
   * Name -> Name
   */
  HAS_BASIONYM("bas", NomRelType.BASIONYM),
  SPELLING_CORRECTION_OF("corr", NomRelType.SPELLING_CORRECTION),
  BASED_ON("ex", NomRelType.BASED_ON),
  REPLACEMENT_NAME_OF("nov", NomRelType.REPLACEMENT_NAME),
  CONSERVED_AGAINST("cons", NomRelType.CONSERVED),
  LATER_HOMONYM_OF("hom", NomRelType.LATER_HOMONYM),
  SUPERFLUOUS_BECAUSE_OF("superfl", NomRelType.SUPERFLUOUS),
  TYPE("typ", NomRelType.TYPE),
  HOMOTYPIC("ht", NomRelType.HOMOTYPIC),

  // TAX RELATIONS
  EQUALS("eq", TaxonConceptRelType.EQUALS),
  INCLUDES("incl", TaxonConceptRelType.INCLUDES),
  INCLUDED_IN("inclIn", TaxonConceptRelType.INCLUDED_IN),
  OVERLAPS("over", TaxonConceptRelType.OVERLAPS),
  EXCLUDES("excl", TaxonConceptRelType.EXCLUDES),

  // SPECIES INTERACTIONS
  RELATED_TO("related_to", SpeciesInteractionType.RELATED_TO),
  CO_OCCURS_WITH("co_occurs_with", SpeciesInteractionType.CO_OCCURS_WITH),
  INTERACTS_WITH("interacts_with", SpeciesInteractionType.INTERACTS_WITH),
  ADJACENT_TO("adjacent_to", SpeciesInteractionType.ADJACENT_TO),
  SYMBIONT_OF("symbiont_of", SpeciesInteractionType.SYMBIONT_OF),
  EATS("eats", SpeciesInteractionType.EATS),
  EATEN_BY("eaten_by", SpeciesInteractionType.EATEN_BY),
  KILLS("kills", SpeciesInteractionType.KILLS),
  KILLED_BY("killed_by", SpeciesInteractionType.KILLED_BY),
  PREYS_UPON("preys_upon", SpeciesInteractionType.PREYS_UPON),
  PREYED_UPON_BY("preyed_upon_by", SpeciesInteractionType.PREYED_UPON_BY),
  HOST_OF("host_of", SpeciesInteractionType.HOST_OF),
  HAS_HOST("has_host", SpeciesInteractionType.HAS_HOST),
  PARASITE_OF("parasite_of", SpeciesInteractionType.PARASITE_OF),
  HAS_PARASITE("has_parasite", SpeciesInteractionType.HAS_PARASITE),
  PATHOGEN_OF("pathogen_of", SpeciesInteractionType.PATHOGEN_OF),
  HAS_PATHOGEN("has_pathogen", SpeciesInteractionType.HAS_PATHOGEN),
  VECTOR_OF("vector_of", SpeciesInteractionType.VECTOR_OF),
  HAS_VECTOR("has_vector", SpeciesInteractionType.HAS_VECTOR),
  ENDOPARASITE_OF("endoparasite_of", SpeciesInteractionType.ENDOPARASITE_OF),
  HAS_ENDOPARASITE("has_endoparasite", SpeciesInteractionType.HAS_ENDOPARASITE),
  ECTOPARASITE_OF("ectoparasite_of", SpeciesInteractionType.ECTOPARASITE_OF),
  HAS_ECTOPARASITE("has_ectoparasite", SpeciesInteractionType.HAS_ECTOPARASITE),
  HYPERPARASITE_OF("hyperparasite_of", SpeciesInteractionType.HYPERPARASITE_OF),
  HAS_HYPERPARASITE("has_hyperparasite", SpeciesInteractionType.HAS_HYPERPARASITE),
  KLEPTOPARASITE_OF("kleptoparasite_of", SpeciesInteractionType.KLEPTOPARASITE_OF),
  HAS_KLEPTOPARASITE("has_kleptoparasite", SpeciesInteractionType.HAS_KLEPTOPARASITE),
  PARASITOID_OF("parasitoid_of", SpeciesInteractionType.PARASITOID_OF),
  HAS_PARASITOID("has_parasitoid", SpeciesInteractionType.HAS_PARASITOID),
  HYPERPARASITOID_OF("hyperparasitoid_of", SpeciesInteractionType.HYPERPARASITOID_OF),
  HAS_HYPERPARASITOID("has_hyperparasitoid", SpeciesInteractionType.HAS_HYPERPARASITOID),
  VISITS("visits", SpeciesInteractionType.VISITS),
  VISITED_BY("visited_by", SpeciesInteractionType.VISITED_BY),
  VISITS_FLOWERS_OF("visits_flowers_of", SpeciesInteractionType.VISITS_FLOWERS_OF),
  FLOWERS_VISITED_BY("flowers_visited_by", SpeciesInteractionType.FLOWERS_VISITED_BY),
  POLLINATES("pollinates", SpeciesInteractionType.POLLINATES),
  POLLINATED_BY("pollinated_by", SpeciesInteractionType.POLLINATED_BY),
  LAYS_EGGS_ON("lays_eggs_on", SpeciesInteractionType.LAYS_EGGS_ON),
  HAS_EGGS_LAYED_ON_BY("has_eggs_layed_on_by", SpeciesInteractionType.HAS_EGGS_LAYED_ON_BY),
  EPIPHYTE_OF("epiphyte_of", SpeciesInteractionType.EPIPHYTE_OF),
  HAS_EPIPHYTE("has_epiphyte", SpeciesInteractionType.HAS_EPIPHYTE),
  COMMENSALIST_OF("commensalist_of", SpeciesInteractionType.COMMENSALIST_OF),
  MUTUALIST_OF("mutualist_of", SpeciesInteractionType.MUTUALIST_OF);

  public final NomRelType nomRelType;
  public final TaxonConceptRelType taxRelType;
  public final SpeciesInteractionType specInterType;
  public final String abbrev;
  
  RelType(String abbrev) {
    this.abbrev = Preconditions.checkNotNull(abbrev);
    this.nomRelType = null;
    this.taxRelType = null;
    this.specInterType = null;
  }
  
  RelType(String abbrev, NomRelType type) {
    this.abbrev = Preconditions.checkNotNull(abbrev);
    this.nomRelType = type;
    this.taxRelType = null;
    this.specInterType = null;
  }

  RelType(String abbrev, TaxonConceptRelType type) {
    this.abbrev = Preconditions.checkNotNull(abbrev);
    this.nomRelType = null;
    this.taxRelType = type;
    this.specInterType = null;
  }

  RelType(String abbrev, SpeciesInteractionType type) {
    this.abbrev = Preconditions.checkNotNull(abbrev);
    this.nomRelType = null;
    this.taxRelType = null;
    this.specInterType = type;
  }

  private final static Map<NomRelType, RelType> NOM_MAP = Arrays.stream(values())
      .filter(relType -> relType.nomRelType != null)
      .collect(ImmutableMap.toImmutableMap(rt -> rt.nomRelType, Function.identity()));

  private final static Map<TaxonConceptRelType, RelType> TAX_MAP = Arrays.stream(values())
    .filter(relType -> relType.taxRelType != null)
    .collect(ImmutableMap.toImmutableMap(rt -> rt.taxRelType, Function.identity()));

  private final static Map<SpeciesInteractionType, RelType> SPEC_MAP = Arrays.stream(values())
    .filter(relType -> relType.specInterType != null)
    .collect(ImmutableMap.toImmutableMap(rt -> rt.specInterType, Function.identity()));

  public boolean isNameRel(){
    return nomRelType != null;
  }

  public boolean isTaxonConceptRel(){
    return taxRelType != null;
  }

  public boolean isSpeciesInteraction(){
    return specInterType != null;
  }

  public Class<? extends Enum> relationClass(){
    if (nomRelType != null) return NomRelType.class;
    if (taxRelType != null) return TaxonConceptRelType.class;
    if (specInterType != null) return SpeciesInteractionType.class;
    return null;
  }

  public static RelType from(NomRelType rt) {
    return NOM_MAP.get(rt);
  }

  public static RelType from(TaxonConceptRelType rt) {
    return TAX_MAP.get(rt);
  }

  public static RelType from(SpeciesInteractionType rt) {
    return SPEC_MAP.get(rt);
  }
}
