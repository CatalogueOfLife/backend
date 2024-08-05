package life.catalogue.matching;

import java.util.List;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.matching.model.NameUsageMatch;

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

    NameUsageMatch m = NameUsageMatch.builder().diagnostics(NameUsageMatch.Diagnostics.builder().build()).build();
    m.setUsage(NameUsageMatch.RankedName.builder()
        .key(usageKey)
        .name(scientificName)
        .canonicalName(canonicalName)
        .rank(rank).build());
    m.setAcceptedUsage(NameUsageMatch.RankedName.builder().key(acceptedUsageKey).build());
    m.getDiagnostics().setStatus(status);
    m.getDiagnostics().setConfidence(confidence);
    m.getDiagnostics().setNote(note);
    m.getDiagnostics().setMatchType(matchType);
    m.getDiagnostics().setAlternatives(alternatives);
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
