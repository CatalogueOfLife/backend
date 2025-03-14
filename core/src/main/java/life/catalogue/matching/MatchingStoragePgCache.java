package life.catalogue.matching;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.SimpleNameWithNidx;

import java.util.List;

public class MatchingStorageDisk implements MatchingStorage<SimpleNameWithNidx> {
  @Override
  public List<SimpleNameWithNidx> get(int canonNidx) {
    return List.of();
  }

  @Override
  public void put(int canonNidx, List<SimpleNameWithNidx> usages) {

  }

  @Override
  public List<SimpleNameWithNidx> getClassification(String usage) {
    return List.of();
  }

  @Override
  public SimpleNameWithNidx convert(NameUsageBase nu, int canonNidx) {
    return null;
  }

  @Override
  public void clear(int canonNidx) {

  }

}
