package life.catalogue.matching;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IDController {

  @Autowired
  MatchingService matchingService;

  @ApiResponse(responseCode = "200", description = "Name usage suggestions found")
  @Tag(name = "ID lookup")
  @GetMapping(
    value = {"v2/id/{datasetId}/{identifier}"},
    produces = "application/json")
  public Object matchV2(
    @PathVariable(value = "datasetId", required = false) String datasetId,
    @PathVariable(value = "identifier", required = false) String identifier) {
    return matchingService.matchID(datasetId, identifier);
  }

  @ApiResponse(responseCode = "200", description = "Name usage suggestions found")
  @Tag(name = "ID lookup")
  @GetMapping(
    value = {"v2/id/{identifier}"},
    produces = "application/json")
  public Object matchV2(
    @PathVariable(value = "identifier", required = false) String identifier) {
    return matchingService.matchID(identifier);
  }
}
