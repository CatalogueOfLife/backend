package life.catalogue.common.text;

import java.util.List;

import org.junit.Test;

import com.google.common.base.Joiner;

import static org.junit.Assert.assertEquals;

public class CSVUtilsTest {
  
  int counter = 1;

  @Test
  public void parseLine() {
    testParse("a,b,c,d,e",   "a","b","c","d","e");
    testParse(",,",  null,null,null);
    testParse(",\",,,\"", null, ",,,");
    testParse("k6,KINGDOM,Plantae",   "k6","KINGDOM","Plantae");
    testParse("\"fhsdfgh,; h2\",PHYLUM,\"Tracheophyta, 1677\"",   "fhsdfgh,; h2", "PHYLUM", "Tracheophyta, 1677");
    testParse("\"id\"\"123\"\"\",GENUS,\"Bern'd, (1973)\"",   "id\"123\"", "GENUS", "Bern'd, (1973)");
    testParse(",,Bernd)",   null, null, "Bernd)");
    testParse("",    null);
  }
  
  private void testParse(String line, String... cols) {
    List<String> row = CSVUtils.parseLine(line);
    System.out.println(String.format("%02d %s", counter++, Joiner.on("|").useForNull("").join(row)));
    if (cols != null && cols.length>0) {
      int idx = 0;
      for (String col : row) {
        assertEquals(cols[idx++], col);
      }
    }
  }
}