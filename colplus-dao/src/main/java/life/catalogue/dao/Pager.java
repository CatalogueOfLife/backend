package life.catalogue.dao;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import com.google.common.base.Preconditions;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.db.DatasetPageable;
import life.catalogue.db.GlobalPageable;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DecisionMapper;
import life.catalogue.db.mapper.EstimateMapper;
import life.catalogue.db.mapper.SectorMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iterator over entities from paging responses that optionally filters out deleted entities.
 */
public class Pager<T> implements Iterable<T> {
  private static final Logger LOG = LoggerFactory.getLogger(Pager.class);
  private static final int DEFAULT_PAGE_SIZE = 100;
  private final int pageSize;
  private final Function<Page, List<T>> nextPageFunc;
  
  public Pager(int pageSize, Function<Page, List<T>> nextPageFunc) {
    Preconditions.checkArgument(pageSize > 0, "pageSize must at least be 1");
    this.pageSize = pageSize;
    this.nextPageFunc = nextPageFunc;
  }
  
  public static Iterable<EditorialDecision> decisions(final int catalogueKey, final SqlSessionFactory factory) {
    return pager(DEFAULT_PAGE_SIZE, catalogueKey, DecisionMapper.class, factory);
  }
  
  public static Iterable<SpeciesEstimate> estimates(final int catalogueKey, final SqlSessionFactory factory) {
    return pager(DEFAULT_PAGE_SIZE, catalogueKey, EstimateMapper.class, factory);
  }

  public static Iterable<Sector> sectors(final int catalogueKey, final SqlSessionFactory factory) {
    return pager(DEFAULT_PAGE_SIZE, catalogueKey, SectorMapper.class, factory);
  }
  
  public static Iterable<Dataset> datasets(final SqlSessionFactory factory) {
    return pager(DEFAULT_PAGE_SIZE, DatasetMapper.class, factory);
  }
  
  private static <M> Iterable<M> pager(int pageSize, Class<? extends GlobalPageable<M>> mapperClass, final SqlSessionFactory factory) {
    PageablePager<M> pp = new PageablePager(mapperClass, factory);
    return new Pager<M>(pageSize, pp::nextPage);
  }

  private static <M> Iterable<M> pager(int pageSize, int datasetKey, Class<? extends DatasetPageable<M>> mapperClass, final SqlSessionFactory factory) {
    DatasetPageablePager<M> pp = new DatasetPageablePager(datasetKey, mapperClass, factory);
    return new Pager<M>(pageSize, pp::nextPage);
  }

  public static Iterable<Dataset> datasets(final SqlSessionFactory factory, int contributesTo) {
    final DatasetSearchRequest req;
    req = new DatasetSearchRequest();
    req.setContributesTo(contributesTo);
    return new Pager<>(DEFAULT_PAGE_SIZE, new Function<Page, List<Dataset>>() {
      @Override
      public List<Dataset> apply(Page page) {
        try (SqlSession session = factory.openSession()) {
          return session.getMapper(DatasetMapper.class).search(req, page);
        }
      }
    });
  }
  
  static class PageablePager<T> {
    private final Class<GlobalPageable<T>> mapperClass;
    private final SqlSessionFactory factory;
  
    PageablePager(Class<GlobalPageable<T>> mapperClass, SqlSessionFactory factory) {
      this.mapperClass = mapperClass;
      this.factory = factory;
    }
    private List<T> nextPage(Page page) {
      try (SqlSession session = factory.openSession()) {
        return session.getMapper(mapperClass).list(page);
      }
    }
  }
  
  static class DatasetPageablePager<T> {
    private final Class<DatasetPageable<T>> mapperClass;
    private final SqlSessionFactory factory;
    private final int datasetKey;
  
    DatasetPageablePager(int datasetKey, Class<DatasetPageable<T>> mapperClass, SqlSessionFactory factory) {
      this.mapperClass = mapperClass;
      this.factory = factory;
      this.datasetKey = datasetKey;
    }
    private List<T> nextPage(Page page) {
      try (SqlSession session = factory.openSession()) {
        return session.getMapper(mapperClass).list(datasetKey, page);
      }
    }
  }
  
  class PageResultIterator implements Iterator<T> {
    private final Page page = new Page(0, pageSize);
    private boolean hasMore = true;
    private Iterator<T> iter;
    private T next;
    
    public PageResultIterator() {
      loadPage();
      next = nextObj();
    }
    
    @Override
    public boolean hasNext() {
      return next != null;
    }
    
    @Override
    public T next() {
      T entity = next;
      next = nextObj();
      return entity;
    }
    
    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
    
    private T nextObj() {
      while (true) {
        if (!iter.hasNext()) {
          if (hasMore) {
            loadPage();
          } else {
            // no more records to load, stop!
            return null;
          }
        }
        T obj = iter.next();
        return obj;
      }
    }
    
    private void loadPage() {
      LOG.debug("Loading page {}-{}", page.getOffset(), page.getOffset() + page.getLimit());
      List<T> resp = nextPageFunc.apply(page);
      iter = resp.iterator();
      hasMore = resp.size() == pageSize;
      page.next();
    }
  }
  
  @Override
  public Iterator<T> iterator() {
    return new PageResultIterator();
  }
  
}
