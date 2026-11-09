package life.catalogue.event;

import life.catalogue.api.event.*;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DatasetOrigin;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventCodecTest {
  final EventCodec codec = new EventCodec();

  static Dataset dataset(boolean privat) {
    var d = new Dataset();
    d.setKey(3001);
    d.setOrigin(DatasetOrigin.RELEASE);
    d.setSourceKey(3);
    d.setGbifPublisherKey(UUID.randomUUID());
    d.setCreatedBy(101);
    d.setPrivat(privat);
    return d;
  }

  @Test
  void roundtrip() {
    var u = new User();
    u.setKey(101);
    u.setUsername("Peter");
    var deleted = DatasetChanged.deleted(dataset(false), 102);
    deleted.usernamesToInvalidate.add("Peter");
    for (Event e : List.of(
      DatasetChanged.created(dataset(true), 101),
      DatasetChanged.changed(dataset(false), dataset(true), 101),
      deleted,
      new DatasetDataChanged(3, 101),
      new DatasetLogoChanged(3),
      UserChanged.changed(u, 100),
      new UserPermissionChanged("Peter"),
      new FlushCaches()
    )) {
      String json = codec.encode(e);
      assertTrue(json.getBytes(StandardCharsets.UTF_8).length < EventCodec.MAX_BYTES, json);
      assertEquals(e, codec.decode(json), json);
    }
  }

  @Test
  void thinFacts() {
    var e = (DatasetChanged) codec.decode(codec.encode(DatasetChanged.changed(dataset(false), dataset(true), 101)));
    assertTrue(e.isUpdated());
    assertTrue(e.privatChanged);
    assertTrue(e.isMadePublic());
    assertTrue(e.isRelease());
    assertEquals(3, (int) e.sourceKey);
    assertFalse(e.deleted);

    e = (DatasetChanged) codec.decode(codec.encode(DatasetChanged.deleted(dataset(true), 101)));
    assertTrue(e.isDeletion());
    assertTrue(e.deleted);
    assertFalse(e.isMadePublic());
  }

  @Test
  void unknownIsNull() {
    assertNull(codec.decode("{\"t\":\"martian\"}"));
    assertNull(codec.decode("{\"key\":1}"));
    assertNull(codec.decode("rubbish"));
    assertEquals(new DatasetLogoChanged(7), codec.decode("{\"t\":\"logo\",\"datasetKey\":7,\"color\":\"red\"}"));
  }

  @Test
  void oversizeBecomesFlush() {
    var e = DatasetChanged.deleted(dataset(false), 1);
    for (int i = 0; i < 1000; i++) {
      e.usernamesToInvalidate.add("user" + i);
    }
    assertEquals(new FlushCaches(), codec.decode(codec.encode(e)));
  }
}
