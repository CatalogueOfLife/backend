/*
 * Copyright 2014 Global Biodiversity Information Facility (GBIF)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.col.api.util;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.ClassPath;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VocabularyUtils {
  
  private static final Logger LOG = LoggerFactory.getLogger(VocabularyUtils.class);
  
  public static final TermFactory TF = TermFactory.instance();
  
  /**
   * Finds a term using this term factory, removing also - and # which are not removed in the TermFactory itself.
   */
  public static Term findTerm(String name, boolean isClassTerm) {
    return TF.findTerm(name.replaceAll("[#-]+", ""), isClassTerm);
  }
  
  /**
   * Generic method to toEnum an enumeration value for a given string based on the name of the enum member.
   * The toEnum is case insensitive and ignore whitespaces, underscores and dashes.
   *
   * @param name  the enum members name to toEnum
   * @param vocab the enumeration class
   * @return the matching enum member or null if {@code name} is null or empty (see http://dev.gbif.org/issues/browse/POR-2858)
   * @throws IllegalArgumentException if the name cannot be parsed into a known name
   */
  public static <T extends Enum<?>> T lookupEnum(String name, Class<T> vocab) {
    T val = lookupEnumInternal(name, vocab);
    if (val == null) {
      throw new IllegalArgumentException("Cannot parse " + name + " into a known " + vocab.getSimpleName());
    }
    return val;
  }
  
  /**
   * Same as {@link #lookupEnum(String, Class)} } without IllegalArgumentException.
   * On failure, this method will return Optional.absent().
   *
   * @param name
   * @param vocab
   * @param <T>
   * @return instance of com.google.common.base.Optional, never null.
   */
  public static <T extends Enum<?>> Optional<T> lookup(String name, Class<T> vocab) {
    return Optional.ofNullable(lookupEnumInternal(name, vocab));
  }
  
  private static <T extends Enum<?>> T lookupEnumInternal(String name, Class<T> vocab) {
    if (Strings.isNullOrEmpty(name)) {
      return null;
    }
    final String normedType = name.toUpperCase().replaceAll("[. _-]", "");
    T[] values = vocab.getEnumConstants();
    if (values != null) {
      for (T val : values) {
        final String normedVal = val.name().toUpperCase().replaceAll("[. _-]", "");
        if (normedType.equals(normedVal)) {
          return val;
        }
      }
    }
    return null;
  }
  
  /**
   * Looks up an enumeration by class name. One can get the classname using the likes of:
   * <p/>
   * <p>
   * <pre>
   * {@code
   * Country.class.getName()
   * }
   * </pre>
   *
   * @param fullyQualifiedClassName Which should name the enumeration (e.g. org.col.api.vocab.Country)
   * @return The enumeration or null if {@code fullyQualifiedClassName} is null or empty (see http://dev.gbif.org/issues/browse/POR-2858)
   * @throws IllegalArgumentException if {@code fullyQualifiedClassName} class cannot be located
   */
  @SuppressWarnings("unchecked")
  public static Class<? extends Enum<?>> lookupVocabulary(String fullyQualifiedClassName) {
    if (!Strings.isNullOrEmpty(fullyQualifiedClassName)) {
      try {
        Class<?> cl = Class.forName(fullyQualifiedClassName);
        if (Enum.class.isAssignableFrom(cl)) {
          return (Class<? extends Enum<?>>) cl;
        }
      } catch (Exception e) {
        throw new IllegalArgumentException("Unable to toEnum the vocabulary: " + fullyQualifiedClassName, e);
      }
    }
    return null;
  }
  
  /**
   * Utility method to get a map of all enumerations within a package.
   * The map will use the enumeration class simple name as key and the enum itself as value.
   *
   * @return a map of all enumeration within the package or an empty map in all other cases.
   */
  public static Map<String, Enum<?>[]> listEnumerations(String packageName) {
    try {
      ClassPath cp = ClassPath.from(VocabularyUtils.class.getClassLoader());
      ImmutableMap.Builder<String, Enum<?>[]> builder = ImmutableMap.builder();
      
      List<ClassPath.ClassInfo> infos = cp.getTopLevelClasses(packageName).asList();
      for (ClassPath.ClassInfo info : infos) {
        Class<? extends Enum<?>> vocab = lookupVocabulary(info.getName());
        // verify that it is an Enumeration
        if (vocab != null && vocab.getEnumConstants() != null) {
          builder.put(info.getSimpleName(), vocab.getEnumConstants());
        }
      }
      return builder.build();
    } catch (Exception e) {
      LOG.error("Unable to read the classpath for enumerations", e);
      return ImmutableMap.<String, Enum<?>[]>of(); // empty
    }
  }
  
  /**
   * A static utils class.
   */
  private VocabularyUtils() {
  }
  
  /**
   * Converts an enumeration value into a constant with the exact same name from a different enumeration class.
   * In case the enumeration constant name does not exist an error is thrown.
   *
   * @param targetClass class of the target enumeration
   * @param value
   * @throws IllegalArgumentException in case the enumeration name does not exist in the target class
   */
  public static <G extends Enum<G>> G convertEnum(Class<G> targetClass, Enum<?> value) {
    return value == null ? null : Enum.valueOf(targetClass, value.name());
  }
  
}
