package life.catalogue.api.model;

import life.catalogue.coldp.ColdpTerm;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Objects;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import static life.catalogue.common.tax.SciNameNormalizer.removeDagger;
import static life.catalogue.common.tax.SciNameNormalizer.removeHybridMarker;
/**
 *
 */
public class Classification {
  public static final List<Rank> RANKS = ImmutableList.of(
      Rank.SUPERKINGDOM,
      Rank.KINGDOM,
      Rank.SUBKINGDOM,
      Rank.SUPERPHYLUM,
      Rank.PHYLUM,
      Rank.SUBPHYLUM,
      Rank.SUPERCLASS,
      Rank.CLASS,
      Rank.SUBCLASS,
      Rank.SUPERORDER,
      Rank.ORDER,
      Rank.SUBORDER,
      Rank.SUPERFAMILY,
      Rank.FAMILY,
      Rank.SUBFAMILY,
      Rank.TRIBE,
      Rank.SUBTRIBE,
      Rank.GENUS,
      Rank.SUBGENUS,
      Rank.SECTION, // this is a botanical placement!
      Rank.SPECIES
  );
  private static final List<Rank> RANKS_REVERSED = ImmutableList.copyOf(Lists.reverse(RANKS));

  private String superkingdom;
  private String kingdom;
  private String subkingdom;
  private String superphylum;
  private String phylum;
  private String subphylum;
  private String superclass;
  private String class_;
  private String subclass;
  private String superorder;
  private String order;
  private String suborder;
  private String superfamily;
  private String family;
  private String subfamily;
  private String tribe;
  private String subtribe;
  private String genus;
  private String subgenus;
  private String section;
  private String species;

  public Classification() {
  }

  public Classification(Classification other) {
    this.superkingdom = other.superkingdom;
    this.kingdom = other.kingdom;
    this.subkingdom = other.subkingdom;
    this.superphylum = other.superphylum;
    this.phylum = other.phylum;
    this.subphylum = other.subphylum;
    this.superclass = other.superclass;
    this.class_ = other.class_;
    this.subclass = other.subclass;
    this.superorder = other.superorder;
    this.order = other.order;
    this.suborder = other.suborder;
    this.superfamily = other.superfamily;
    this.family = other.family;
    this.subfamily = other.subfamily;
    this.tribe = other.tribe;
    this.subtribe = other.subtribe;
    this.genus = other.genus;
    this.subgenus = other.subgenus;
    this.section = other.section;
    this.species = other.species;
  }

  public String getSuperkingdom() {
    return superkingdom;
  }

  public void setSuperkingdom(String superkingdom) {
    this.superkingdom = superkingdom;
  }

  public String getKingdom() {
    return kingdom;
  }
  
  public void setKingdom(String kingdom) {
    this.kingdom = kingdom;
  }

  public String getSubkingdom() {
    return subkingdom;
  }

  public void setSubkingdom(String subkingdom) {
    this.subkingdom = subkingdom;
  }

  public String getSuperphylum() {
    return superphylum;
  }

  public void setSuperphylum(String superphylum) {
    this.superphylum = superphylum;
  }

  public String getPhylum() {
    return phylum;
  }
  
  public void setPhylum(String phylum) {
    this.phylum = phylum;
  }
  
  public String getSuperclass() {
    return superclass;
  }

  public void setSuperclass(String superclass) {
    this.superclass = superclass;
  }

  public String getClass_() {
    return class_;
  }

  public void setClass_(String class_) {
    this.class_ = class_;
  }

  public String getSuperorder() {
    return superorder;
  }

  public void setSuperorder(String superorder) {
    this.superorder = superorder;
  }

  public String getOrder() {
    return order;
  }
  
  public void setOrder(String order) {
    this.order = order;
  }
  
  public String getSuperfamily() {
    return superfamily;
  }
  
  public void setSuperfamily(String superfamily) {
    this.superfamily = superfamily;
  }
  
  public String getFamily() {
    return family;
  }
  
  public void setFamily(String family) {
    this.family = family;
  }
  
  public String getTribe() {
    return tribe;
  }
  
