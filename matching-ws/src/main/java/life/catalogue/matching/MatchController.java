package life.catalogue.matching;

import static life.catalogue.matching.MatchingService.first;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Objects;
import java.util.Optional;
import life.catalogue.parser.RankParser;
import life.catalogue.parser.UnparsableException;
import org.gbif.nameparser.api.Rank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MatchController {

  @GetMapping("/")
  public String index() {
    return "Lets get matching!";
  }

  @Autowired MatchingService matchingService;

  @Operation(
      operationId = "matchNames",
      summary = "Fuzzy name match service",
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
            description =
                "Filters by taxonomic rank as given in our https://api.gbif.org/v1/enumeration/basic/Rank[Rank enum].",
            schema = @Schema(implementation = Rank.class)),
        @Parameter(name = "taxonRank", hidden = true),
        @Parameter(name = "kingdom", description = "Kingdom to match.", in = ParameterIn.QUERY),
        @Parameter(name = "phylum", description = "Phylum to match.", in = ParameterIn.QUERY),
        @Parameter(name = "order", description = "Order to match.", in = ParameterIn.QUERY),
        @Parameter(name = "class", description = "Class to match.", in = ParameterIn.QUERY),
        @Parameter(name = "family", description = "Family to match.", in = ParameterIn.QUERY),
        @Parameter(name = "genus", description = "Genus to match.", in = ParameterIn.QUERY),
        @Parameter(
            name = "genericName",
            description =
                "Generic part of the name to match when given as atomised parts instead of the full name parameter."),
        @Parameter(name = "specificEpithet", description = "Specific epithet to match."),
        @Parameter(name = "infraspecificEpithet", description = "Infraspecific epithet to match."),
        @Parameter(name = "classification", hidden = true),
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
  @GetMapping(value = "match", produces = "application/json")
  public NameUsageMatch match(
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
      @RequestParam(value = "verbose", required = false) Boolean verbose) {

    return matchingService.match(
        usageKey,
        first(scientificName, scientificName2),
        first(authorship, authorship2),
        genericName,
        specificEpithet,
        infraspecificEpithet,
        parseRank(first(rank, rank2)),
        classification,
        null,
        bool(strict),
        bool(verbose));
  }

  private Rank parseRank(String value) {
    try {
      if (!Objects.isNull(value) && !value.isEmpty()) {
        Optional<org.gbif.nameparser.api.Rank> pr = RankParser.PARSER.parse(null, value);
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
