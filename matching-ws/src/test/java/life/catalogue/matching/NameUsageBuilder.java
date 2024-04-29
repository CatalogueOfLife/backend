package life.catalogue.matching;

import java.util.List;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import lombok.Builder;
import org.gbif.nameparser.api.Rank;

class NameUsageBuilder {

  @Builder(builderMethodName = "builder")
  public static NameUsageMatch newNameUsageMatch(
      String usageKey,
      String acceptedUsageKey,
      String scientificName,
      String canonicalName,
      Rank rank,
      TaxonomicStatus status,
      Integer confidence,
      String note,
      MatchType matchType,
      List<NameUsageMatch> alternatives,
      String kingdom,
      String phylum,
      String clazz,
      String order,
      String family,
      String genus,
      String subgenus,
      String species,
      String kingdomKey,
      String phylumKey,
      String classKey,
      String orderKey,
      String familyKey,
      String genusKey,
      String subgenusKey,
      String speciesKey) {

    NameUsageMatch m = new NameUsageMatch();
    m.setUsage(new RankedName());
    m.getUsage().setKey(usageKey);
    m.getUsage().setName(scientificName);
    m.getUsage().setCanonicalName(canonicalName);
    m.getUsage().setRank(rank);
    m.setAcceptedUsage(new RankedName(acceptedUsageKey, null, null));
    m.setStatus(status);
    m.getDiagnostics().setConfidence(confidence);
    m.getDiagnostics().setNote(note);
    m.getDiagnostics().setMatchType(matchType);
    m.setAlternatives(alternatives);
    m.setKingdom(kingdom);
    m.setPhylum(phylum);
    m.setClazz(clazz);
    m.setOrder(order);
    m.setFamily(family);
    m.setGenus(genus);
    m.setSubgenus(subgenus);
    m.setSpecies(species);
    m.setKingdomKey(kingdomKey);
    m.setPhylumKey(phylumKey);
    m.setClassKey(classKey);
    m.setOrderKey(orderKey);
    m.setFamilyKey(familyKey);
    m.setGenusKey(genusKey);
    m.setSubgenusKey(subgenusKey);
    m.setSpeciesKey(speciesKey);
    return m;
  }
}