  public void setTribe(String tribe) {
    this.tribe = tribe;
  }
  
  public String getSubtribe() {
    return subtribe;
  }
  
  public void setSubtribe(String subtribe) {
    this.subtribe = subtribe;
  }
  
  public String getGenus() {
    return genus;
  }
  
  public void setGenus(String genus) {
    this.genus = genus;
  }
  
  public String getSubgenus() {
    return subgenus;
  }
  
  public void setSubgenus(String subgenus) {
    this.subgenus = subgenus;
  }
  
  public String getSection() {
    return section;
  }
  
  public void setSection(String section) {
    this.section = section;
  }
  
  public String getSpecies() {
    return species;
  }
  
  public void setSpecies(String species) {
    this.species = species;
  }
  
  public String getSubphylum() {
    return subphylum;
  }
  
  public void setSubphylum(String subphylum) {
    this.subphylum = subphylum;
  }
  
  public String getSubclass() {
    return subclass;
  }
  
  public void setSubclass(String subclass) {
    this.subclass = subclass;
  }
  
  public String getSuborder() {
    return suborder;
  }
  
  public void setSuborder(String suborder) {
    this.suborder = suborder;
  }
  
  public String getSubfamily() {
    return subfamily;
  }
  
  public void setSubfamily(String subfamily) {
    this.subfamily = subfamily;
  }
  
  public boolean setByTerm(DwcTerm rank, String name) {
    switch (rank) {
      case kingdom:
        setKingdom(name);
        return true;
      case phylum:
        setPhylum(name);
        return true;
      case class_:
        setClass_(name);
        return true;
      case order:
        setOrder(name);
        return true;
      case family:
        setFamily(name);
        return true;
      case subfamily:
        setSubfamily(name);
        return true;
      case genus:
        setGenus(name);
        return true;
      case subgenus:
        setSubgenus(name);
        return true;
    }
    return false;
  }

  public boolean setByTerm(ColdpTerm rank, String name) {
    switch (rank) {
      case kingdom:
        setKingdom(name);
        return true;
      case phylum:
        setPhylum(name);
        return true;
      case subphylum:
        setSubphylum(name);
        return true;
      case class_:
        setClass_(name);
        return true;
      case subclass:
        setSubclass(name);
        return true;
      case order:
        setOrder(name);
        return true;
      case suborder:
        setSuborder(name);
        return true;
      case superfamily:
        setSuperfamily(name);
        return true;
      case family:
        setFamily(name);
        return true;
      case subfamily:
        setSubfamily(name);
        return true;
      case tribe:
        setTribe(name);
        return true;
      case subtribe:
        setSubtribe(name);
        return true;
      case genus:
        setGenus(name);
        return true;
      case subgenus:
        setSubgenus(name);
        return true;
      case section:
        setSection(name);
        return true;
      case species:
        setSpecies(name);
        return true;
    }
    return false;
  }

  public boolean setByRank(Rank rank, String name) {
    switch (rank) {
      case SUPERKINGDOM:
        setSuperkingdom(name);
        return true;
      case KINGDOM:
        setKingdom(name);
        return true;
      case SUBKINGDOM:
        setSubkingdom(name);
        return true;
      case SUPERPHYLUM:
        setSuperphylum(name);
        return true;
      case PHYLUM:
        setPhylum(name);
        return true;
      case SUBPHYLUM:
        setSubphylum(name);
        return true;
      case SUPERCLASS:
        setSuperclass(name);
        return true;
      case CLASS:
        setClass_(name);
        return true;
      case SUBCLASS:
        setSubclass(name);
        return true;
      case SUPERORDER:
        setSuperorder(name);
        return true;
      case ORDER:
        setOrder(name);
        return true;
      case SUBORDER:
        setSuborder(name);
        return true;
      case SUPERFAMILY:
        setSuperfamily(name);
        return true;
      case FAMILY:
        setFamily(name);
        return true;
      case SUBFAMILY:
        setSubfamily(name);
        return true;
      case TRIBE:
        setTribe(name);
        return true;
      case SUBTRIBE:
        setSubtribe(name);
        return true;
      case GENUS:
        setGenus(name);
        return true;
      case SUBGENUS:
        setSubgenus(name);
        return true;
      case SECTION:
        setSection(name);
        return true;
      case SPECIES:
        setSpecies(name);
        return true;
    }
    return false;
  }

