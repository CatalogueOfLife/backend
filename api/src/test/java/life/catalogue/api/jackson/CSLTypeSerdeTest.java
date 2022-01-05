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
package life.catalogue.api.jackson;


import java.io.IOException;

import org.junit.Test;

import de.undercouch.citeproc.csl.CSLType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CSLTypeSerdeTest extends EnumSerdeTestBase<CSLType> {
  
  public CSLTypeSerdeTest() {
    super(CSLType.class);
  }
  
  @Test
  public void testUnderscores() throws IOException {
    assertEquals("\"article-magazine\"", ApiModule.MAPPER.writeValueAsString(CSLType.ARTICLE_MAGAZINE));
    assertEquals("\"article\"", ApiModule.MAPPER.writeValueAsString(CSLType.ARTICLE));
    assertEquals("\"personal_communication\"", ApiModule.MAPPER.writeValueAsString(CSLType.PERSONAL_COMMUNICATION));
    
    assertEquals(CSLType.ARTICLE_MAGAZINE, ApiModule.MAPPER.readValue("\"article-magazine\"", CSLType.class));
    assertEquals(CSLType.ARTICLE, ApiModule.MAPPER.readValue("\"article\"", CSLType.class));
    assertEquals(CSLType.PERSONAL_COMMUNICATION, ApiModule.MAPPER.readValue("\"personal-Communication\"", CSLType.class));
    assertEquals(CSLType.PERSONAL_COMMUNICATION, ApiModule.MAPPER.readValue("\"personal_communication\"", CSLType.class));
    assertEquals(CSLType.PERSONAL_COMMUNICATION, ApiModule.MAPPER.readValue("\"personal communication\"", CSLType.class));
  }
  
  @Test
  public void testBadAnystyleValues() throws IOException {
    assertNull(ApiModule.MAPPER.readValue("\"Misc\"", CSLType.class));
    assertNull(ApiModule.MAPPER.readValue("\"III\"", CSLType.class));
    assertNull(ApiModule.MAPPER.readValue("\"197\"", CSLType.class));
  }
  
}
