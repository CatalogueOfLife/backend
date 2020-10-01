package life.catalogue.exporter;

import freemarker.template.*;
import life.catalogue.common.text.StringUtils;

import java.sql.Date;
import java.time.LocalDate;

public class LocalDateObjectWrapper extends DefaultObjectWrapper {

  public LocalDateObjectWrapper(Version incompatibleImprovements) {
    super(incompatibleImprovements);
  }

  public class CamelCaseEnum implements TemplateScalarModel {
    final Enum value;

    public CamelCaseEnum(Enum value) {
      this.value = value;
    }

    @Override
    public String getAsString() {

      return value == null ? null : StringUtils.camelCase(value);
    }
  }

  @Override
  public TemplateModel wrap(final Object obj) throws TemplateModelException {
    if (obj == null) { return super.wrap(obj); }
    if (obj instanceof LocalDate) {
      return new SimpleDate(Date.valueOf((LocalDate) obj));
    }
    if (obj.getClass().isEnum()) {
      return new CamelCaseEnum((Enum) obj);
    }
    return super.wrap(obj);
  }
}
