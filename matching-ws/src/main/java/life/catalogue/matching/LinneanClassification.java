package life.catalogue.matching;

import org.gbif.api.vocabulary.Rank;

public interface LinneanClassification {
  String getKingdom();

  String getPhylum();

  String getClazz();

  String getOrder();

  String getFamily();

  String getGenus();

  String getSubgenus();

  String getSpecies();

  void setKingdom(String v);

  void setPhylum(String v);

  void setClazz(String v);

  void setOrder(String v);

  void setFamily(String v);

  void setGenus(String v);

  void setSubgenus(String v);

  void setSpecies(String v);

  default String getHigherRank(Rank rank) {
    if (rank != null) {
      switch (rank) {
        case KINGDOM:
          return getKingdom();
        case PHYLUM:
          return getPhylum();
        case CLASS:
          return getClazz();
        case ORDER:
          return getOrder();
        case FAMILY:
          return getFamily();
        case GENUS:
          return getGenus();
        case SUBGENUS:
          return getSubgenus();
        case SPECIES:
          return getSpecies();
      }
    }
    return null;
  }

  default void setHigherRank(String name, Rank rank) {
    if (rank != null) {
      switch (rank) {
        case KINGDOM:
          setKingdom(name);
          break;
        case PHYLUM:
          setPhylum(name);
          break;
        case CLASS:
          setClazz(name);
          break;
        case ORDER:
          setOrder(name);
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
        case SPECIES:
          setSpecies(name);
      }
    }
  }
}
