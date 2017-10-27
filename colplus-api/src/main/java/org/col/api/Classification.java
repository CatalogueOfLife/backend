package org.col.api;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.col.api.vocab.Rank;
import org.gbif.dwc.terms.DwcTerm;

import java.util.List;

/**
 *
 */
public class Classification {
  public static final List<Rank> RANKS = ImmutableList.of(
      Rank.KINGDOM,
      Rank.PHYLUM,
      Rank.CLASS,
      Rank.ORDER,
      Rank.SUPERFAMILY,
      Rank.FAMILY,
      Rank.GENUS,
      Rank.SUBGENUS
  );
  private static final List<Rank> RANKS_REVERSED = ImmutableList.copyOf(Lists.reverse(RANKS));

  private String kingdom;
  private String phylum;
  private String class_;
  private String order;
  private String superfamily;
  private String family;
  private String genus;
  private String subgenus;

  public static Classification copy(Classification src) {
    Classification cl = new Classification();
    cl.setKingdom(src.kingdom);
    cl.setPhylum(src.phylum);
    cl.setClass_(src.class_);
    cl.setOrder(src.order);
    cl.setSuperfamily(src.superfamily);
    cl.setFamily(src.family);
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

  public void setByTerm(DwcTerm rank, String name) {
    switch (rank) {
      case kingdom: setKingdom(name); break;
      case phylum: setPhylum(name); break;
      case class_: setClass_(name); break;
      case order: setOrder(name); break;
      case family: setFamily(name); break;
      case genus: setGenus(name); break;
      case subgenus: setSubgenus(name); break;
    }
  }

  public void setByRank(Rank rank, String name) {
    switch (rank) {
      case KINGDOM: setKingdom(name); break;
      case PHYLUM: setPhylum(name); break;
      case CLASS: setClass_(name); break;
      case ORDER: setOrder(name); break;
      case SUPERFAMILY: setSuperfamily(name); break;
      case FAMILY: setFamily(name); break;
      case GENUS: setGenus(name); break;
      case SUBGENUS: setSubgenus(name); break;
    }
  }

  public String getByRank(Rank rank) {
    switch (rank) {
      case KINGDOM: return getKingdom();
      case PHYLUM: return getPhylum();
      case CLASS: return getClass_();
      case ORDER: return getOrder();
      case SUPERFAMILY: return getSuperfamily();
      case FAMILY: return getFamily();
      case GENUS: return getGenus();
      case SUBGENUS: return getSubgenus();
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

}
