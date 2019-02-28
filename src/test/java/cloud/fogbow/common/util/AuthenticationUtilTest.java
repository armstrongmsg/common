package cloud.fogbow.common.util;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.UnauthenticatedUserException;
import cloud.fogbow.common.models.FederationUser;
import cloud.fogbow.common.stubs.StubTokenGenerator;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.*;

import static org.junit.Assert.*;

public class AuthenticationUtilTest {

    private StubTokenGenerator tokenGenerator;
    private String publicKeyString;
    private PublicKey publicKey;
    private PrivateKey privateKey;

    @Before
    public void setUp() throws Exception {

        String keysPath = HomeDir.getPath();
        String pubKeyPath = keysPath + "public.key";
        String privKeyPath = keysPath + "private.key";

        ServiceAsymmetricKeysHolder.getInstance().setPublicKeyFilePath(pubKeyPath);
        ServiceAsymmetricKeysHolder.getInstance().setPrivateKeyFilePath(privKeyPath);

        this.publicKey = RSAUtil.getPublicKey(pubKeyPath);
        this.privateKey = RSAUtil.getPrivateKey(privKeyPath);

        this.publicKeyString = RSAUtil.getKey(pubKeyPath);
        this.tokenGenerator = new StubTokenGenerator();
    }

    @Test
    public void testSuccessfulAuthentication() throws IOException, GeneralSecurityException, FogbowException {
        // set up
        String tokenValue = tokenGenerator.createTokenValue(publicKeyString, 1);

        // exercise
        FederationUser federationUser = AuthenticationUtil.authenticate(publicKey, tokenValue);

        // verify
        assertNotEquals(null, federationUser);
    }

    @Test(expected = UnauthenticatedUserException.class)
    public void testExpiredToken() throws InterruptedException, FogbowException {
        // set up
        String tokenValue = tokenGenerator.createTokenValue(publicKeyString, 0);
        Thread.sleep(2000);

        // exercise
        FederationUser federationUser = AuthenticationUtil.authenticate(publicKey, tokenValue);
    }

    @Test(expected = UnauthenticatedUserException.class)
    public void testInvalidSignature() throws FogbowException, GeneralSecurityException {
        // set up
        KeyPair keyPair = RSAUtil.generateKeyPair();
        PublicKey differentKey = keyPair.getPublic();
        String differentKeyString = RSAUtil.savePublicKey(differentKey);
        String tokenValue = tokenGenerator.createTokenValue(differentKeyString, 1);

        // exercise
        // Try to verify the signature of a different key
        FederationUser federationUser = AuthenticationUtil.authenticate(publicKey, tokenValue);
    }
}