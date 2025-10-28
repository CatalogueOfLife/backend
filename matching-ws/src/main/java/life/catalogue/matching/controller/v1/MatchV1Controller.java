package life.catalogue.matching.controller.v1;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import life.catalogue.matching.controller.MatchController;
import life.catalogue.matching.model.*;
import life.catalogue.matching.model.v1.NameUsageMatchFlatV1;
import life.catalogue.matching.model.v1.NameUsageMatchV1;
import life.catalogue.matching.service.MatchingService;
import life.catalogue.matching.util.IUCNUtils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static life.catalogue.matching.controller.ApiLogger.logRequest;
import static life.catalogue.matching.util.CleanupUtils.first;
import static life.catalogue.matching.util.CleanupUtils.removeNulls;

/**
 * Legacy matching controller for the v1 species match API.
 * <p>
 * This controller is deprecated and should not be used for new implementations.
 * Use {@link MatchController} instead.
 */
@Slf4j
@RestController
@Deprecated
public class MatchV1Controller {

  public static final String V1_SPECIES_IUCN_RED_LIST_CATEGORY = "v1/species/iucnRedListCategory";
  public static final String V1_SPECIES_MATCH2 = "v1/species/match2";
  public static final String V1_SPECIES_MATCH = "v1/species/match";

  private final MatchingService matchingService;

  @Autowired
  public MatchV1Controller( MatchingService matchingService) {
    this.matchingService = matchingService;
  }

