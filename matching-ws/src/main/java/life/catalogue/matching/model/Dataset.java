package life.catalogue.matching.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

import java.util.List;

/**
 * A dataset representing a source of taxonomic data.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Dataset {
  Integer key;
  String gbifKey;
  String title;
  String alias;
  String prefix;
  List<String> prefixMapping = List.of();
  Long taxonCount = 0L;
  Long matchesToMainIndex = 0L;
}
