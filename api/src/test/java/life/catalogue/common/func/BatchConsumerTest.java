package life.catalogue.common.func;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class BatchConsumerTest {

  /**
   * Collects every batch it is given, optionally failing on all of them.
   */
  static class Batches implements Consumer<List<Integer>> {
    final List<List<Integer>> seen = new ArrayList<>();
    final boolean fail;

    Batches(boolean fail) {
      this.fail = fail;
    }

    @Override
    public void accept(List<Integer> batch) {
      seen.add(List.copyOf(batch));
      if (fail) {
        throw new IllegalStateException("boom");
      }
    }
  }

  @Test
  public void submitFullBatchesAndTheRestOnClose() {
    var batches = new Batches(false);
    try (var bc = new BatchConsumer<>(batches, 2)) {
      bc.accept(1);
      bc.accept(2);
      bc.accept(3);
    }
    assertEquals(List.of(List.of(1, 2), List.of(3)), batches.seen);
  }

  /**
   * A batch that failed to submit must not be handed to the consumer a second time by close().
   * Retrying it there throws again while the first exception is already unwinding a try-with-resources,
   * which masks the real error - with an identical instance it even becomes
   * "IllegalArgumentException: Self-suppression not permitted", see the ITIS import of 2026-08-31.
   */
  @Test
  public void failedBatchNotRetriedOnClose() {
    var batches = new Batches(true);
    var bc = new BatchConsumer<>(batches, 2);
    bc.accept(1);
    assertThrows(IllegalStateException.class, () -> bc.accept(2));
    bc.close();
    assertEquals(List.of(List.of(1, 2)), batches.seen);
  }
}
