package life.catalogue.release;

import life.catalogue.api.model.SimpleNameWithNidx;
import life.catalogue.common.id.ShortUUID;

public interface UsageIdGen {

  String issue(SimpleNameWithNidx usage);
  Integer nidx2canonical(Integer nidx);

  UsageIdGen RANDOM_SHORT_UUID = new UsageIdGen() {
    @Override
    public String issue(SimpleNameWithNidx usage) {
      return ShortUUID.ID_GEN.get();
    }

    @Override
    public Integer nidx2canonical(Integer nidx) {
      return null;
    }
  };

}