  /**
   * @return the name at given rank, cleaned from potential hybrid markers and dagger symbols
   */
  public String getByRankCleaned(Rank rank) {
    return clean(getByRank(rank));
  }

  public String getByRank(Rank rank) {
    switch (rank) {
      case SUPERKINGDOM:
        return getSuperkingdom();
      case KINGDOM:
        return getKingdom();
      case SUBKINGDOM:
        return getSubkingdom();
      case SUPERPHYLUM:
        return getSuperphylum();
      case PHYLUM:
        return getPhylum();
      case SUBPHYLUM:
        return getSubphylum();
      case SUPERCLASS:
        return getSuperclass();
      case CLASS:
        return getClass_();
      case SUBCLASS:
        return getSubclass();
      case SUPERORDER:
        return getSuperorder();
      case ORDER:
        return getOrder();
      case SUBORDER:
        return getSuborder();
      case SUPERFAMILY:
        return getSuperfamily();
      case FAMILY:
        return getFamily();
      case SUBFAMILY:
        return getSubfamily();
      case TRIBE:
        return getTribe();
      case SUBTRIBE:
        return getSubtribe();
      case GENUS:
        return getGenus();
      case SUBGENUS:
        return getSubgenus();
      case SECTION:
        return getSection();
      case SPECIES:
        return getSpecies();
    }
    return null;
  }
  
  public void clearRankAndBelow(Rank rank) {
    for (Rank r : RANKS_REVERSED) {
      if (r.higherThan(rank)) {
        break;
      }
      setByRank(r, null);
    }
  }
  
  public Rank getLowestExistingRank() {
    for (Rank r : RANKS_REVERSED) {
      if (getByRank(r) != null) {
        return r;
      }
    }
    return null;
  }
  
  public boolean isEmpty() {
    for (Rank r : RANKS) {
      if (getByRank(r) != null) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Classification)) return false;
    Classification that = (Classification) o;
    return Objects.equals(superkingdom, that.superkingdom)
           && Objects.equals(kingdom, that.kingdom)
           && Objects.equals(subkingdom, that.subkingdom)
           && Objects.equals(superphylum, that.superphylum)
           && Objects.equals(phylum, that.phylum)
           && Objects.equals(subphylum, that.subphylum)
           && Objects.equals(superclass, that.superclass)
           && Objects.equals(class_, that.class_)
           && Objects.equals(subclass, that.subclass)
           && Objects.equals(superorder, that.superorder)
           && Objects.equals(order, that.order)
           && Objects.equals(suborder, that.suborder)
           && Objects.equals(superfamily, that.superfamily)
           && Objects.equals(family, that.family)
           && Objects.equals(subfamily, that.subfamily)
           && Objects.equals(tribe, that.tribe)
           && Objects.equals(subtribe, that.subtribe)
           && Objects.equals(genus, that.genus)
           && Objects.equals(subgenus, that.subgenus)
           && Objects.equals(section, that.section)
           && Objects.equals(species, that.species);
  }

  @Override
  public int hashCode() {
    return Objects.hash(superkingdom, kingdom, subkingdom, superphylum, phylum, subphylum, superclass, class_, subclass, superorder, order, suborder, superfamily, family, subfamily, tribe, subtribe, genus, subgenus, section, species);
  }

  public boolean equalsAboveRank(Classification o, Rank lowest) {
    if (this == o) return true;
    if (o == null) return false;
    for (Rank r : RANKS) {
      if (r.higherThan(lowest)) {
        if (!Objects.equals(clean(this.getByRank(r)), clean(o.getByRank(r)))) {
          return false;
        }
      } else {
        return true;
      }
    }
    return false;
  }

  private static String clean(String x) {
    return removeHybridMarker(removeDagger(x));
  }
  
}
