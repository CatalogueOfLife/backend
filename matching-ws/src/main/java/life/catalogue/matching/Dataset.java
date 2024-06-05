package life.catalogue.matching;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
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
