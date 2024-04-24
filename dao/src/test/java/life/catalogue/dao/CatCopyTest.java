package life.catalogue.dao;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CatCopyTest {
  
  @Test
  public void latinName() throws Exception {
    assertEquals("Abies", CopyUtil.latinName("Abies"));
    assertEquals("Bao wen dong fang tun", CopyUtil.latinName("Bào wén dōng fāng tún"));
    assertEquals("bao wen duo ji tun", CopyUtil.latinName("豹紋多紀魨"));
    assertEquals("Alphabetikos Katalogos", CopyUtil.latinName("Αλφαβητικός Κατάλογος"));
    assertEquals("Doering Spass", CopyUtil.latinName("Döring Spaß"));
  }
  
}