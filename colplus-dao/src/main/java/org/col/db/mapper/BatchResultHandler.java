package org.col.db.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;

public class BatchResultHandler<T> implements ResultHandler<T>, AutoCloseable {

  private final Consumer<List<T>> batchConsumer;
  private final int batchSize;
  private final List<T> batch;

  public BatchResultHandler(Consumer<List<T>> batchConsumer, int batchSize) {
    Preconditions.checkArgument(batchSize > 0);
    this.batchConsumer = batchConsumer;
    this.batchSize = batchSize;
    batch = new ArrayList<>(batchSize);
  }

  @Override
  public void handleResult(ResultContext<? extends T> ctx) {
    batch.add(ctx.getResultObject());
    if (batch.size() >= batchSize) {
      submit();
    }
  }

  private void submit() {
    batchConsumer.accept(batch);
    batch.clear();
  }

  @Override
  public void close() throws Exception {
    submit();
  }
}
