package life.catalogue.matching.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import life.catalogue.matching.model.ExternalID;
import life.catalogue.matching.service.MatchingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ID lookup services, largely for debug purposes to check identifiers
 * have been indexed.
 */
@RestController
@Slf4j
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
    StopWatch watch = new StopWatch();
    watch.start();
    List<ExternalID> ids = matchingService.matchID(datasetId, identifier);
    watch.stop();
    log.info("v2/id/datasetId: {} {}, time: {}ms", datasetId, identifier, watch.getTime(TimeUnit.MILLISECONDS));
    return ids;
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
    StopWatch watch = new StopWatch();
    watch.start();
    List<ExternalID> ids = matchingService.matchID(identifier);
    watch.stop();
    log.info("v2/id: {}, time: {}ms", identifier, watch.getTime(TimeUnit.MILLISECONDS));
    return ids;
  }

  @Operation(
    operationId = "joinLookup",
    summary = "ID join lookup service",
    description =
      "A lookup service, largely intended for debug purposes, to enable " +
        "verification that an external ID has been indexed and to check if " +
        "the ID has been associated with a taxon in the main index. ")
  @ApiResponse(responseCode = "200", description = "Name usage suggestions found")
  @Tag(name = "ID lookup services")
  @GetMapping(
    value = {"v2/joins/{identifier}"},
    produces = "application/json")
  public List<ExternalID> joins(
    @PathVariable(value = "identifier", required = false) String identifier) {
    StopWatch watch = new StopWatch();
    watch.start();
    List<ExternalID> ids = matchingService.lookupJoins(identifier);
    watch.stop();
    log.info("v2/id: {}, time: {}ms", identifier, watch.getTime(TimeUnit.MILLISECONDS));
    return ids;
  }
}
