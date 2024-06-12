package life.catalogue.matching;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Metadata about an index.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Data
@Schema(description = "Metadata about the index and software used to access the index through webservices",
  title = "APIMetadata", type = "object")
public class APIMetadata {
  @Schema(description = "When the index was created")
  String created;
  @Schema(description = "Git build information")
  Map<String, Object> buildInfo = new HashMap<>();
  IndexMetadata mainIndex;
  List<IndexMetadata> identifierIndexes = new ArrayList<>();
  List<IndexMetadata> ancillaryIndexes = new ArrayList<>();
}
