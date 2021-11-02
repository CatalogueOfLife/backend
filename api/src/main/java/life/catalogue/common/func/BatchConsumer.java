package life.catalogue.common.func;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;

/**
 * Consumer that offers the accepted objects in batches. Make always sure to close the consumer to submit the last batch!
 */
public class BatchConsumer<T> implements Consumer<T>, AutoCloseable {
  
    private final Consumer<List<T>> batchConsumer;
    private final int batchSize;
    private final List<T> batch;
    
    public BatchConsumer(Consumer<List<T>> batchConsumer, int batchSize) {
      Preconditions.checkArgument(batchSize > 0);
      this.batchConsumer = batchConsumer;
      this.batchSize = batchSize;
      batch = new ArrayList<>(batchSize);
    }
  
    @Override
    public void accept(T obj) {
      batch.add(obj);
      if (batch.size() >= batchSize) {
        submit();
      }
    }
    
    private void submit() {
      batchConsumer.accept(batch);
      batch.clear();
    }
    
    @Override
    public void close() {
      if (batch.size() != 0) {
        submit();
      }
    }
  }
