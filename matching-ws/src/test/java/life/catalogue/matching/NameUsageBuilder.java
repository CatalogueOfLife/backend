package life.catalogue.matching;

import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.matching.model.NameUsageMatch;

import org.gbif.nameparser.api.Rank;

import java.util.List;

import lombok.Builder;

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
    m.setUsage(NameUsageMatch.Usage.builder()
        .key(usageKey)
        .name(scientificName)
        .canonicalName(canonicalName)
        .rank(rank).build());
    m.setAcceptedUsage(NameUsageMatch.Usage.builder().key(acceptedUsageKey).build());
    m.getUsage().setStatus(status);
    m.getDiagnostics().setConfidence(confidence);
    m.getDiagnostics().setNote(note);
    m.getDiagnostics().setMatchType(matchType);
    m.getDiagnostics().setAlternatives(alternatives);
    m.setNameFor(kingdom, Rank.KINGDOM);
    m.setNameFor(phylum, Rank.PHYLUM);
    m.setNameFor(clazz, Rank.CLASS);
    m.setNameFor(order, Rank.ORDER);
    m.setNameFor(family, Rank.FAMILY);
    m.setNameFor(genus, Rank.GENUS);
    m.setNameFor(subgenus, Rank.SUBGENUS);
    m.setNameFor(species, Rank.SPECIES);
    m.setKeyFor(kingdomKey, Rank.KINGDOM);
    m.setKeyFor(phylumKey, Rank.PHYLUM);
    m.setKeyFor(classKey, Rank.CLASS);
    m.setKeyFor(orderKey, Rank.ORDER);
    m.setKeyFor(familyKey, Rank.FAMILY);
    m.setKeyFor(genusKey, Rank.GENUS);
    m.setKeyFor(subgenusKey, Rank.SUBGENUS);
    m.setKeyFor(speciesKey, Rank.SPECIES);
    return m;
  }
}
