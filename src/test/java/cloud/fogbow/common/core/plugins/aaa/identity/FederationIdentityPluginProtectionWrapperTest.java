package cloud.fogbow.common.core.plugins.aaa.identity;

import cloud.fogbow.common.models.FederationUser;
import org.fogbowcloud.ras.core.plugins.aaa.RASAuthenticationHolder;
import org.fogbowcloud.ras.core.plugins.aaa.tokengenerator.DefaultTokenGeneratorPlugin;
import org.fogbowcloud.ras.core.plugins.aaa.tokengenerator.TokenGeneratorPluginProtectionWrapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPrivateKey;
import java.util.HashMap;

public class FederationIdentityPluginProtectionWrapperTest {
    private FederationIdentityPluginProtectionWrapper federationIdentityPluginProtectionWrapper;
    private RSAPrivateKey privateKey;

    @Before
    public void setUp() throws IOException, GeneralSecurityException {
        this.privateKey = RASAuthenticationHolder.getInstance().getPrivateKey();
        FederationIdentityPlugin embeddedPlugin = new DefaultFederationIdentityPlugin();
        this.federationIdentityPluginProtectionWrapper =
                new FederationIdentityPluginProtectionWrapper(embeddedPlugin);
    }

    //test case: createToken with an encrypted tokenValue should generate a Token with the appropriate values
    @Test
    public void testCreateToken() throws UnexpectedException, FogbowRasException, IOException,
            GeneralSecurityException {
        //set up
        TokenGeneratorPluginProtectionWrapper tokenGeneratorPluginProtectionWrapper =
                new TokenGeneratorPluginProtectionWrapper(new DefaultTokenGeneratorPlugin());
        String protectedTokenValue =
                tokenGeneratorPluginProtectionWrapper.createTokenValue(new HashMap<String, String>());
        String unprotectedTokenValue;

        try {
            String split[] = protectedTokenValue.split(TokenGeneratorPluginProtectionWrapper.SEPARATOR);
            if (split.length != 2) {
                throw new InvalidParameterException();
            }
            String randomKey = RSAUtil.decrypt(split[0], this.privateKey);
            unprotectedTokenValue = RSAUtil.decryptAES(randomKey.getBytes("UTF-8"), split[1]);
        } catch (Exception e) {
            throw new InvalidParameterException();
        }

        //exercise
        FederationUser token =
                this.federationIdentityPluginProtectionWrapper.createToken(protectedTokenValue);
        FederationUser otherToken =
                this.federationIdentityPluginProtectionWrapper.getEmbeddedPlugin().createToken(unprotectedTokenValue);

        //verify
        Assert.assertEquals(token, otherToken);
        Assert.assertEquals(token.getUserId(), otherToken.getUserId());
        Assert.assertEquals(token.getTokenProvider(), otherToken.getTokenProvider());
        Assert.assertEquals(token.getUserName(), otherToken.getUserName());
    }
}
