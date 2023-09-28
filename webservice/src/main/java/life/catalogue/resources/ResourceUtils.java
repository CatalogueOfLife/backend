package life.catalogue.resources;

import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dw.jersey.MoreHttpHeaders;
import life.catalogue.metadata.FmUtil;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;

import javax.ws.rs.RedirectionException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import freemarker.template.Template;
import freemarker.template.TemplateException;

public class ResourceUtils {

  public static RedirectionException redirect(URI location) {
    return new RedirectionException(Response.Status.FOUND, location);
  }

  public static String filenameFromHeaders(HttpHeaders h) {
    if (h != null && h.getRequestHeaders() != null) {
      return h.getRequestHeaders().getFirst(MoreHttpHeaders.FILENAME);
    }
    return null;
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

  /**
   * Content-Disposition: attachment; filename="filename.jpg"
   */
  public static String fileAttachment(String filename) {
    return "attachment; filename=\"" + filename + "\"";
  }


}