  @Deprecated
  @Operation(
    operationId = "matchNames",
    summary = "Legacy fuzzy name match service (Version 1 - flat format)",
    description = "Version 1 - Warning: this method will be removed and users are advised to migrate to the " +
      "version 2 API v2/species/match.\n\n" +
      "Fuzzy matches scientific names against the GBIF Backbone Taxonomy with the optional " +
      "classification provided. If a classification is provided and strict is not set to true, the default matching " +
      "will also try to match against these if no direct match is found for the name parameter alone.\n\n" +
      "Additionally, a lookup may be performed by providing the usageKey which will short-circuit the name-based matching " +
      "and ONLY use the given key, either finding the concept or returning no match.",
    extensions = @Extension(name = "Order", properties = @ExtensionProperty(name = "Order", value = "0130"))
  )
  @Tag(name = "Searching names", description = "Matching services for scientific names and taxon identifiers")
  @Parameters(
    value = {
      @Parameter(
        name = "name",
        description = "The scientific name to fuzzy match against. May include the authorship and year"
      ),
      @Parameter(name = "taxonID", hidden = true),
      @Parameter(name = "taxonConceptID", hidden = true),
      @Parameter(name = "scientificNameID", hidden = true),
      @Parameter(name = "scientificName", hidden = true),
      @Parameter(
        name = "authorship",
        description = "The scientific name authorship to fuzzy match against."
      ),
      @Parameter(name = "scientificNameAuthorship", hidden = true),
      @Parameter(
        name = "rank",
        description = "Filters by taxonomic rank as given in our https://api.gbif.org/v1/enumeration/basic/Rank[Rank enum].",
        schema = @Schema(implementation = org.gbif.api.vocabulary.Rank.class)
      ),
      @Parameter(name = "taxonRank", hidden = true),
      @Parameter(
        name = "kingdom",
        description = "Kingdom to match.",
        in = ParameterIn.QUERY,
        schema = @Schema(implementation = String.class)
      ),
      @Parameter(
        name = "phylum",
        description = "Phylum to match.",
        in = ParameterIn.QUERY,
        schema = @Schema(implementation = String.class)
      ),
      @Parameter(
        name = "order",
        description = "Order to match.",
        in = ParameterIn.QUERY,
        schema = @Schema(implementation = String.class)
      ),
      @Parameter(
        name = "class",
        description = "Class to match.",
        in = ParameterIn.QUERY,
        schema = @Schema(implementation = String.class)
      ),
      @Parameter(
        name = "family",
        description = "Family to match.",
        in = ParameterIn.QUERY,
        schema = @Schema(implementation = String.class)
      ),
      @Parameter(
        name = "genus",
        description = "Genus to match.",
        in = ParameterIn.QUERY,
        schema = @Schema(implementation = String.class)
      ),
      @Parameter(
        name = "genericName",
        description = "Generic part of the name to match when given as atomised parts instead of the full name parameter."
      ),
      @Parameter(
        name = "specificEpithet",
        description = "Specific epithet to match."
      ),
      @Parameter(
        name = "infraspecificEpithet",
        description = "Infraspecific epithet to match."
      ),
      @Parameter(name = "classification", hidden = true),
      @Parameter(
        name = "strict",
        description = "If true it fuzzy matches only the given name, but never a taxon in the upper classification."
      ),
      @Parameter(
        name = "verbose",
        description = "If true it shows alternative matches which were considered but then rejected."
      ),
      @Parameter(
        name = "usageKey",
        description = "The usage key to look up. When provided, all other fields are ignored."
      )
    }
  )
  @ApiResponse(responseCode = "200", description = "Name usage suggestions found")
  @GetMapping(
    value = {V1_SPECIES_MATCH},
    produces = "application/json")
  public Object matchFlatV1(
    @RequestParam(value = "usageKey", required = false) String usageKey,
    @RequestParam(value = "taxonID", required = false) String taxonID,
    @RequestParam(value = "taxonConceptID", required = false) String taxonConceptID,
    @RequestParam(value = "scientificNameID", required = false) String scientificNameID,
    @RequestParam(value = "name", required = false) String scientificName2,
    @RequestParam(value = "scientificName", required = false) String scientificName,
    @RequestParam(value = "authorship", required = false) String authorship2,
    @RequestParam(value = "scientificNameAuthorship", required = false) String authorship,
    @RequestParam(value = "rank", required = false) String rank2,
    @RequestParam(value = "taxonRank", required = false) String rank,
    @RequestParam(value = "genericName", required = false) String genericName,
    @RequestParam(value = "specificEpithet", required = false) String specificEpithet,
    @RequestParam(value = "infraspecificEpithet", required = false) String infraspecificEpithet,
    ClassificationQuery classification,
    @RequestParam(value = "exclude", required = false) Set<Integer> exclude,
    @RequestParam(value = "strict", required = false) Boolean strict,
    @RequestParam(value = "verbose", required = false) Boolean verbose,
    HttpServletRequest response) {

    try {
      StopWatch watch = new StopWatch();
      watch.start();

      classification.setClazz(response.getParameter("class"));
      NameUsageQuery query = NameUsageQuery.create(
        usageKey,
        taxonID,
        taxonConceptID,
        scientificNameID,
        first(removeNulls(scientificName), removeNulls(scientificName2)),
        first(removeNulls(authorship), removeNulls(authorship2)),
        genericName,
        specificEpithet,
        infraspecificEpithet,
        rank,
        rank2,
        classification,
        exclude != null ? exclude.stream().map(Object::toString).collect(Collectors.toSet()) : Set.of(),
        strict,
        verbose);

      Optional<NameUsageMatchFlatV1> optionalNameUsageMatchV1 = NameUsageMatchFlatV1.createFrom(
        matchingService.match(query));

      watch.stop();
      logRequest(log, V1_SPECIES_MATCH, query,  watch);

      if (optionalNameUsageMatchV1.isPresent()) {
        return optionalNameUsageMatchV1.get();
      } else {
        return Map.of("message", "Unable to support API v1 for this checklist. Please use v2 instead.");
      }
    } catch (Exception e){
      log.error(e.getMessage(), e);
      return null;
    }
  }

