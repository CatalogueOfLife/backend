package life.catalogue.doi.datacite.model;

import life.catalogue.api.jackson.ApiModule;

import life.catalogue.api.model.EnumValue;

import org.junit.Test;

import com.google.common.reflect.ClassPath;

import static org.junit.Assert.assertEquals;

public class EnumValueTest {

  @Test
  public void customValueSerde() throws Exception {
    // serialize using the enum value
    for (ClassPath.ClassInfo info : ClassPath.from(getClass().getClassLoader()).getTopLevelClasses(NameType.class.getPackage().getName())) {
      var cl = info.load();
      if (cl.isEnum() && EnumValue.class.isAssignableFrom(cl)) {
        for (Object en : ((Class<Enum>)cl).getEnumConstants()) {
          EnumValue ev = (EnumValue) en;
          System.out.println(cl.getSimpleName() + ": " + ev.value());
          assertEquals("\"" + ev.value() + "\"", ApiModule.MAPPER.writeValueAsString(ev));
        }
      }
    }
  }
}