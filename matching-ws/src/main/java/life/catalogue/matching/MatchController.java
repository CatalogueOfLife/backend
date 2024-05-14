package life.catalogue.matching;

import static life.catalogue.matching.CleanupUtils.clean;
import static life.catalogue.matching.MatchingService.first;

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
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import life.catalogue.parser.RankParser;
import life.catalogue.parser.UnparsableException;
import org.apache.commons.lang3.StringUtils;
import org.gbif.nameparser.api.Rank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.WebRequest;

/**
 * The MatchController provides a RESTful API for fuzzy matching of scientific names against a checklist.
 */
@RestController
public class MatchController implements ErrorController {

  @Autowired MatchingService matchingService;

  private final ErrorAttributes errorAttributes;

  @Value("${v1.enabled:false}")
  protected boolean v1Enabled = false;

  private static final String PATH = "/error";

  @Hidden
  @RequestMapping(value = PATH, produces = "application/json")
  public Map<String, Object> error(WebRequest aRequest){
    Map<String, Object> body = getErrorAttributes(aRequest);
    String traceRequested = aRequest.getParameter("trace");
    if (StringUtils.isNotBlank(traceRequested) &&
      (traceRequested.equalsIgnoreCase("true") || traceRequested.equalsIgnoreCase("on"))) {
      String trace = (String) body.get("trace");
      if (trace != null){
        String[] lines = trace.split("\n\t");
        body.put("trace", lines);
      }
    } else {
      body.remove("trace");
    }
    return body;
  }

  private boolean getTraceParameter(HttpServletRequest request) {
    String parameter = request.getParameter("trace");
    if (parameter == null) {
      return false;
    }
    return !"false".equals(parameter.toLowerCase());
  }

