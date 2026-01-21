package life.catalogue.event;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.event.*;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.Users;

import java.util.Set;

import org.junit.Test;

import static life.catalogue.common.kryo.ApiKryoPoolTest.assertSerde;

public class EventKryoPoolTest {
  EventKryoPool kryo = new EventKryoPool(1);

  @Test
  public void testEvents() throws Exception {
    assertSerde(kryo, new DatasetLogoChanged(8));
    assertSerde(kryo, new DatasetDataChanged(8, Users.TESTER));
    assertSerde(kryo, new UserPermissionChanged("markus"));
    assertSerde(kryo, new DatasetLogoChanged(8));
    assertSerde(kryo, new DeleteSector(DSID.of(123, 1234), 18));

    User u = new User();
    u.setKey(123);
    u.setUsername("markus");
    u.setLastname("DD");
    u.setFirstname("Markus");
    u.setEmail("<EMAIL>");
    u.setOrcid("0000-0002-1825-0097");
    u.setRoles(Set.of(User.Role.EDITOR));
    u.setCountry(Country.GERMANY);
    assertSerde(kryo, UserChanged.changed(u, Users.TESTER));

    Dataset d1 = TestEntityGenerator.newFullDataset(12);
    Dataset d2 = TestEntityGenerator.newFullDataset(13);
    assertSerde(kryo, DatasetChanged.changed(d1, d2, 18));
  }
}