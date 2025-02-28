package life.catalogue.feedback;

import java.security.GeneralSecurityException;

import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

public class EmailEncryption {

  private final TextEncryptor encryptor;

  public EmailEncryption(String password, String salt) {
    this.encryptor = Encryptors.text(password, salt);
  }

  public String encrypt(String input) {
    return encryptor.encrypt(input);
  }

  public String decrypt(String cipherText) {
    return encryptor.decrypt(cipherText);
  }
}
