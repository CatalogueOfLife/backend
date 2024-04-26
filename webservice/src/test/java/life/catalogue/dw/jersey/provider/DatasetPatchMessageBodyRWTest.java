package life.catalogue.dw.jersey.provider;

import com.google.common.base.Charsets;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.License;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.common.io.CsvWriter;
import life.catalogue.common.io.Resources;

import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;

public class DatasetPatchMessageBodyRWTest {

  @Test
  public void readFrom() throws IOException {
    var entity = Resources.stream("jersey/patch.json");
    var rw = new DatasetPatchMessageBodyRW();
    var d = rw.readFrom(Dataset.class, null, null, null, null, entity);
    System.out.println(d);

    assertEquals(Dataset.NULL_TYPES.get("doi"), d.getDoi());
    assertEquals(Dataset.NULL_TYPES.get("issued"), d.getIssued());
    assertEquals(Dataset.NULL_TYPES.get("contact"), d.getContact());
    assertEquals(Dataset.NULL_TYPES.get("publisher"), d.getPublisher());
    assertEquals(Dataset.NULL_TYPES.get("contributor"), d.getContributor());
  }

  @Test
  public void writeTo() throws IOException {
    final var rw = new DatasetPatchMessageBodyRW();

    Dataset d = new Dataset();
    d.setKey(278852);
    d.setAlias("COL");
    d.setIssued((FuzzyDate) Dataset.NULL_TYPES.get("issued"));
    d.setDoi((DOI) Dataset.NULL_TYPES.get("doi"));
    d.setDescription("COL as we know it");
    d.setContact((Agent) Dataset.NULL_TYPES.get("contact"));
    d.setPublisher((Agent) Dataset.NULL_TYPES.get("publisher"));
    d.setContributor((List<Agent>) Dataset.NULL_TYPES.get("contributor"));
    d.setLicense(License.CC_BY);

    var out = new ByteArrayOutputStream();
    rw.writeTo(d, Dataset.class, null, null, null, null, out);
    var json = out.toString(Charsets.UTF_8);
    json.replace("\"label\":\"null\"", "");
    json.replace("\"private\":false", "");
    System.out.println(json);

    assertTrue(json.contains("\"issued\":null"));
    assertTrue(json.contains("\"doi\":null"));
    assertTrue(json.contains("\"contact\":null"));
    assertTrue(json.contains("\"publisher\":null"));
    assertTrue(json.contains("\"contributor\":null"));

    assertTrue(json.contains("\"description\":\"COL as we know it\""));
    assertTrue(json.contains("\"alias\":\"COL\""));
    System.out.println(json);
  }
}