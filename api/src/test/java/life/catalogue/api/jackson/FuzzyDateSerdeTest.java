package life.catalogue.api.jackson;

import life.catalogue.common.date.FuzzyDate;

import org.junit.Test;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FuzzyDateSerdeTest extends SerdeTestBase<FuzzyDate> {

  public FuzzyDateSerdeTest() {
    super(FuzzyDate.class);
  }

  @Override
  public FuzzyDate genTestValue() throws Exception {
    return FuzzyDate.now();
  }

  @Test
  public void cslSerdeRoundtrip() throws Exception {
    CslWrapper wrapper = new CslWrapper(FuzzyDate.of(1999,1,13));
    String json = ApiModule.MAPPER.writeValueAsString(wrapper);
    System.out.println(json);
    assertTrue(json.contains("[[1999,1,13]]"));
    assertTrue(json.contains("date-parts"));
    CslWrapper wrapper2 = ApiModule.MAPPER.readValue(json, CslWrapper.class);
    assertEquals(wrapper.value, wrapper2.value);
  }

  public static class CslWrapper {
    @JsonSerialize(using = FuzzyDateCSLSerde.Serializer.class)
    @JsonDeserialize(using = FuzzyDateCSLSerde.Deserializer.class)
    public FuzzyDate value;

    public CslWrapper() {
    }

    public CslWrapper(FuzzyDate value) {
      this.value = value;
    }
  }
}