  @Deprecated
  @Operation(
    operationId = "matchNames",
    summary = "Legacy fuzzy name match service (Version 1 - nested format)",
    description = "Fuzzy matches scientific names against the GBIF Backbone Taxonomy with the optional " +
      "classification provided. If a classification is provided and strict is not set to true, the default matching " +
      "will also try to match against these if no direct match is found for the name parameter alone.\n\n" +
      "Additionally, a lookup may be performed by providing the usageKey which will short-circuit the name-based matching " +
      "and ONLY use the given key, either finding the concept or returning no match.",
    extensions = @Extension(name = "Order", properties = @ExtensionProperty(name = "Order", value = "0130"))
  )
  @Tag(name = "Searching names", description = "Matching services for scientific names and taxon identifiers")
  @Parameters(
    value = {
      @Parameter(
        name = "name",
        description = "The scientific name to fuzzy match against. May include the authorship and year",
        schema = @Schema(implementation = String.class)
      ),
      @Parameter(name = "scientificName", hidden = true),
      @Parameter(
        name = "authorship",
        description = "The scientific name authorship to fuzzy match against.",
        schema = @Schema(implementation = String.class)
      ),
      @Parameter(name = "scientificNameAuthorship", hidden = true),
      @Parameter(
        name = "rank",
        description = "Filters by taxonomic rank as given in our https://api.gbif.org/v1/enumeration/basic/Rank[Rank enum].",
        schema = @Schema(implementation = org.gbif.api.vocabulary.Rank.class)
      ),
      @Parameter(name = "taxonRank", hidden = true),
      @Parameter(
        name = "kingdom",
        description = "Kingdom to match.",
        in = ParameterIn.QUERY,
        schema = @Schema(implementation = String.class)
      ),
      @Parameter(
        name = "phylum",
        description = "Phylum to match.",
        in = ParameterIn.QUERY,
        schema = @Schema(implementation = String.class)
      ),
      @Parameter(
        name = "order",
        description = "Order to match.",
        in = ParameterIn.QUERY,
        schema = @Schema(implementation = String.class)
      ),
      @Parameter(
        name = "class",
        description = "Class to match.",
        in = ParameterIn.QUERY,
        schema = @Schema(implementation = String.class)
      ),
      @Parameter(
        name = "family",
        description = "Family to match.",
        in = ParameterIn.QUERY,
        schema = @Schema(implementation = String.class)
      ),
      @Parameter(
        name = "genus",
        description = "Genus to match.",
        in = ParameterIn.QUERY,
        schema = @Schema(implementation = String.class)
      ),
      @Parameter(
        name = "genericName",
        description = "Generic part of the name to match when given as atomised parts instead of the full name parameter.",
        schema = @Schema(implementation = String.class)
      ),
      @Parameter(
        name = "specificEpithet",
        description = "Specific epithet to match.",
        schema = @Schema(implementation = String.class)
      ),
      @Parameter(
        name = "infraspecificEpithet",
        description = "Infraspecific epithet to match.",
        schema = @Schema(implementation = String.class)
      ),
      @Parameter(name = "classification", hidden = true),
      @Parameter(
        name = "strict",
        description = "If true it fuzzy matches only the given name, but never a taxon in the upper classification.",
        schema = @Schema(implementation = Boolean.class)
      ),
      @Parameter(
        name = "verbose",
        description = "If true it shows alternative matches which were considered but then rejected.",
        schema = @Schema(implementation = Boolean.class)
      ),
      @Parameter(
        name = "usageKey",
        description = "The usage key to look up. When provided, all other fields are ignored.",
        schema = @Schema(implementation = String.class)
      ),
      @Parameter(
        name = "exclude",
        description = "An array of usage keys to exclude from the match.",
        schema = @Schema(implementation = Integer[].class)
      )
    }
  )
  @ApiResponse(responseCode = "200", description = "Name usage suggestions found")
  @GetMapping(
    value = {V1_SPECIES_MATCH2},
    produces = "application/json")
  @Hidden
  public Object matchV1(
    @RequestParam(value = "usageKey", required = false) String usageKey,
    @RequestParam(value = "taxonID", required = false) String taxonID,
    @RequestParam(value = "taxonConceptID", required = false) String taxonConceptID,
    @RequestParam(value = "scientificNameID", required = false) String scientificNameID,
    @RequestParam(value = "name", required = false) String scientificName2,
    @RequestParam(value = "scientificName", required = false) String scientificName,
    @RequestParam(value = "authorship", required = false) String authorship2,
    @RequestParam(value = "scientificNameAuthorship", required = false) String authorship,
    @RequestParam(value = "rank", required = false) String rank2,
    @RequestParam(value = "taxonRank", required = false) String rank,
    @RequestParam(value = "genericName", required = false) String genericName,
    @RequestParam(value = "specificEpithet", required = false) String specificEpithet,
    @RequestParam(value = "infraspecificEpithet", required = false) String infraspecificEpithet,
    ClassificationQuery classification,
    @RequestParam(value = "exclude", required = false) Set<Integer> exclude,
    @RequestParam(value = "strict", required = false) Boolean strict,
    @RequestParam(value = "verbose", required = false) Boolean verbose,
    HttpServletRequest response) {

    StopWatch watch = new StopWatch();
    watch.start();

    classification.setClazz(response.getParameter("class"));
    NameUsageQuery query = NameUsageQuery.create(
      usageKey,
      taxonID,
      taxonConceptID,
      scientificNameID,
      first(removeNulls(scientificName), removeNulls(scientificName2)),
      first(removeNulls(authorship), removeNulls(authorship2)),
      genericName,
      specificEpithet,
      infraspecificEpithet,
      rank,
      rank2,

      classification,
      exclude != null ? exclude.stream().map(Object::toString).collect(Collectors.toSet()) : Set.of(),
      strict,
      verbose);
    Optional<NameUsageMatchV1> optionalNameUsageMatchV1 = NameUsageMatchV1.createFrom(
      matchingService.match(query));

    watch.stop();
    logRequest(log, V1_SPECIES_MATCH2, query, watch);

    if (optionalNameUsageMatchV1.isPresent()) {
      optionalNameUsageMatchV1.get().getDiagnostics().setTimeTaken(watch.getTime(TimeUnit.MILLISECONDS));
      return optionalNameUsageMatchV1.get();
    } else {
      return Map.of("message", "Unable to support API v1 for this checklist. Please use v2 instead.");
    }
  }

