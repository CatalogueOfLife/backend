package life.catalogue.matching.controller;

import static life.catalogue.matching.util.CleanupUtils.*;

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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import life.catalogue.matching.model.*;
import life.catalogue.matching.service.MatchingService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.gbif.nameparser.api.Rank;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import javax.servlet.http.HttpServletRequest;

/**
 * The MatchController provides a REST-ful API for fuzzy matching of scientific names against a checklist.
 */
@Slf4j
@RestController
public class MatchController implements ErrorController {

  private final MatchingService matchingService;
  private final ErrorAttributes errorAttributes;

  @Value("${v1.enabled:false}")
  protected boolean v1Enabled = false;

  private static final String ERROR_PATH = "/error";

  @Hidden
  @GetMapping(value = ERROR_PATH, produces = "application/json")
  public Map<String, Object> error(WebRequest request) {
    Map<String, Object> errorAttributes = getErrorAttributes(request);
    String traceRequested = request.getParameter("trace");
    if (isTraceRequested(traceRequested)) {
      Optional.ofNullable(errorAttributes.get("trace"))
        .map(Object::toString)
        .ifPresent(trace -> errorAttributes.put("trace", trace.split("\n\t")));
    } else {
      errorAttributes.remove("trace");
    }
    return errorAttributes;
  }

  private boolean isTraceRequested(String traceRequested) {
    return "true".equalsIgnoreCase(traceRequested) || "on".equalsIgnoreCase(traceRequested);
  }

  @Autowired
  public MatchController(ErrorAttributes errorAttributes, MatchingService matchingService) {
    Assert.notNull(errorAttributes, "ErrorAttributes must not be null");
    this.errorAttributes = errorAttributes;
    this.matchingService = matchingService;
  }

  private Map<String, Object> getErrorAttributes(WebRequest request) {
    ErrorAttributeOptions options = ErrorAttributeOptions.defaults().including(ErrorAttributeOptions.Include.STACK_TRACE);
    return errorAttributes.getErrorAttributes(request, options);
  }

  @Operation(
    operationId = "metadata",
    summary = "Retrieve metadata about the matching service",
    description =
      "Returns metadata about the index, such as the size, the dataset, the software versioning. Includes:" +
        " index size\n" +
        " counts by rank\n" +
        " git commit ID\n" +
        " git commit date\n" +
        " git commit user\n" +
        " git commit message\n" +
        " git branch\n" +
        " git tag\n" +
        " dataset ID\n" +
        " dataset title\n" +
        " dataset version\n" +
        " dataset release date",
    extensions =
    @Extension(
      name = "Order",
      properties = @ExtensionProperty(name = "Order", value = "0130")))
  @Tag(name = "Searching names")
  @GetMapping(
    value = {"v2/metadata"},
    produces = "application/json")
  public Optional<APIMetadata> metadata(){
    return matchingService.getAPIMetadata();
  }

  @Hidden
  @GetMapping(
    value = {"species/match", "match"},
    produces = "application/json")
  public NameUsageMatch matchOldPaths(
    @RequestParam(value = "usageKey", required = false) String usageKey,
    @RequestParam(value = "name", required = false) String scientificName2,
    @RequestParam(value = "scientificName", required = false) String scientificName,
    @RequestParam(value = "authorship", required = false) String authorship2,
    @RequestParam(value = "scientificNameAuthorship", required = false) String authorship,
    @RequestParam(value = "rank", required = false) String rank2,
    @RequestParam(value = "taxonRank", required = false) String rank,
    @RequestParam(value = "genericName", required = false) String genericName,
    @RequestParam(value = "specificEpithet", required = false) String specificEpithet,
    @RequestParam(value = "infraspecificEpithet", required = false) String infraspecificEpithet,
    Classification classification,
    @RequestParam(value = "exclude", required = false) Set<String> exclude,
    @RequestParam(value = "strict", required = false) Boolean strict,
    @RequestParam(value = "verbose", required = false) Boolean verbose,
    HttpServletRequest response) {
    return matchV2(
      usageKey,
      null,null,null,
      scientificName2, scientificName,
      authorship, authorship2,
      removeNulls(genericName),
      removeNulls(specificEpithet),
      removeNulls(infraspecificEpithet),
      rank,
      rank2,
      classification,
      exclude,
      bool(strict),
      bool(verbose),
      response);
  }

