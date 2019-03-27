package org.col.common.csl;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.col.api.TestEntityGenerator;
import org.col.api.jackson.ApiModule;
import org.col.api.model.CslData;
import org.col.common.io.Resources;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CslUtilTest {

  @Test
  public void makeBibliography() {
    for (int i = 0; i < 10; i++) {
      System.out.println(CslUtil.buildCitation(TestEntityGenerator.newReference()));
    }
  }
  
  @Test
  public void readTest() throws IOException {
    final ObjectMapper OM = ApiModule.MAPPER;
    InputStream in = Resources.stream("references/test.json");
    TypeReference<List<CslData>> cslType = new TypeReference<List<CslData>>(){};
    List<CslData> refs = OM.readValue(in, cslType);

    List<String> expected = Lists.newArrayList(
        "Droege, G., Barker, K., Seberg, O., Coddington, J., Benson, E., Berendsohn, W. G., Bunk, B., et al. (2016). The Global Genome Biodiversity Network (GGBN) Data Standard specification. Database, 2016, baw125.",
        "Frank, H. S. (1970). The Structure of Ordinary Water: New data and interpretations are yielding new insights into this fascinating substance. Science, 169(3946), 635–641."
    );
    int idx=0;
    for (CslData csl : refs) {
      assertEquals(expected.get(idx++), CslUtil.buildCitation(csl));
    }
  }
}