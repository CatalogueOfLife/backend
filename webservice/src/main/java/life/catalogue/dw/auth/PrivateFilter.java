package life.catalogue.dw.auth;

import life.catalogue.api.model.User;
import life.catalogue.db.mapper.DatasetMapper;

import java.io.IOException;
import java.util.function.IntPredicate;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanMaps;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
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
  // is dataset private cache?
  private final Int2BooleanMap cache = Int2BooleanMaps.synchronize(new Int2BooleanOpenHashMap());

  @Override
  public void filter(ContainerRequestContext req) throws IOException {
    Integer datasetKey = AuthFilter.requestedDataset(req.getUriInfo());
    req.setProperty(DATASET_KEY_PROPERTY, datasetKey);
    if (datasetKey != null) {
      // is this a private dataset?
      boolean priv = cache.computeIfAbsent(datasetKey, (IntPredicate) value -> {
        try (SqlSession session = factory.openSession()) {
          DatasetMapper dm = session.getMapper(DatasetMapper.class);
          return dm.isPrivate(datasetKey);
        }
      });

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