package life.catalogue.exporter;

import freemarker.template.*;
import life.catalogue.common.io.UTF8IoUtils;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

public class FmUtil {
  public static final Version FREEMARKER_VERSION = Configuration.VERSION_2_3_28;
  public static final Configuration FMK = new Configuration(FREEMARKER_VERSION);
  static {
    FMK.setClassForTemplateLoading(FmUtil.class, "/freemarker-templates");
    // see https://freemarker.apache.org/docs/pgui_quickstart_createconfiguration.html
    FMK.setDefaultEncoding("UTF-8");
    FMK.setDateFormat("yyyy-MM-dd");
    FMK.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    FMK.setLogTemplateExceptions(false);
    FMK.setWrapUncheckedExceptions(true);
    // allow the use of java8 dates
    FMK.setObjectWrapper(new LocalDateObjectWrapper(FREEMARKER_VERSION));
  }

  public static String render(Object data, String template) throws IOException, TemplateException {
    Writer out = new StringWriter();
    Template temp = FmUtil.FMK.getTemplate(template);
    temp.process(data, out);
    return out.toString();
  }
}
