package life.catalogue.api.search;

import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;

import java.util.List;
import java.util.function.Supplier;

public class VernacularSearchResponse extends ResultPage<VernacularNameUsage> {

  public VernacularSearchResponse() {
  }

  public VernacularSearchResponse(Page page, int total, List<VernacularNameUsage> result) {
    super(page, total, result);
  }

  public VernacularSearchResponse(Page page, List<VernacularNameUsage> result, Supplier<Integer> count) {
    super(page, result, count);
  }

}
