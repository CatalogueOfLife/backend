package life.catalogue.importer.neo.model;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.api.vocab.TaxRelType;
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
  
  HOMOTYPIC("ht", NomRelType.HOMOTYPIC);
  
  public final NomRelType nomRelType;
  public final TaxRelType taxRelType;
  public final String abbrev;
  
  RelType(String abbrev) {
    this.abbrev = Preconditions.checkNotNull(abbrev);
    this.nomRelType = null;
    this.taxRelType = null;
  }
  
  RelType(String abbrev, NomRelType type) {
    this.abbrev = Preconditions.checkNotNull(abbrev);
    this.nomRelType = type;
    this.taxRelType = null;
  }

  RelType(String abbrev, TaxRelType type) {
    this.abbrev = Preconditions.checkNotNull(abbrev);
    this.nomRelType = null;
    this.taxRelType = type;
  }

  private final static Map<NomRelType, RelType> NOM_MAP = Arrays.stream(values())
      .filter(relType -> relType.nomRelType != null)
      .collect(ImmutableMap.toImmutableMap(rt -> rt.nomRelType, Function.identity()));

  private final static Map<TaxRelType, RelType> TAX_MAP = Arrays.stream(values())
    .filter(relType -> relType.taxRelType != null)
    .collect(ImmutableMap.toImmutableMap(rt -> rt.taxRelType, Function.identity()));

  public boolean isNameRel(){
    return nomRelType != null;
  }

  public boolean isTaxonRel(){
    return taxRelType != null;
  }

  public static RelType from(NomRelType rt) {
    return NOM_MAP.get(rt);
  }

  public static RelType from(TaxRelType rt) {
    return TAX_MAP.get(rt);
  }
}
