package life.catalogue.resources;

import life.catalogue.dw.jersey.MoreHttpHeaders;
import life.catalogue.dw.jersey.filter.CacheControlResponseFilter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;

public class ResourceUtils {

  /**
   * By default the CacheControlResponseFilter will make all calls to release and external datasets cacheable.
   * Only project resources are not cached.
   * This can be overriden by setting the CacheControlResponseFilter.DONT_CACHE_PROPERTY property to any non null value
   * which this methods sets.
   */
  public static void dontCache(ContainerRequestContext ctx){
    ctx.setProperty(CacheControlResponseFilter.DONT_CACHE, true);
  }

  public static String filenameFromHeaders(HttpHeaders h) {
    if (h != null && h.getRequestHeaders() != null) {
      return h.getRequestHeaders().getFirst(MoreHttpHeaders.FILENAME);
    }
    return null;
  }

  /**
   * Content-Disposition: attachment; filename="filename.jpg"
   */
  public static String fileAttachment(String filename) {
    return "attachment; filename=\"" + filename + "\"";
  }


}
