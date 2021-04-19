package life.catalogue.resources;

import freemarker.template.Template;
import freemarker.template.TemplateException;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Dataset;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.exporter.FmUtil;

import javax.ws.rs.PathParam;
import javax.ws.rs.RedirectionException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;

public class ResourceUtils {

  public static void redirect(URI location) throws RedirectionException {
    throw new RedirectionException(Response.Status.FOUND, location);
  }

  public static Response streamFreemarker(Object data, String template, MediaType mediaType) {
    StreamingOutput stream = os -> {
      try {
        Writer out = UTF8IoUtils.writerFromStream(os);
        Template temp = FmUtil.FMK.getTemplate(template);
        temp.process(data, out);
        os.flush();
      } catch (TemplateException e) {
        throw new IOException(e);
      }
    };

    return Response.ok(stream)
      .type(mediaType)
      .build();
  }
}
