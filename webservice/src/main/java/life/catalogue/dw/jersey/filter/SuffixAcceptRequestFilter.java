package life.catalogue.dw.jersey.filter;

import life.catalogue.dw.jersey.MoreMediaTypes;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.Provider;
import java.net.URI;
import java.util.Map;

/**
 * Filter that adds an Accept header based on a file suffix found in the URL path.
 * See SuffixAcceptRequestFilter#SUFFICES for supported mime types.
 */
@Provider
@PreMatching
public class SuffixAcceptRequestFilter implements ContainerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(SuffixAcceptRequestFilter.class);
  private final static Map<String, String> SUFFICES = Map.of(
    "xml", MediaType.APPLICATION_XML,
    "json", MediaType.APPLICATION_JSON,
    "txt", MediaType.TEXT_PLAIN,
    "html", MediaType.TEXT_HTML,
    "tsv", MoreMediaTypes.TEXT_TSV,
    "csv", MoreMediaTypes.TEXT_CSV,
    "zip", MoreMediaTypes.APP_ZIP,
    "png", MoreMediaTypes.IMG_PNG
  );

  @Override
  public void filter(ContainerRequestContext req)  {
    final URI uri = req.getUriInfo().getRequestUri();
    final int index = FilenameUtils.indexOfExtension(uri.getPath());
    if (index > 1) {
      String suffix = uri.getPath().substring(index+1).trim().toLowerCase();
      if (SUFFICES.containsKey(suffix)) {
        String mimeType = SUFFICES.get(suffix);
        req.getHeaders().putSingle(HttpHeaders.ACCEPT, mimeType);
        // remove the suffix from the request
        String newPath = uri.getPath().substring(0, index);
        URI newURI = UriBuilder.fromUri(uri)
          .replacePath(newPath)
          .build();
        req.setRequestUri(newURI);
        LOG.debug("Accept: {}, change URI to {}", mimeType, newURI.toString());
      }
    }
  }

}
