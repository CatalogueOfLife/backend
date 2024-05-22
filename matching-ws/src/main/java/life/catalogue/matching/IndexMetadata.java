package life.catalogue.matching;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Metadata about an index.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Data
@Schema(description = "Metadata about the index and software used to access the index through webservices", title = "IndexMetadata", type = "object")
public class IndexMetadata {
  @Schema(description = "The dataset key")
  String datasetKey;
  @Schema(description = "The dataset description")
  String datasetTitle;
  @Schema(description = "When the index was created")
  String created;
  @Schema(description = "The size of the index in MB")
  Long sizeInMB = 0L;
  @Schema(description = "The number of name usages in the index")
  Long taxonCount = 0L;
  @Schema(description = "Counts of taxa by rank")
  Map<String, Long> taxaByRankCount = new HashMap<>();
  @Schema(description = "Git build information")
  Map<String, Object> buildInfo = new HashMap<>();
}
