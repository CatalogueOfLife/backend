package life.catalogue.matching.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A dataset representing a source of taxonomic data.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Dataset {
  Integer key;
  String gbifKey;
  String title;
  String alias;
  String prefix;
  List<String> prefixMapping = List.of();
  Long taxonCount = 0L;
  Long matchesToMainIndex = 0L;
  Boolean removePrefixForMatching = false;
}
