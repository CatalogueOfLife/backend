package life.catalogue.dw.jersey.exception;

import freemarker.template.TemplateException;
import io.dropwizard.jersey.errors.ErrorMessage;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.dw.jersey.filter.DatasetKeyRewriteFilter;
import life.catalogue.portal.PortalPageRenderer;
import life.catalogue.resources.PortalResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * Converts a {@link NotFoundException} into a 404.
 * This exception mapper has to be registered manually!
 */
public class NotFoundExceptionMapper extends JsonExceptionMapperBase<NotFoundException> {
  private static final Logger LOG = LoggerFactory.getLogger(NotFoundExceptionMapper.class);

  private PortalPageRenderer renderer;

  @Context
  private ResourceInfo resourceInfo;

  public NotFoundExceptionMapper() {
    super(Response.Status.NOT_FOUND, true, false, null);
  }

  /**
   * This is required to activate the html 404 page rendering.
   * Otherwise only json 404s are returned.
   */
  public void setRenderer(PortalPageRenderer renderer) {
    this.renderer = renderer;
  }

  @Override
  public Response toResponse(NotFoundException ex) {
    try {
      if (renderer != null && resourceInfo.getResourceClass().equals(PortalResource.class)) {
        return Response
          .status(Response.Status.NOT_FOUND)
          .type(MediaType.TEXT_HTML)
          .entity(renderer.render404())
          .build();
      }
    } catch (TemplateException | IOException | RuntimeException e) {
      LOG.error("Failed to render 404 page", e);
    }
    return super.toResponse(ex);
  }
}