  @Hidden
  @ApiResponse(responseCode = "200", description = "Name usage suggestions found")
  @GetMapping(
    value = {"v2/species/match2"},
    produces = "application/json")
  public NameUsageMatch matchV2LegacyPath(
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
    Classification classification,
    @RequestParam(value = "exclude", required = false) Set<String> exclude,
    @RequestParam(value = "strict", required = false) Boolean strict,
    @RequestParam(value = "verbose", required = false) Boolean verbose,
    HttpServletRequest response) {
    return matchV2(
      usageKey,
      taxonID,
      taxonConceptID,
      scientificNameID,
      scientificName2,
      scientificName,
      authorship2,
      authorship,
      rank2,
      rank,
      genericName,
      specificEpithet,
      infraspecificEpithet,
      classification,
      exclude,
      strict,
      verbose,
      response);
  }

  @Operation(
      operationId = "matchNames",
      summary = "Fuzzy name match service (Version 2)",
      description =
          "Fuzzy matches scientific names against the Taxonomy with the optional "
              + "classification provided. If a classification is provided and strict is not set to true, the default matching "
              + "will also try to match against these if no direct match is found for the name parameter alone.\n\n"
              + "Additionally, a lookup may be performed by providing the usageKey which will short-circuit the name-based matching "
              + "and ONLY use the given key, either finding the concept or returning no match.",
      extensions =
          @Extension(
              name = "Order",
              properties = @ExtensionProperty(name = "Order", value = "0130")))
  @Tag(name = "Searching names")
  @Parameters(
      value = {
        @Parameter(
          name = "usageKey",
          description = "The usage key to look up. When provided, all other fields are ignored."),
        @Parameter(name = "taxonID", description = "The taxonID to match.", in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(name = "taxonConceptID", description = "The taxonConceptID to match.", in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(name = "scientificNameID", description = "The scientificNameID to match.", in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(
            name = "name",
            deprecated = true,
            description =
                "The scientific name to fuzzy match against. May include the authorship and year"),
        @Parameter(name = "scientificName", description =
          "The scientific name to fuzzy match against. May include the authorship and year"),
        @Parameter(
            name = "authorship",
            description = "The scientific name authorship to fuzzy match against."),
        @Parameter(name = "scientificNameAuthorship", hidden = true),
        @Parameter(name = "classification", hidden = true),
        @Parameter(
            name = "rank",
            description = "Filters by taxonomic rank.",
            deprecated = true,
            schema = @Schema(implementation = Rank.class)),
        @Parameter(name = "taxonRank", description = "Filters by taxonomic rank.",
          schema = @Schema(implementation = Rank.class)),
        @Parameter(name = "kingdom", description = "Kingdom to match.", in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(name = "phylum", description = "Phylum to match.", in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(name = "order", description = "Order to match.", in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(name = "class", description = "Class to match.", in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(name = "family", description = "Family to match.", in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(name = "genus", description = "Genus to match.", in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(name = "subgenus", description = "Subgenus to match.", in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(name = "species", description = "Species to match.", in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(
            name = "genericName",
            description =
                "Generic part of the name to match when given as atomised parts instead of the full name parameter."),
        @Parameter(name = "specificEpithet", description = "Specific epithet to match.", schema = @Schema(implementation = String.class)),
        @Parameter(name = "infraspecificEpithet", description = "Infraspecific epithet to match.", schema = @Schema(implementation = String.class)),
        @Parameter(
            name = "strict",
            description =
                "If true it fuzzy matches only the given name, but never a taxon in the upper classification."),
        @Parameter(
            name = "verbose",
            description =
                "If true it shows alternative matches which were considered but then rejected."),

      })
  @ApiResponse(responseCode = "200", description = "Name usage suggestions found")
  @GetMapping(
      value = {"v2/species/match"},
      produces = "application/json")
  public NameUsageMatch matchV2(
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
      @ParameterObject Classification classification,
      @RequestParam(value = "exclude", required = false) Set<String> exclude,
      @RequestParam(value = "strict", required = false) Boolean strict,
      @RequestParam(value = "verbose", required = false) Boolean verbose,
      HttpServletRequest response) {

    StopWatch watch = new StopWatch();
    watch.start();
    // ugly, but jackson/spring isn't working with @JsonProperty
    classification.setClazz(response.getParameter("class"));
    NameUsageMatch nameUsageMatch = matchingService.match(
        removeNulls(usageKey),
        removeNulls(taxonID),
        removeNulls(taxonConceptID),
        removeNulls(scientificNameID),
        first(removeNulls(scientificName), removeNulls(scientificName2)),
        first(removeNulls(authorship), removeNulls(authorship2)),
        removeNulls(genericName),
        removeNulls(specificEpithet),
        removeNulls(infraspecificEpithet),
        parseRank(first(removeNulls(rank), removeNulls(rank2))),
        clean(classification),
        exclude,
        bool(strict),
        bool(verbose));
    watch.stop();
    log("v2/species/match", scientificName, watch);
    nameUsageMatch.getDiagnostics().setTimeTaken(watch.getTime(TimeUnit.MILLISECONDS));
    return nameUsageMatch;
  }

  private static void log(String requestPath, String query, StopWatch watch) {
    log.info("[{}ms] {}: {}", String.format("%4d", watch.getTime(TimeUnit.MILLISECONDS)), requestPath, query);
  }

  @Operation(
    operationId = "matchNames",
    summary = "Legacy fuzzy name match service (Version 1 - flat format)",
    description = "Fuzzy matches scientific names against the GBIF Backbone Taxonomy with the optional " +
      "classification provided. If a classification is provided and strict is not set to true, the default matching " +
      "will also try to match against these if no direct match is found for the name parameter alone.\n\n" +
      "Additionally, a lookup may be performed by providing the usageKey which will short-circuit the name-based matching " +
      "and ONLY use the given key, either finding the concept or returning no match.",
    extensions = @Extension(name = "Order", properties = @ExtensionProperty(name = "Order", value = "0130"))
  )
  @Tag(name = "Searching names")
  @Parameters(
    value = {
      @Parameter(
        name = "name",
        description = "The scientific name to fuzzy match against. May include the authorship and year"
      ),
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
      value = {"v1/species/match"},
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
      Classification classification,
      @RequestParam(value = "exclude", required = false) Set<Integer> exclude,
      @RequestParam(value = "strict", required = false) Boolean strict,
      @RequestParam(value = "verbose", required = false) Boolean verbose,
      HttpServletRequest response) {

    try {

      if (!v1Enabled) {
        return Map.of("message", "API v1 is disabled. Please use v2 instead.");
      }

      StopWatch watch = new StopWatch();
      watch.start();

      classification.setClazz(response.getParameter("class"));
      String scientificNameToUse = first(removeNulls(scientificName), removeNulls(scientificName2));
      Optional<NameUsageMatchFlatV1> optionalNameUsageMatchV1 = NameUsageMatchFlatV1.createFrom(
        matchingService.match(
          removeNulls(usageKey),
          removeNulls(taxonID),
          removeNulls(taxonConceptID),
          removeNulls(scientificNameID),
          scientificNameToUse,
          first(removeNulls(authorship), removeNulls(authorship2)),
          removeNulls(genericName),
          removeNulls(specificEpithet),
          removeNulls(infraspecificEpithet),
          parseRank(first(removeNulls(rank), removeNulls(rank2))),
          clean(classification),
          exclude != null ? exclude.stream().map(Object::toString).collect(Collectors.toSet()) : Set.of(),
          bool(strict),
          bool(verbose)));

      watch.stop();
      log("v1/species/match", scientificNameToUse, watch);

      if (optionalNameUsageMatchV1.isPresent()) {
        return optionalNameUsageMatchV1.get();
      } else {
        return Map.of("message", "Unable to support API v1  for this checklist. Please use v2 instead.");
      }
    } catch (Exception e){
      log.error(e.getMessage(), e);
      return null;
    }
  }

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
  @Tag(name = "Searching names")
  @Parameters(
    value = {
      @Parameter(
        name = "name",
        description = "The scientific name to fuzzy match against. May include the authorship and year"
      ),
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
    value = {"v1/species/match2"},
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
    Classification classification,
    @RequestParam(value = "exclude", required = false) Set<Integer> exclude,
    @RequestParam(value = "strict", required = false) Boolean strict,
    @RequestParam(value = "verbose", required = false) Boolean verbose,
    HttpServletRequest response) {

    if (!v1Enabled) {
      return Map.of("message", "API v1 is disabled. Please use v2 instead.");
    }
    StopWatch watch = new StopWatch();
    watch.start();

    classification.setClazz(response.getParameter("class"));
    String scientificNameToUse = first(removeNulls(scientificName), removeNulls(scientificName2));
    Optional<NameUsageMatchV1> optionalNameUsageMatchV1 = NameUsageMatchV1.createFrom(
      matchingService.match(
        removeNulls(usageKey),
        removeNulls(taxonID),
        removeNulls(taxonConceptID),
        removeNulls(scientificNameID),
        scientificNameToUse,
        first(removeNulls(authorship), removeNulls(authorship2)),
        removeNulls(genericName),
        removeNulls(specificEpithet),
        removeNulls(infraspecificEpithet),
        parseRank(first(removeNulls(rank), removeNulls(rank2))),
        clean(classification),
        exclude != null ? exclude.stream().map(Object::toString).collect(Collectors.toSet()) : Set.of(),
        bool(strict),
        bool(verbose)));

    watch.stop();
    log("v1/species/match", scientificNameToUse, watch);

    if (optionalNameUsageMatchV1.isPresent()) {
      optionalNameUsageMatchV1.get().getDiagnostics().setTimeTaken(watch.getTime(TimeUnit.MILLISECONDS));
      return optionalNameUsageMatchV1.get();
    } else {
      return Map.of("message", "Unable to support API v1  for this checklist. Please use v2 instead.");
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
    log("v1/species", sourceId, watch);
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
    NameUsageMatch match = matchingService.match(usageKey, null, null, null, null, null,
      null, null, null, null, null, Set.of(),
      true,
      false);

    List<NameUsageMatch.Status> statusList = match.getAdditionalStatus();
    if (statusList == null || statusList.isEmpty() || statusList.get(0).getStatus() == null) {
      return Map.of();
    }
    NameUsageMatch.Status status = statusList.get(0);
    String formatted = formatIucn(status.getStatus());
    if (formatted == null || formatted.isEmpty()) {
      return Map.of();
    }

    String scientificName = match.getAcceptedUsage() != null ? match.getAcceptedUsage().getCanonicalName() : match.getUsage().getCanonicalName();

    try {
      IUCN iucn = IUCN.valueOf(formatted); // throws IllegalArgumentException if not found
      watch.stop();
      log("v1/species/iucnRedListCategory", usageKey, watch);
      return Map.of(
        "category", iucn.name(),
        "usageKey", Integer.parseInt(usageKey),
        "scientificName", scientificName,
        "taxonomicStatus", NameUsageMatchV1.TaxonomicStatusV1.convert(
          match.getDiagnostics().getStatus()),
        "iucnTaxonID", status.getSourceId(),
        "code", iucn.code
      );
    } catch (IllegalArgumentException e) {
      log.error("IUCN category not found: {}", formatted, e);
      return Map.of(
        "category", status.getStatus(),
        "usageKey", Integer.parseInt(usageKey),
        "scientificName", scientificName,
        "taxonomicStatus", match.getDiagnostics().getStatus(),
        "iucnTaxonID", status.getSourceId(),
        "code", status.getStatus()
      );
    }
  }

  String formatIucn(String original){
    if (original == null) {
      return null;
    }
    // Trim the string
    String trimmed = original.trim();
    // Convert to uppercase
    String uppercased = trimmed.toUpperCase();
    // Replace any whitespace with a single underscore
    return uppercased.replaceAll("\\s+", "_");
  }

   enum IUCN {
    EXTINCT("EX"),
    EXTINCT_IN_THE_WILD("EW"),
    CRITICALLY_ENDANGERED ("CR"),
    ENDANGERED ("EN"),
    VULNERABLE ("VU"),
    NEAR_THREATENED ("NT"),
    CONSERVATION_DEPENDENT ("CD"),
    LEAST_CONCERN ("LC"),
    DATA_DEFICIENT ("DD"),
    NOT_EVALUATED ("NE");

    private final String code;

    IUCN(String code) {
      this.code = code;
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
