package life.catalogue.dw.jersey.filter;

import life.catalogue.common.ws.MoreMediaTypes;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;

/**
 * Sets the charset encoding parameter to UTF8 for all text media types that do not have a charset specified.
 */
@Provider
public class CharsetResponseFilter implements ContainerResponseFilter {

  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) {
    MediaType type = response.getMediaType();
    if (type != null && MediaType.TEXT_PLAIN_TYPE.getType().equalsIgnoreCase(type.getType())) {
      String contentType = type.toString();
      if (!contentType.contains("charset")) {
        MoreMediaTypes.setUTF8ContentType(type, response.getHeaders());
      }
    }
  }
}
