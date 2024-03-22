package life.catalogue.dw.jersey.filter;

import life.catalogue.common.ws.MoreMediaTypes;

import java.net.URI;
import java.util.Map;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.Provider;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Map.entry;
/**
 * Filter that adds an Accept header based on a file suffix found in the URL path.
 * See SuffixAcceptRequestFilter#SUFFICES for supported mime types.
 *
 * It also changes the default Accept header to json for request without anything given
 * to avoid returning xml for datasets or textree for taxon resources.
 */
@Provider
@PreMatching
public class SuffixAcceptRequestFilter implements ContainerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(SuffixAcceptRequestFilter.class);
  private final static Map<String, String> SUFFICES = Map.ofEntries(
    entry("xml", MediaType.APPLICATION_XML),
    entry("json", MediaType.APPLICATION_JSON),
    entry("yaml", MoreMediaTypes.TEXT_YAML),
    entry("txt", MediaType.TEXT_PLAIN),
    entry("html", MediaType.TEXT_HTML),
    entry("tsv", MoreMediaTypes.TEXT_TSV),
    entry("csv", MoreMediaTypes.TEXT_CSV),
    entry("zip", MoreMediaTypes.APP_ZIP),
    entry("png", MoreMediaTypes.IMG_PNG),
    entry("bib", MoreMediaTypes.APP_BIBTEX),
    entry("csljs", MoreMediaTypes.APP_JSON_CSL),
    entry("coldp", MoreMediaTypes.APP_JSON_COLDP)
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
    } else {
      // json default?
      if (!req.getHeaders().containsKey(HttpHeaders.ACCEPT) || req.getHeaders().getFirst(HttpHeaders.ACCEPT).equals("*/*")) {
        req.getHeaders().putSingle(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON+",*/*");
      }
    }
  }

}
