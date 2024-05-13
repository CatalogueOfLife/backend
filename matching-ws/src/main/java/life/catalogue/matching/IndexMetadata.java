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
  String createdDate;
  Long sizeInMB = 0L;
  Long taxaCount = 0L;
  Map<String, Long> taxaCounts = new HashMap<>();
}
