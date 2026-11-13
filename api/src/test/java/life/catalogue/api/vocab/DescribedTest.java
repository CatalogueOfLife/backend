package life.catalogue.api.vocab;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import com.google.common.reflect.ClassPath;

import static org.junit.Assert.assertTrue;

public class DescribedTest {

  private static List<Class<?>> vocabEnums() throws Exception {
    List<Class<?>> enums = new ArrayList<>();
    for (ClassPath.ClassInfo info : ClassPath.from(DescribedTest.class.getClassLoader()).getTopLevelClassesRecursive(Described.class.getPackageName())) {
      Class<?> cl = info.load();
      if (cl.isEnum()) {
        enums.add(cl);
      }
    }
    return enums;
  }

  @Test
  public void everyValueDescribed() throws Exception {
    int counter = 0;
    List<String> missing = new ArrayList<>();
    for (Class<?> cl : vocabEnums()) {
      if (Described.class.isAssignableFrom(cl)) {
        counter++;
        for (Object val : cl.getEnumConstants()) {
          if (StringUtils.isBlank(((Described) val).getDescription())) {
            missing.add(cl.getSimpleName() + "." + val);
          }
        }
      }
    }
    assertTrue("Values without a description: " + missing, missing.isEmpty());
    // Issue, Setting, Gazetteer, IgnoreReason, TaxonomicStatus and many more
    assertTrue("Only " + counter + " described vocabularies found", counter > 15);
  }

  /**
   * A vocabulary with a description getter must say so by implementing Described.
   */
  @Test
  public void describedVocabsImplementInterface() throws Exception {
    for (Class<?> cl : vocabEnums()) {
      for (var m : cl.getDeclaredMethods()) {
        if (m.getName().equals("getDescription") && m.getParameterCount() == 0 && Modifier.isPublic(m.getModifiers())) {
          assertTrue(cl.getName() + " has a description but does not implement Described", Described.class.isAssignableFrom(cl));
        }
      }
    }
  }
}
