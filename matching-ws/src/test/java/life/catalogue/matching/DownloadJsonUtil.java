/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package life.catalogue.matching;

import life.catalogue.matching.util.IOUtils;

import org.gbif.api.model.checklistbank.NameUsageMatch;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Assertions;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Manual utility to generate a list of unique canonical names from the test index json files and
 * more importantly to download new species match json files for a list of names to query the
 * backbone for.
 */
public class DownloadJsonUtil {
  public static Set<String> extract() {
    Set<String> names = new TreeSet<>();
    ObjectMapper mapper = new ObjectMapper();
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    int id = 0;
    while (id < 150) {
      String file = "index/nub" + id + ".json";
      InputStream json = IOUtils.classpathStream(file);
      if (json != null) {
        try {
          NameUsageMatch m = mapper.readValue(json, NameUsageMatch.class);
          if (m.getUsageKey() != null) {
            names.add(m.getCanonicalName());
          } else {
            for (NameUsageMatch m2 : m.getAlternatives()) {
              if (m2.getUsageKey() != null) {
                names.add(m2.getCanonicalName());
                break;
              }
            }
          }

          if (m.getAlternatives() != null && m.getAlternatives().size() >= 4) {
            if (m.getAlternatives().get(3).getUsageKey() != null) {
              names.add(m.getAlternatives().get(3).getCanonicalName());
            }
            if (m.getAlternatives().size() >= 10) {
              if (m.getAlternatives().get(9).getUsageKey() != null) {
                names.add(m.getAlternatives().get(9).getCanonicalName());
              }
            }
          }
        } catch (IOException e) {
          Assertions.fail("Failed to read " + file + ": " + e.getMessage());
        }
      }
      id++;
    }
    return names;
  }

  public static void download(Set<String> names) throws IOException {
    // FIXME
    //    HttpUtil http = new HttpUtil(new DefaultHttpClient());
    //    int i=1;
    //    for (String n : names) {
    //      File json = new File("/Users/markus/nub/nub"+ i++ +".json");
    //      String url =
    // "http://api.gbif-uat.org/v1/species/match?verbose=true&name="+n.replaceAll(" ", "%20");
    //      System.out.println(url);
    //      http.download(url, json);
    //    }
  }

  public static void main(String[] args) throws IOException {
    download(extract());
  }
}
