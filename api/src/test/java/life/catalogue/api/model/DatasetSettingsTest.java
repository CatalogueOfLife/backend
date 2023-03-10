package life.catalogue.api.model;

import life.catalogue.api.jackson.SerdeTestBase;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Frequency;
import life.catalogue.api.vocab.Gazetteer;
import life.catalogue.api.vocab.Setting;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 */
public class DatasetSettingsTest extends SerdeTestBase<DatasetSettings> {

  public DatasetSettingsTest() {
    super(DatasetSettings.class);
  }
  
  @Override
  public DatasetSettings genTestValue() throws Exception {
    return generateTestValue();
  }

  public static DatasetSettings generateTestValue() {

    DatasetSettings ds = DatasetSettings.of(Map.ofEntries(
      Map.entry(Setting.DISTRIBUTION_GAZETTEER, Gazetteer.ISO),
      Map.entry(Setting.REMATCH_DECISIONS, true),
      Map.entry(Setting.CSV_DELIMITER, "\t"),
      Map.entry(Setting.CSV_QUOTE, "\""),
      Map.entry(Setting.CSV_QUOTE_ESCAPE, "\\"),
      Map.entry(Setting.NOMENCLATURAL_CODE, NomCode.BOTANICAL),
      Map.entry(Setting.IMPORT_FREQUENCY, Frequency.MONTHLY),
      Map.entry(Setting.DATA_ACCESS, URI.create("www.gbif.org")),
      Map.entry(Setting.SECTOR_ENTITIES, List.of(EntityType.VERNACULAR)),
      Map.entry(Setting.SECTOR_RANKS, List.of(Rank.GENUS, Rank.SPECIES, Rank.SUBGENUS, Rank.TRIBE))
    ));
    return ds;
  }

  @Test
  public void testSettings() throws Exception {
    DatasetSettings d = new DatasetSettings();

    assertNull(d.getURI(Setting.XRELEASE_CONFIG));
    assertNull(d.getEnum(Setting.NOMENCLATURAL_CODE));

    d.put(Setting.NOMENCLATURAL_CODE, NomCode.BOTANICAL);
    assertEquals(NomCode.BOTANICAL, d.getEnum(Setting.NOMENCLATURAL_CODE));

    assertNull(d.getChar(Setting.CSV_QUOTE));
    assertFalse(d.has(Setting.CSV_QUOTE));

    d.put(Setting.CSV_QUOTE, null);
    assertNull(d.getChar(Setting.CSV_QUOTE));
    assertFalse(d.has(Setting.CSV_QUOTE));

    d.put(Setting.CSV_QUOTE, '\t');
    assertEquals(Character.valueOf('\t'), d.getChar(Setting.CSV_QUOTE));
    assertTrue(d.has(Setting.CSV_QUOTE));
  }


  @Test(expected = IllegalArgumentException.class)
  public void testIllegalChar() throws Exception {
    DatasetSettings d = new DatasetSettings();
    d.put(Setting.CSV_DELIMITER, "<tab>");
  }

}