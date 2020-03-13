package life.catalogue.release;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

abstract class TableCopyHandlerBase<T> implements Consumer<T>, AutoCloseable {
  
  private static final Logger LOG = LoggerFactory.getLogger(TableCopyHandlerBase.class);
  private static final int BATCHSIZE = 10000;

  private final SqlSessionFactory factory;
  protected final Consumer<T> updater;
  protected final SqlSession session;
  private final String entityName;
  private int counter;
  
  public TableCopyHandlerBase(SqlSessionFactory factory, String entityName, Consumer<T> updater) {
    this.factory = factory;
    this.updater = updater;
    this.entityName = entityName;
    // we open up a separate batch session that we can write to so we do not disturb the open main cursor for processing with this handler
    this.session = factory.openSession(ExecutorType.BATCH, false);
  }
  
  @Override
  public void close() {
    session.commit();
    session.close();
  }
  
  public int getCounter() {
    return counter;
  }
  
  abstract void create(T obj);
  
  @Override
  public void accept(T obj) {
    updater.accept(obj);
    create(obj);
    // commit in batches
    if (counter++ % BATCHSIZE == 0) {
      session.commit();
      LOG.debug("Inserted {} {}", counter, entityName);
    }
  }
}
