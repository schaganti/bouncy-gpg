package name.neuhalfen.projects.crypto.bouncycastle.openpgp.testtooling.gpg;

import name.neuhalfen.projects.crypto.bouncycastle.openpgp.testtooling.gpg.list.ListKeysCommand;

public final class Commands {

  private Commands() {
  }

  public static ListPacketCommand listPackets(final String comment, final byte[] data) {
    return new ListPacketCommand(comment,data);
  }

  public static ListKeysCommand listKeys() {
    return new ListKeysCommand(false);
  }

  public static ListKeysCommand listSecretKeys() {
    return new ListKeysCommand(true);
  }

  public static VersionCommand version() {
    return new VersionCommand();
  }

  public static ImportCommand importKey(byte[] key, String passphrase) {
    return new ImportCommand(key, passphrase);
  }

  public static ImportCommand importKey(byte[] key) {
    return new ImportCommand(key);
  }

  public static DecryptCommand decrypt(byte[] ciphertext, String passphrase) {
    return new DecryptCommand(ciphertext,
        passphrase);
  }

  public static EncryptCommand encrypt(byte[] plaintext, String toUid) {
    return new EncryptCommand(plaintext, toUid);
  }
}
