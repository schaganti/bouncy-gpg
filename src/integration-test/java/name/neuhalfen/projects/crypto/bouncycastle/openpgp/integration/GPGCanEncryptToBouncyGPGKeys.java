package name.neuhalfen.projects.crypto.bouncycastle.openpgp.integration;

import name.neuhalfen.projects.crypto.bouncycastle.openpgp.BouncyGPG;
import name.neuhalfen.projects.crypto.bouncycastle.openpgp.integration.BouncyGPGCanEncryptToGPG.TestFixture;
import name.neuhalfen.projects.crypto.bouncycastle.openpgp.keys.keyrings.KeyringConfig;
import name.neuhalfen.projects.crypto.bouncycastle.openpgp.testtooling.gpg.Commands;
import name.neuhalfen.projects.crypto.bouncycastle.openpgp.testtooling.gpg.EncryptCommand.EncryptCommandResult;
import name.neuhalfen.projects.crypto.bouncycastle.openpgp.testtooling.gpg.GPGExec;
import name.neuhalfen.projects.crypto.bouncycastle.openpgp.testtooling.gpg.ImportCommand;
import name.neuhalfen.projects.crypto.bouncycastle.openpgp.testtooling.gpg.Result;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.util.io.Streams;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import java.io.*;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;
import java.util.Collection;

import static name.neuhalfen.projects.crypto.bouncycastle.openpgp.integration.BouncyGPGCanEncryptToGPG.TestFixture.testFixture;
import static name.neuhalfen.projects.crypto.bouncycastle.openpgp.integration.Helper.logPackets;
import static name.neuhalfen.projects.crypto.bouncycastle.openpgp.integration.KeyRingGenerators.EMAIL_JULIET;
import static org.junit.Assume.assumeTrue;

/**
 * Test that gpg can encrypt to BouncyGPG generated keys.
 * <p>
 * This is a "sawed off shotgun" kind of test: Throw some freshly generated values at GPG to test
 * interop. If the test fails the logfiles written to the per-test tempdir can be used to analyse
 * the cause of the failure.
 */
@RunWith(Parameterized.class)
public class GPGCanEncryptToBouncyGPGKeys {

    private final static String NO_PASSPHRASE = null; // no passphrase
    private final static String WITH_PASSPHRASE = "This is secret";

    private final static String PLAINTEXT = "See how she leans her cheek upon her hand.\n"
            + "O, that I were a glove upon that hand\n"
            + "That I might touch that cheek! (Romeo)";


    @Parameter(value = 0)
    public String testName;

    /**
     * skipTest can be used to toggle off certain tests.
     */
    @Parameter(value = 1)
    public boolean skipTest;

    @Parameter(value = 2)
    public TestFixture fixtureStrategies;

    @Parameterized.Parameters(name = "{index}: {0}")
    public static Collection<Object[]> keyRingGenerators() {
        return Arrays.asList(new Object[][]{
                        {
                                "Simple RSA keyring without passphrase",
                                false,
                                testFixture(KeyRingGenerators::generateSimpleRSAKeyring,
                                        NO_PASSPHRASE)

                        },
                        {
                                "Complex RSA keyring with a passphrase",
                                false,
                                testFixture(KeyRingGenerators::generateComplexRSAKeyring,
                                        WITH_PASSPHRASE)

                        },
                        {
                                "Simple ECC keyring without passphrase",
                                true,
                                testFixture(KeyRingGenerators::generateSimpleECCKeyring,
                                        NO_PASSPHRASE)
                        },
                        {
                                "Complex RSA with ECC subkey keyring and passphrase",
                                true,
                                testFixture(KeyRingGenerators::generateRSAWithECCSubkeyKeyring,
                                        WITH_PASSPHRASE)
                        },
                        {
                                "Complex ECC with ECC subkey keyring and passphrase",
                                true,
                                testFixture(KeyRingGenerators::generateComplexEccKeyring,
                                        WITH_PASSPHRASE)
                        },
                        {
                                "Complex ECC with ECC subkey keyring and without passphrase",
                                true,
                                testFixture(KeyRingGenerators::generateComplexEccKeyring,
                                        NO_PASSPHRASE)
                        },
                }
        );
    }

    @Before
    public void setup() {
        BouncyGPG.registerProvider();
    }

    @Test
    public void gpgCanEncryptToGeneratedKeyPair()
            throws IOException, InterruptedException, PGPException, NoSuchAlgorithmException,
            NoSuchProviderException, InvalidAlgorithmParameterException {
        assumeTrue(! skipTest);


        // we generate a keyring to Juliet with BouncyGPG,
        // copy the public key to GPG,
        // encrypt a message in GPG,
        // and finally decrypt the message in BouncyGPG
        final GPGExec gpg = GPGExec.newInstance();

        final KeyringConfig keyring = fixtureStrategies.keyRingGenerator
                .generateKeyringWithBouncyGPG(gpg.version(), fixtureStrategies.passphrase);

        importPublicKeyInGPG(gpg, keyring.getPublicKeyRings());
        logPackets(gpg, "Secret keyring", keyring.getSecretKeyRings().getEncoded());

        byte[] chiphertext = encryptMessageInGPG(gpg, PLAINTEXT, EMAIL_JULIET);
        logPackets(gpg, "Ciphertext", chiphertext);

        String decryptedPlaintext = decrpytMessageInBouncyGPG(keyring, chiphertext);

        Assert.assertThat(decryptedPlaintext, Matchers.equalTo(PLAINTEXT));
    }

    private String decrpytMessageInBouncyGPG(final KeyringConfig keyring,
                                             final byte[] chiphertext)
            throws IOException {

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (
                final InputStream cipherTextStream = new ByteArrayInputStream(chiphertext);

                final BufferedOutputStream bufferedOut = new BufferedOutputStream(output);

                final InputStream plaintextStream = BouncyGPG
                        .decryptAndVerifyStream()
                        .withConfig(keyring)
                        .andIgnoreSignatures()
                        .fromEncryptedInputStream(cipherTextStream);

        ) {
            Streams.pipeAll(plaintextStream, bufferedOut);
        } catch (NoSuchProviderException | IOException e) {
            Assert.fail(e.getMessage());
        }
        output.close();
        final String decrypted_message = new String(output.toByteArray());
        return decrypted_message;
    }

    private byte[] encryptMessageInGPG(final GPGExec gpg,
                                       final String plaintext,
                                       final String recipient) throws IOException, InterruptedException {

        final EncryptCommandResult encryptCommandResult = gpg
                .runCommand(Commands.encrypt(plaintext.getBytes(), recipient));
        Assert.assertEquals(0, encryptCommandResult.exitCode());

        return encryptCommandResult.getCiphertext();
    }

    private void importPublicKeyInGPG(final GPGExec gpg,
                                      final PGPPublicKeyRingCollection publicKeyRings)
            throws IOException, InterruptedException {
        final byte[] encoded = publicKeyRings.getEncoded();

        final Result<ImportCommand> importCommandResult = gpg.runCommand(Commands.importKey(encoded));

        Assert.assertEquals(0, importCommandResult.exitCode());
    }

}
