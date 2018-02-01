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
package org.col.dw.api.jackson;


import com.fasterxml.jackson.core.JsonProcessingException;
import org.col.dw.api.vocab.Language;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LanguageSerdeTest extends SerdeTestBase<Language> {

  public LanguageSerdeTest() {
    super(Language.class, LanguageSerde.MODULE);
  }

  @Test
  public void testLowerCase() throws JsonProcessingException {
    Wrapper<Language> wrapper = new Wrapper<Language>(Language.SUNDANESE);
    String json = MAPPER.writeValueAsString(wrapper);
    assertEquals("Only lower case letters allowed for language iso codes", json.toLowerCase(), json);
  }
}
