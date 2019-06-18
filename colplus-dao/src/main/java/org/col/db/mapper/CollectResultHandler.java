package org.col.db.mapper;

import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;

/**
 * Mybatis result handler that collects all results.
 */
public class CollectResultHandler<T> implements ResultHandler<T> {

  private final List<T> results = new ArrayList<T>();
  
  @Override
  public void handleResult(ResultContext<? extends T> ctx) {
    results.add(ctx.getResultObject());
  }
  
  public List<T> getResults() {
    return results;
  }
}
