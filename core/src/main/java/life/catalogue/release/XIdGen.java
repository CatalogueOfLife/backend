package life.catalogue.release;

import life.catalogue.assembly.TreeMergeHandler;
import life.catalogue.common.id.IdConverter;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class XIdGen implements Supplier<String> {
  private final AtomicInteger id = new AtomicInteger(1);
  private final IdConverter converter;

  /**
   * Uses tilde as id prefix which is URI safe and not present in ShortUUIDs nor LATIN29 which can be found in project data.
   */
  public XIdGen() {
    this(TreeMergeHandler.ID_PREFIX);
  }

  public XIdGen(char prefix) {
    converter = new IdConverter(IdConverter.URISAFE64, prefix);
  }

  @Override
  public String get() {
    return converter.encode(id.incrementAndGet());
  }
}
