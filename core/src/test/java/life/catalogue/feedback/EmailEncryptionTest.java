package life.catalogue.feedback;

import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.*;

class EmailEncryptionTest {

  @Test
  void roundtrip() throws GeneralSecurityException {
    final String password = "seÖfgzh6SG.d22";
    final String salt = "8560b4f4b3";

    var text = "sibylle.lustig@parents-for-future.de";
    var enc = new EmailEncryption(password, salt);
    var cipher = enc.encrypt(text);

    var dec = new EmailEncryption(password, salt);
    var clear = dec.decrypt(cipher);

    assertEquals(text, clear);
  }

}