package org.col.api.model;

import java.util.List;
import java.util.Objects;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.col.api.datapackage.ColTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.nameparser.api.Rank;

/**
 *
 */
public class Classification {
  public static final List<Rank> RANKS = ImmutableList.of(
      Rank.KINGDOM,
      Rank.PHYLUM,
      Rank.SUBPHYLUM,
      Rank.CLASS,
      Rank.SUBCLASS,
      Rank.ORDER,
      Rank.SUBORDER,
      Rank.SUPERFAMILY,
      Rank.FAMILY,
      Rank.SUBFAMILY,
      Rank.GENUS,
      Rank.SUBGENUS
  );
  private static final List<Rank> RANKS_REVERSED = ImmutableList.copyOf(Lists.reverse(RANKS));
  
  private String kingdom;
  private String phylum;
  private String subphylum;
  private String class_;
  private String subclass;
  private String order;
  private String suborder;
  private String superfamily;
  private String family;
  private String subfamily;
  private String genus;
  private String subgenus;
  
  public static Classification copy(Classification src) {
    Classification cl = new Classification();
    cl.setKingdom(src.kingdom);
    cl.setPhylum(src.phylum);
    cl.setSubphylum(src.subphylum);
    cl.setClass_(src.class_);
    cl.setSubclass(src.subclass);
    cl.setOrder(src.order);
    cl.setSuborder(src.suborder);
    cl.setSuperfamily(src.superfamily);
    cl.setFamily(src.family);
    cl.setSubfamily(src.subfamily);
    cl.setGenus(src.genus);
    cl.setSubgenus(src.subgenus);
    return cl;
  }
  
  public String getKingdom() {
    return kingdom;
  }
  
  public void setKingdom(String kingdom) {
    this.kingdom = kingdom;
  }
  
  public String getPhylum() {
    return phylum;
  }
  
  public void setPhylum(String phylum) {
    this.phylum = phylum;
  }
  
  public String getClass_() {
    return class_;
  }
  
  public void setClass_(String class_) {
    this.class_ = class_;
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
      case genus:
        setGenus(name);
        return true;
      case subgenus:
        setSubgenus(name);
        return true;
    }
    return false;
  }
  
  public boolean setByTerm(ColTerm rank, String name) {
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
      case genus:
        setGenus(name);
        return true;
      case subgenus:
        setSubgenus(name);
        return true;
    }
    return false;
  }

  public void setByRank(Rank rank, String name) {
    switch (rank) {
      case KINGDOM:
        setKingdom(name);
        break;
      case PHYLUM:
        setPhylum(name);
        break;
      case CLASS:
        setClass_(name);
        break;
      case ORDER:
        setOrder(name);
        break;
      case SUPERFAMILY:
        setSuperfamily(name);
        break;
      case FAMILY:
        setFamily(name);
        break;
      case GENUS:
        setGenus(name);
        break;
      case SUBGENUS:
        setSubgenus(name);
        break;
    }
  }
  
  public String getByRank(Rank rank) {
    switch (rank) {
      case KINGDOM:
        return getKingdom();
      case PHYLUM:
        return getPhylum();
      case SUBPHYLUM:
        return getSubphylum();
      case CLASS:
        return getClass_();
      case SUBCLASS:
        return getSubclass();
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
      case GENUS:
        return getGenus();
      case SUBGENUS:
        return getSubgenus();
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
    if (o == null || getClass() != o.getClass()) return false;
    Classification that = (Classification) o;
    return Objects.equals(kingdom, that.kingdom) &&
        Objects.equals(phylum, that.phylum) &&
        Objects.equals(subphylum, that.subphylum) &&
        Objects.equals(class_, that.class_) &&
        Objects.equals(subclass, that.subclass) &&
        Objects.equals(order, that.order) &&
        Objects.equals(suborder, that.suborder) &&
        Objects.equals(superfamily, that.superfamily) &&
        Objects.equals(family, that.family) &&
        Objects.equals(subfamily, that.subfamily) &&
        Objects.equals(genus, that.genus) &&
        Objects.equals(subgenus, that.subgenus);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(kingdom, phylum, subphylum, class_, subclass, order, suborder, superfamily, family, subfamily, genus, subgenus);
  }
  
  public boolean equalsAboveRank(Classification o, Rank lowest) {
    if (this == o) return true;
    if (o == null) return false;
    for (Rank r : RANKS) {
      if (r.higherThan(lowest)) {
        if (!Objects.equals(this.getByRank(r), o.getByRank(r))) {
          return false;
        }
      } else {
        return true;
      }
    }
    return false;
  }
  
}
