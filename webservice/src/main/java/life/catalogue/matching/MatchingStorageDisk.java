package life.catalogue.matching;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.SimpleNameWithNidx;

import java.util.List;

public class MatchingStorageDisk implements MatchingStorage<SimpleNameWithNidx> {
  @Override
  public List<SimpleNameWithNidx> get(DSID<Integer> canonNidx) {
    return List.of();
  }

  @Override
  public void put(DSID<Integer> canonNidx, List<SimpleNameWithNidx> usages) {

  }

  @Override
  public List<SimpleNameWithNidx> getClassification(DSID<String> usage) {
    return List.of();
  }

  @Override
  public SimpleNameWithNidx convert(NameUsageBase nu, DSID<Integer> canonNidx) {
    return null;
  }

  @Override
  public void clear(DSID<Integer> canonNidx) {

  }

  @Override
  public void clear(int datasetKey) {

  }

  @Override
  public void clear() {

  }

  @Override
  public void start() throws Exception {

  }

  @Override
  public void stop() throws Exception {

  }

  @Override
  public boolean hasStarted() {
    return false;
  }
}
