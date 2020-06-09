package life.catalogue.dw.jersey.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.lang.annotation.Annotation;

/**
 * Legcay API Filter to apply Accept header by looking at a "response" parameter
 */
@Provider
@ApplyFormatFilter
public class LegacyFormatFilter implements ContainerResponseFilter {

  private static final Logger LOG = LoggerFactory.getLogger(LegacyFormatFilter.class);
  private static final String PARAM  = "format";

  @Override
  public void filter(ContainerRequestContext req, ContainerResponseContext resp) throws IOException {
    MultivaluedMap<String, String> params = req.getUriInfo().getQueryParameters();
    // default to XML
    MediaType type = MediaType.APPLICATION_XML_TYPE;
    if (params.containsKey(PARAM) && params.getFirst(PARAM).endsWith("json")) {
      type = MediaType.APPLICATION_JSON_TYPE;
    }
    resp.setEntity(resp.getEntity(), new Annotation[0], type);
  }
}
