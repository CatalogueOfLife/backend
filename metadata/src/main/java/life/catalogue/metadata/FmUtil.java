package life.catalogue.metadata;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import freemarker.template.*;

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

  public static String render(Object data, String templateName) throws IOException, TemplateException {
    Writer out = new StringWriter();
    Template temp = FmUtil.FMK.getTemplate(templateName);
    temp.process(data, out);
    return out.toString();
  }

}
