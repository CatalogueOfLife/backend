package life.catalogue.dw.auth;

import life.catalogue.api.model.User;
import life.catalogue.db.mapper.DatasetMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import com.google.common.annotations.VisibleForTesting;

import jakarta.annotation.Priority;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.SecurityContext;

/**
 * Avoids unprivileged access to private datasets. A user has to be at least a reviewer with read access to pass this filter.
 * See https://github.com/CatalogueOfLife/backend/issues/659
 * <p>
 * To prevent performance penalties a low memory footprint cache is used.
 * Make sure that any dataset changing actions keep this filter up to date using {@link PrivateFilter#updateCache(int, boolean)}.
 * <p>
 * A SqlSessionFactory MUST be set before the service is used.
 */
@Priority(Priorities.AUTHORIZATION)
public class PrivateFilter implements ContainerRequestFilter {
  public static String DATASET_KEY_PROPERTY = "request.datasetKey";

  private SqlSessionFactory factory;
  // is dataset private cache? Read by every request thread and updated by dataset change events.
  // The lookup is atomic per key, so a change event arriving while a request reads the db cannot be overwritten
  // by the stale value that request read - a plain map used to cache a dataset that just went private as public.
  private final Map<Integer, Boolean> cache = new ConcurrentHashMap<>(10_000);

  @Override
  public void filter(ContainerRequestContext req) throws IOException {
    Integer datasetKey = AuthFilter.requestedDataset(req.getUriInfo());
    req.setProperty(DATASET_KEY_PROPERTY, datasetKey);
    if (datasetKey != null) {
      // is this a private dataset?
      boolean priv = isPrivate(datasetKey);
      if (priv) {
        // check if user has permissions to at least read
        SecurityContext secCtxt = req.getSecurityContext();
        if (secCtxt != null && secCtxt.getUserPrincipal() != null && secCtxt.getUserPrincipal() instanceof User) {
          User user = (User) secCtxt.getUserPrincipal();
          if (!AuthFilter.hasReadAccess(user, datasetKey)) {
            throw new ForbiddenException("Dataset " + datasetKey + " is private");
          }
        } else {
          throw new NotAuthorizedException("Dataset " + datasetKey + " is private");
        }
      }
    }
  }

  @VisibleForTesting
  boolean isPrivate(int datasetKey) {
    return cache.computeIfAbsent(datasetKey, this::isPrivateInDB);
  }

  private boolean isPrivateInDB(int datasetKey) {
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      return dm.isPrivate(datasetKey);
    }
  }

  /**
   * Wires up the mybatis sqlfactory to be used.
   */
  public void setSqlSessionFactory(SqlSessionFactory factory) {
    this.factory = factory;
  }

  public void updateCache(int datasetKey, boolean privat) {
    cache.put(datasetKey, privat);
  }

  public void flushCache() {
    cache.clear();
  }
}