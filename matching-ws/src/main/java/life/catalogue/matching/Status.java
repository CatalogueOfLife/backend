package life.catalogue.matching;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Status {
  private String datasetKey;
  private String datasetTitle;
  private String category;
}