package life.catalogue.release;

import freemarker.template.*;

import java.sql.Date;
import java.time.LocalDate;

public class LocalDateObjectWrapper extends DefaultObjectWrapper {

  public LocalDateObjectWrapper(Version incompatibleImprovements) {
    super(incompatibleImprovements);
  }

  @Override
  public TemplateModel wrap(final Object obj) throws TemplateModelException {
    if (obj == null) { return super.wrap(obj); }
    if (obj instanceof LocalDate) {
      return new SimpleDate(Date.valueOf((LocalDate) obj));
    }
    return super.wrap(obj);
  }
}
