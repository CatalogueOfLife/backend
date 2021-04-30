package life.catalogue.exporter;

import freemarker.ext.beans.BeanModel;
import freemarker.ext.beans.BeansWrapper;
import freemarker.template.*;
import life.catalogue.api.vocab.License;
import life.catalogue.common.text.StringUtils;

import java.sql.Date;
import java.time.LocalDate;

public class LocalDateObjectWrapper extends DefaultObjectWrapper {

  public LocalDateObjectWrapper(Version incompatibleImprovements) {
    super(incompatibleImprovements);
  }

  public class CamelCaseEnum extends BeanModel implements TemplateScalarModel {
    final Enum<?> value;

    public CamelCaseEnum(Enum<?> value, BeansWrapper wrapper) {
      super(value, wrapper);
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
    if (obj.getClass().equals(License.class)) {
      return new BeanModel(obj, this);
    }
    if (obj.getClass().isEnum()) {
      return new CamelCaseEnum((Enum) obj, this);
    }
    return super.wrap(obj);
  }
}
