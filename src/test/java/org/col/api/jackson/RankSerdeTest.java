package org.col.api.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.col.api.vocab.Rank;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class RankSerdeTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  public void testRoundtrip() throws IOException {

    for (Rank r : Rank.values()) {
      RankWrapper rank = new RankWrapper(r);
      String json = MAPPER.writeValueAsString(rank);
      assertEquals(rank.rank, MAPPER.readValue(json, RankWrapper.class).rank);
    }
  }

  public static class RankWrapper {
    @JsonSerialize(using = RankSerde.Serializer.class)
    @JsonDeserialize(using = RankSerde.Deserializer.class)
    public Rank rank;

    public RankWrapper(){}

    public RankWrapper(Rank rank){
      this.rank = rank;
    }
  }
}
