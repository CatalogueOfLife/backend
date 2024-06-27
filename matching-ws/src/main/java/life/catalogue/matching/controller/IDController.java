package life.catalogue.matching.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import life.catalogue.matching.model.ExternalID;
import life.catalogue.matching.service.MatchingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ID lookup services, largely for debug purposes to check identifiers
 * have been indexed.
 */
@RestController
public class IDController {

  final MatchingService matchingService;

  public IDController(MatchingService matchingService) {
    this.matchingService = matchingService;
  }

  @Operation(
    operationId = "idLookupByDataset",
    summary = "ID lookup service by dataset",
    description =
      "A lookup service, largely intended for debug purposes, to enable " +
        "verification that an external ID for a specific dataset  (e.g. WoRMS)  has been indexed and to check if " +
        "the ID has been associated with a taxon in the main index. ")
  @ApiResponse(responseCode = "200", description = "Name usage suggestions found")
  @Tag(name = "ID lookup services")
  @GetMapping(
    value = {"v2/id/{datasetId}/{identifier}"},
    produces = "application/json")
  public List<ExternalID> matchV2(
    @PathVariable(value = "datasetId", required = false) String datasetId,
    @PathVariable(value = "identifier", required = false) String identifier) {
    return matchingService.matchID(datasetId, identifier);
  }

  @Operation(
    operationId = "idLookup",
    summary = "ID lookup service",
    description =
      "A lookup service, largely intended for debug purposes, to enable " +
        "verification that an external ID has been indexed and to check if " +
        "the ID has been associated with a taxon in the main index. ")
  @ApiResponse(responseCode = "200", description = "Name usage suggestions found")
  @Tag(name = "ID lookup services")
  @GetMapping(
    value = {"v2/id/{identifier}"},
    produces = "application/json")
  public List<ExternalID> matchV2(
    @PathVariable(value = "identifier", required = false) String identifier) {
    return matchingService.matchID(identifier);
  }
}
