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


import java.io.IOException;

import org.col.api.vocab.CSLRefType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CSLRefTypeSerdeTest extends EnumSerdeTestBase<CSLRefType> {

  public CSLRefTypeSerdeTest() {
    super(CSLRefType.class);
  }

  @Test
  public void testUnderscores() throws IOException {
    assertEquals("\"article-magazine\"", ApiModule.MAPPER.writeValueAsString(CSLRefType.ARTICLE_MAGAZINE));
    assertEquals("\"article\"", ApiModule.MAPPER.writeValueAsString(CSLRefType.ARTICLE));
    assertEquals("\"personal_communication\"", ApiModule.MAPPER.writeValueAsString(CSLRefType.PERSONAL_COMMUNICATION));

    assertEquals(CSLRefType.ARTICLE_MAGAZINE, ApiModule.MAPPER.readValue("\"article-magazine\"", CSLRefType.class));
    assertEquals(CSLRefType.ARTICLE, ApiModule.MAPPER.readValue("\"article\"", CSLRefType.class));
    assertEquals(CSLRefType.PERSONAL_COMMUNICATION, ApiModule.MAPPER.readValue("\"personal-Communication\"", CSLRefType.class));
    assertEquals(CSLRefType.PERSONAL_COMMUNICATION, ApiModule.MAPPER.readValue("\"personal_communication\"", CSLRefType.class));
    assertEquals(CSLRefType.PERSONAL_COMMUNICATION, ApiModule.MAPPER.readValue("\"personal communication\"", CSLRefType.class));
  }

}