  @Autowired
  public MatchController(ErrorAttributes errorAttributes) {
    Assert.notNull(errorAttributes, "ErrorAttributes must not be null");
    this.errorAttributes = errorAttributes;
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
  public IndexMetadata metadata(){

    return matchingService.getIndexMetadata();
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
            name = "name",
            description =
                "The scientific name to fuzzy match against. May include the authorship and year"),
        @Parameter(name = "scientificName", hidden = true),
        @Parameter(
            name = "authorship",
            description = "The scientific name authorship to fuzzy match against."),
        @Parameter(name = "scientificNameAuthorship", hidden = true),
        @Parameter(
            name = "rank",
            description = "Filters by taxonomic rank.",
            schema = @Schema(implementation = Rank.class)),
        @Parameter(name = "taxonRank", hidden = true),
        @Parameter(name = "kingdom", description = "Kingdom to match.", in = ParameterIn.QUERY),
        @Parameter(name = "phylum", description = "Phylum to match.", in = ParameterIn.QUERY),
        @Parameter(name = "order", description = "Order to match.", in = ParameterIn.QUERY),
        @Parameter(name = "class", description = "Class to match.", in = ParameterIn.QUERY),
        @Parameter(name = "family", description = "Family to match.", in = ParameterIn.QUERY),
        @Parameter(name = "genus", description = "Genus to match.", in = ParameterIn.QUERY),
        @Parameter(name = "subgenus", description = "Subgenus to match.", in = ParameterIn.QUERY),
        @Parameter(name = "species", description = "Species to match.", in = ParameterIn.QUERY),
        @Parameter(
            name = "genericName",
            description =
                "Generic part of the name to match when given as atomised parts instead of the full name parameter."),
        @Parameter(name = "specificEpithet", description = "Specific epithet to match."),
        @Parameter(name = "infraspecificEpithet", description = "Infraspecific epithet to match."),
        @Parameter(name = "classification", hidden = true),
        @Parameter(name = "arg10", hidden = true),
        @Parameter(
            name = "strict",
            description =
                "If true it fuzzy matches only the given name, but never a taxon in the upper classification."),
        @Parameter(
            name = "verbose",
            description =
                "If true it shows alternative matches which were considered but then rejected."),
        @Parameter(
            name = "usageKey",
            description = "The usage key to look up. When provided, all other fields are ignored.")
      })
  @ApiResponse(responseCode = "200", description = "Name usage suggestions found")
  @GetMapping(
      value = {"v2/species/match"},
      produces = "application/json")
  public NameUsageMatch matchV2(
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
      LinneanClassificationImpl classification,
      @RequestParam(value = "strict", required = false) Boolean strict,
      @RequestParam(value = "verbose", required = false) Boolean verbose,
      HttpServletRequest response) {
    // ugly, i know, but jackson/spring isn't working with @JsonProperty
    classification.setClazz(response.getParameter("class"));
    return matchingService.match(
        removeNulls(usageKey),
        first(removeNulls(scientificName), removeNulls(scientificName2)),
        first(removeNulls(authorship), removeNulls(authorship2)),
        removeNulls(genericName),
        removeNulls(specificEpithet),
        removeNulls(infraspecificEpithet),
        parseRank(first(removeNulls(rank), removeNulls(rank2))),
        clean(classification),
        Set.of(),
        bool(strict),
        bool(verbose));
  }

  @Operation(
    operationId = "matchNames",
    summary = "Legacy fuzzy name match service (Version 1)",
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
        in = ParameterIn.QUERY
      ),
      @Parameter(
        name = "phylum",
        description = "Phylum to match.",
        in = ParameterIn.QUERY
      ),
      @Parameter(
        name = "order",
        description = "Order to match.",
        in = ParameterIn.QUERY
      ),
      @Parameter(
        name = "class",
        description = "Class to match.",
        in = ParameterIn.QUERY
      ),
      @Parameter(
        name = "family",
        description = "Family to match.",
        in = ParameterIn.QUERY
      ),
      @Parameter(
        name = "genus",
        description = "Genus to match.",
        in = ParameterIn.QUERY
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
  public Object matchV1(
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
      LinneanClassificationImpl classification,
      @RequestParam(value = "strict", required = false) Boolean strict,
      @RequestParam(value = "verbose", required = false) Boolean verbose,
      HttpServletRequest response) {

    if (!v1Enabled) {
      return Map.of("message", "API v1 is disabled. Please use v2 instead.");
    }

    classification.setClazz(response.getParameter("class"));
    Optional<NameUsageMatchV1> optionalNameUsageMatchV1 = NameUsageMatchV1.createFrom(
        matchingService.match(
            removeNulls(usageKey),
            first(removeNulls(scientificName), removeNulls(scientificName2)),
            first(removeNulls(authorship), removeNulls(authorship2)),
            removeNulls(genericName),
            removeNulls(specificEpithet),
            removeNulls(infraspecificEpithet),
            parseRank(first(removeNulls(rank), removeNulls(rank2))),
            clean(classification),
            Set.of(),
            bool(strict),
            bool(verbose)));

    if (optionalNameUsageMatchV1.isPresent()) {
      return optionalNameUsageMatchV1.get();
    } else {
      return Map.of("message", "Unable to support API v1  for this checklist. Please use v2 instead.");
    }
  }

  public static String removeNulls(String value) {
    if (value != null) {
      value = StringUtils.trimToEmpty(value);
      if (value.equalsIgnoreCase("null")) {
        return null;
      }
      return value;
    }
    return null;
  }

  private Rank parseRank(String value) {
    try {
      if (!Objects.isNull(value) && !value.isEmpty()) {
        Optional<Rank> pr = RankParser.PARSER.parse(null, value);
        if (pr.isPresent()) {
          return Rank.valueOf(pr.get().name());
        }
      }
    } catch (UnparsableException e) {
      // throw new UnparsableException("Rank", value);
    }
    return null;
  }

  private boolean bool(Boolean bool) {
    return bool != null && bool;
  }
}
