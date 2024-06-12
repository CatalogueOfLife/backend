package life.catalogue.matching;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Dataset {
  private Integer key;
  private String gbifKey;
  private String title;
  private String alias;
  String prefix;
  List<String> prefixMapping = List.of();
  Long taxonCount = 0L;
  Long matchesToMainIndex = 0L;
}
