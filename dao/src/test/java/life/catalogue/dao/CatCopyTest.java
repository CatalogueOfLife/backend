package life.catalogue.dao;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CatCopyTest {
  
  @Test
  public void latinName() throws Exception {
    assertEquals("Abies", CatCopy.latinName("Abies"));
    assertEquals("Bao wen dong fang tun", CatCopy.latinName("Bào wén dōng fāng tún"));
    assertEquals("bao wen duo ji tun", CatCopy.latinName("豹紋多紀魨"));
    assertEquals("Alphabetikos Katalogos", CatCopy.latinName("Αλφαβητικός Κατάλογος"));
    assertEquals("Doering Spass", CatCopy.latinName("Döring Spaß"));
  }
  
}