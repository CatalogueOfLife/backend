package life.catalogue.matching;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Metadata about an index.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Data
public class IndexMetadata {
  Long datasetKey;
  String datasetTitle;
  String created;
  Long sizeInMB = 0L;
  Long taxonCount = 0L;
  Map<String, Long> taxaByRankCount = new HashMap<>();
}
