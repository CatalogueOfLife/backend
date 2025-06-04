package life.catalogue.matching.controller;

import life.catalogue.matching.model.*;
import life.catalogue.matching.service.MatchingService;
import org.gbif.nameparser.api.Rank;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.time.StopWatch;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
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
import lombok.extern.slf4j.Slf4j;

import static life.catalogue.matching.controller.ApiLogger.logRequest;

/**
 * The MatchController provides a RESTful API for fuzzy matching of scientific names against a checklist.
 */
@Slf4j
@RestController
public class MatchController implements ErrorController {

  public static final String V2_SPECIES_MATCH = "v2/species/match";
  private final MatchingService matchingService;
  private final ErrorAttributes errorAttributes;
  private static final String ERROR_PATH = "/error";

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
      "Returns JSON metadata about the index, the dataset, the software versioning. Includes:" +
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
  @Tag(name = "Metadata", description = "Metadata about the matching service, including details on the indexes and software")
  @GetMapping(
    value = {"v2/species/match/metadata"},
    produces = "application/json")
  public Optional<APIMetadata> metadata(){
    return matchingService.getAPIMetadata(false);
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
  @Tag(name = "Searching names", description = "Matching services for scientific names and taxon identifiers")
  @Parameters(
      value = {
        @Parameter(
          name = "usageKey",
          description = "The usage key to look up. When provided, all other fields are ignored."),
        @Parameter(name = "taxonID",
          description = "The taxonID to match. Matches to a taxonID will take precedence over " +
            "scientificName values supplied. A comparison of the matched scientific and taxonID is performed to" +
            "check for inconsistencies." +
            "See Darwin core term " +
            "<a href='https://dwc.tdwg.org/terms/#dwc:taxonID'>taxonID</a> for more details.",
          in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(name = "taxonConceptID",
          description = "The taxonConceptID to match. " +
            "Matches to a taxonConceptID will take precedence over " +
            "scientificName values supplied. A comparison of the matched scientific and taxonConceptID is performed to " +
            "check for inconsistencies." +
            "See Darwin core term " +
            "<a href='https://dwc.tdwg.org/terms/#dwc:taxonConceptID'>taxonConceptID</a> for more details.",
          in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(name = "scientificNameID",
          description = "The scientificNameID to match. " +
            "Matches to a scientificNameID will take precedence over " +
            "scientificName values supplied. A comparison of the matched scientific and scientificNameID is performed to " +
            "check for inconsistencies." +
            "See Darwin core term " +
            "<a href='https://dwc.tdwg.org/terms/#dwc:scientificNameID'>scientificNameID</a> for more details",
          in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(name = "scientificName", description =
          "The scientific name to fuzzy match against. May include the authorship and year. " +
            "See Darwin core term " +
            "<a href='https://dwc.tdwg.org/terms/#dwc:scientificName'>scientificName</a> for more details"),
        @Parameter(name = "scientificNameAuthorship", description =
          "The scientific name authorship to  match against.  " +
            "See Darwin core term " +
            "<a href='https://dwc.tdwg.org/terms/#dwc:scientificNameAuthorship'>scientificNameAuthorship</a> for more details"),
        @Parameter(name = "classification", hidden = true),
        @Parameter(name = "taxonRank", description = "Filters by taxonomic rank. " +
          "See Darwin core term " +
          "<a href='https://dwc.tdwg.org/terms/#dwc:taxonRank'>taxonRank</a> for more details",
          schema = @Schema(implementation = Rank.class)),
        @Parameter(name = "verbatimTaxonRank", description = "Filters by taxonomic rank. " +
          "See Darwin core term " +
          "<a href='https://dwc.tdwg.org/terms/#dwc:verbatimTaxonRank'>verbatimTaxonRank</a> for more details",
          schema = @Schema(implementation = String.class)),
        @Parameter(name = "kingdom",
          description = "Kingdom to match. " +
            "See Darwin core term " +
            "<a href='https://dwc.tdwg.org/terms/#dwc:kingdom'>kingdom</a> for more details",
          in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(name = "phylum",
          description = "Phylum to match. " +
            "See Darwin core term " +
            "<a href='https://dwc.tdwg.org/terms/#dwc:phylum'>phylum</a> for more details",
          in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(name = "order",
          description = "Order to match. " +
            "See Darwin core term " +
            "<a href='https://dwc.tdwg.org/terms/#dwc:order'>order</a> for more details",
          in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(name = "class",
          description = "Class to match. " +
            "See Darwin core term " +
            "<a href='https://dwc.tdwg.org/terms/#dwc:class'>class</a> for more details",
          in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(name = "family",
          description = "Family to match. " +
            "See Darwin core term " +
            "<a href='https://dwc.tdwg.org/terms/#dwc:family'>family</a> for more details",
          in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(name = "genus",
          description = "Genus to match. " +
            "See Darwin core term " +
            "<a href='https://dwc.tdwg.org/terms/#dwc:genus'>genus</a> for more details",
          in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(name = "subgenus",
          description = "Subgenus to match. " +
            "See Darwin core term " +
            "<a href='https://dwc.tdwg.org/terms/#dwc:subgenus'>subgenus</a> for more details",
          in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(name = "species",
          description = "Species to match. " +
            "See Darwin core term " +
            "<a href='https://dwc.tdwg.org/terms/#dwc:species'>species</a> for more details",
          in = ParameterIn.QUERY, schema = @Schema(implementation = String.class)),
        @Parameter(
            name = "genericName",
            description =
                "Generic part of the name to match when given as atomised parts instead of the full name parameter." +
                  "See Darwin core term <a href='https://dwc.tdwg.org/terms/#dwc:genericName'>genericName</a> for more details",
          schema = @Schema(implementation = String.class)),
        @Parameter(name = "specificEpithet",
          description = "Specific epithet to match. " +
            "See Darwin core term " +
            "<a href='https://dwc.tdwg.org/terms/#dwc:specificEpithet'>specificEpithet</a> for more details",
          schema = @Schema(implementation = String.class)),
        @Parameter(name = "infraspecificEpithet",
          description = "Infraspecific epithet to match. " +
            "See Darwin core term " +
            "<a href='https://dwc.tdwg.org/terms/#dwc:infraspecificEpithet'>infraspecificEpithet</a> for more details",
          schema = @Schema(implementation = String.class)),
        @Parameter(
          name = "exclude",
          description = "An array of usage keys to exclude from the match.",
          schema = @Schema(implementation = String[].class)
        ),
        @Parameter(
            name = "strict",
            description =
                "If set to true, fuzzy matches only the given name, but never a taxon in the upper classification.",
          schema = @Schema(implementation = Boolean.class)
        ),
        @Parameter(
            name = "verbose",
            description =
                "If set to true, it shows alternative matches which were considered but then rejected.",
            schema = @Schema(implementation = Boolean.class))
      })
  @ApiResponse(responseCode = "200", description = "Name usage suggestions found")
  @GetMapping(
      value = {V2_SPECIES_MATCH},
      produces = "application/json")
  public NameUsageMatch match(
      @RequestParam(value = "usageKey", required = false) String usageKey,
      @RequestParam(value = "taxonID", required = false) String taxonID,
      @RequestParam(value = "taxonConceptID", required = false) String taxonConceptID,
      @RequestParam(value = "scientificNameID", required = false) String scientificNameID,
      @RequestParam(value = "scientificName", required = false) String scientificName,
      @RequestParam(value = "scientificNameAuthorship", required = false) String authorship,
      @RequestParam(value = "taxonRank", required = false) String taxonRank,
      @RequestParam(value = "verbatimTaxonRank", required = false) String verbatimTaxonRank,
      @RequestParam(value = "genericName", required = false) String genericName,
      @RequestParam(value = "specificEpithet", required = false) String specificEpithet,
      @RequestParam(value = "infraspecificEpithet", required = false) String infraspecificEpithet,
      @ParameterObject ClassificationQuery classification,
      @RequestParam(value = "exclude", required = false) Set<String> exclude,
      @RequestParam(value = "strict", required = false) Boolean strict,
      @RequestParam(value = "verbose", required = false) Boolean verbose,
      HttpServletRequest response) {

    StopWatch watch = new StopWatch();
    watch.start();
    // ugly, but jackson/spring isn't working with @JsonProperty
    classification.setClazz(response.getParameter("class"));
    NameUsageQuery query = NameUsageQuery.create(
        usageKey,
        taxonID,
        taxonConceptID,
        scientificNameID,
        scientificName,
        authorship,
        genericName,
        specificEpithet,
        infraspecificEpithet,
        taxonRank,
        verbatimTaxonRank,
        classification,
        exclude,
        strict,
        verbose);
      NameUsageMatch nameUsageMatch = matchingService.match(query);
      watch.stop();
      logRequest(MatchController.log, V2_SPECIES_MATCH, query, watch);
      nameUsageMatch.getDiagnostics().setTimeTaken(watch.getTime(TimeUnit.MILLISECONDS));
      return nameUsageMatch;
  }

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

}