  //  http://checklistbank-matching-ws-gbif:8080/v1/species?datasetKey=2d59e5db-57ad-41ff-97d6-11f5fb264527&sourceId=urn%3Alsid%3Amarinespecies.org%3At
  // v1/species/2494686/iucnRedListCategory
  @Hidden
  @GetMapping(
    value = {"v1/species"},
    produces = "application/json")
  public ExternalIDV1Response matchBySourceID(
    @RequestParam(value = "datasetKey", required = true) String datasetKey,
    @RequestParam(value = "sourceId", required = true) String sourceId
  ) {
    StopWatch watch = new StopWatch();
    watch.start();
    List<ExternalID> externalIDs = matchingService.matchID(datasetKey, sourceId);

    if (externalIDs == null || externalIDs.isEmpty()) {
      return new ExternalIDV1Response(new ArrayList<>());
    }
    List<ExternalIDV1> results = externalIDs.stream().map(externalID ->
      ExternalIDV1.builder()
        .key(Integer.parseInt(externalID.getMainIndexID()))
        .nubKey(Integer.parseInt(externalID.getMainIndexID()))
        .scientificName(externalID.getScientificName()).build()).collect(Collectors.toList());
    watch.stop();
    logRequest(log, "v1/species", sourceId, watch);
    return new ExternalIDV1Response(results);
  }

  // v1/species/2494686/iucnRedListCategory
  @Hidden
  @GetMapping(
    value = {"v1/species/{usageKey}/iucnRedListCategory"},
    produces = "application/json")
  public Map<String, Object> iucnRedListV1(@PathVariable(value = "usageKey", required = false) String usageKey) {
    StopWatch watch = new StopWatch();
    watch.start();
    // match by usageKey
    NameUsageMatch match = matchingService.match(new NameUsageQuery(usageKey, null, null, null, null, null,
      null, null, null, null, null, Set.of(),
      true,
      false));

    List<NameUsageMatch.Status> statusList = match.getAdditionalStatus();
    if (statusList == null || statusList.isEmpty() || statusList.get(0).getStatus() == null) {
      return Map.of();
    }
    NameUsageMatch.Status status = statusList.get(0);
    String formatted = IUCNUtils.formatIucn(status.getStatus());
    if (formatted == null || formatted.isEmpty()) {
      return Map.of();
    }

    String scientificName = match.getAcceptedUsage() != null ? match.getAcceptedUsage().getCanonicalName() : match.getUsage().getCanonicalName();

    try {
      IUCNUtils.IUCN iucn = IUCNUtils.IUCN.valueOf(formatted); // throws IllegalArgumentException if not found
      watch.stop();
      logRequest(log, V1_SPECIES_IUCN_RED_LIST_CATEGORY, usageKey, watch);
      return Map.of(
        "category", iucn.name(),
        "usageKey", Integer.parseInt(usageKey),
        "scientificName", scientificName,
        "taxonomicStatus", NameUsageMatchV1.TaxonomicStatusV1.convert(
          match.getUsage().getStatus()),
        "iucnTaxonID", status.getSourceId(),
        "code", iucn.getCode()
      );
    } catch (IllegalArgumentException e) {
      MatchV1Controller.log.error("IUCN category not found: {}", formatted, e);
      return Map.of(
        "category", status.getStatus(),
        "usageKey", Integer.parseInt(usageKey),
        "scientificName", scientificName,
        "taxonomicStatus", match.getUsage().getStatus(),
        "iucnTaxonID", status.getSourceId(),
        "code", status.getStatus()
      );
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ExternalIDV1 {
    int key;
    Integer nubKey;
    String scientificName;
  }

  /**
   * Contains a partial NameUsageSearchResponse mapping, with the fields necessary to lookup concepts within a checklist and locate
   * their equivalent backbone taxon id.
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ExternalIDV1Response {
    List<ExternalIDV1> results;
  }
}
