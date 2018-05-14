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
package org.col.api.jackson;


import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.ArrayUtils;
import org.col.api.vocab.Language;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LanguageSerdeTest extends SerdeMapEnumKeyTestBase<Language> {

  public LanguageSerdeTest() {
    super(Language.class);
  }

  @Test
  public void testMapEquality() throws JsonProcessingException {
    Map<Language, Integer> hm = new HashMap<>();
    Map<Language, Integer> lm = new LinkedHashMap<>();
    for (Language l : Language.values()) {
      hm.put(l, l.ordinal());
      lm.put(l, l.ordinal());
    }
    assertEquals(hm, lm);

    // try different order to make sure its the same
    Map<Language, Integer> lm2 = new LinkedHashMap<>();
    Language[] langs = Language.values();
    ArrayUtils.reverse(langs);
    for (Language l : langs) {
      lm2.put(l, l.ordinal());
    }
    assertEquals(hm, lm2);
    assertEquals(lm, lm2);
  }

  @Test
  public void testLowerCase() throws JsonProcessingException {
    Wrapper<Language> wrapper = new Wrapper<Language>(Language.GERMAN);
    String json = ApiModule.MAPPER.writeValueAsString(wrapper);
    assertEquals("Only lower case letters allowed for language iso codes", json.toLowerCase(), json);
    assertEquals("{\"value\":\"deu\"}", json);
  }
}
