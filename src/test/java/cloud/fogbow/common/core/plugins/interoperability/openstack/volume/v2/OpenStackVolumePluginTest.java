package cloud.fogbow.common.core.plugins.interoperability.openstack.volume.v2;

import org.apache.http.client.HttpResponseException;
import cloud.fogbow.common.core.PropertiesHolder;
import cloud.fogbow.common.core.constants.SystemConstants;
import cloud.fogbow.common.core.models.instances.VolumeInstance;
import cloud.fogbow.common.core.models.orders.VolumeOrder;
import org.fogbowcloud.ras.core.models.tokens.OpenStackV3Token;
import org.fogbowcloud.ras.core.models.tokens.Token;
import cloud.fogbow.common.util.connectivity.AuditableHttpRequestClient;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class OpenStackVolumePluginTest {

    private final String FAKE_STORAGE_URL = "http://localhost:0000";
    private final String FAKE_SIZE = "2";
    private final String FAKE_VOLUME_ID = "fake-id";
    private final String FAKE_NAME = "fake-name";
    private final String FAKE_VOLUME_TYPE = "fake-type";
    private final String FAKE_TOKEN_PROVIDER = "fake-token-provider";
    private final String FAKE_TOKEN_VALUE = "fake-token-value";
    private final String FAKE_USER_ID = "fake-user-id";
    private final String FAKE_PROJECT_ID = "fake-project-id";
    private final String FAKE_INSTANCE_ID = "instance-id";

    // TODO create this json with a library
    private final String FAKE_VOLUME_JSON = "{\"volume\":{\"size\":2,\"name\":\"fake-name\", " +
            "\"id\": \"fake-id\", \"status\": \"fake-status\"}}";
    private final String FAKE_TYPES_JSON = "{\"volume_types\": [" +
          "{\"extra_specs\": {\"fake-capabilities\": \"fake-value\" }," +
          "\"id\": \"fake-id\"," +
          "\"name\": \"SSD\"}]}";

    private OpenStackVolumePlugin openStackVolumePlugin;
    private OpenStackV3Token openStackV3Token;
    private AuditableHttpRequestClient auditableHttpRequestClient;

    @Before
    public void setUp() throws Exception {
        PropertiesHolder propertiesHolder = PropertiesHolder.getInstance();
        Properties properties = propertiesHolder.getProperties();
        properties.put(OpenStackVolumePlugin.VOLUME_NOVAV2_URL_KEY, FAKE_STORAGE_URL);
        String cloudConfPath = HomeDir.getPath() + SystemConstants.CLOUDS_CONFIGURATION_DIRECTORY_NAME + File.separator
                + "default" + File.separator + SystemConstants.CLOUD_SPECIFICITY_CONF_FILE_NAME;
        this.openStackVolumePlugin = Mockito.spy(new OpenStackVolumePlugin(cloudConfPath));
        this.auditableHttpRequestClient = Mockito.mock(AuditableHttpRequestClient.class);
        this.openStackVolumePlugin.setClient(this.auditableHttpRequestClient);
        this.openStackV3Token = new OpenStackV3Token(FAKE_TOKEN_PROVIDER, FAKE_TOKEN_VALUE, FAKE_USER_ID, 
        			FAKE_NAME, FAKE_PROJECT_ID, null);
    }

    // test case: Check if the request in requestInstance() is executed properly with the right parameters.
    @Test
    public void testRequestInstance() throws FogbowRasException, UnexpectedException, HttpResponseException {
        // set up
        VolumeOrder volumeOrder = Mockito.mock(VolumeOrder.class);

        Mockito.doReturn(FAKE_VOLUME_JSON).when(this.auditableHttpRequestClient).doPostRequest(
                Mockito.anyString(), Mockito.any(Token.class), Mockito.any());

        // exercise
        String instanceString = this.openStackVolumePlugin.requestInstance(volumeOrder, this.openStackV3Token);

        // verify
        Mockito.verify(this.auditableHttpRequestClient).doPostRequest(Mockito.anyString(), Mockito.any(Token.class),
                Mockito.any());
        Assert.assertEquals(FAKE_VOLUME_ID, instanceString);
    }

    // test case: Check if the request in requestInstance() is executed properly with the right parameters even when
    // there is volume extra requirements.
    @Test
    public void testRequestInstanceWithRequirements() throws FogbowRasException, UnexpectedException, HttpResponseException {
        // set up
        VolumeOrder volumeOrder = new VolumeOrder(null, null, "fake-name", 2);
        Map<String, String> requirements = new HashMap<>();
        requirements.put("fake-capabilities", "fake-value");
        volumeOrder.setRequirements(requirements);

        Mockito.doReturn(FAKE_TYPES_JSON).when(this.auditableHttpRequestClient).doGetRequest(
                Mockito.anyString(), Mockito.any(Token.class));
        Mockito.doReturn(FAKE_VOLUME_JSON).when(this.auditableHttpRequestClient).doPostRequest(
                Mockito.anyString(), Mockito.any(Token.class), Mockito.any());

        // exercise
        String instanceString = this.openStackVolumePlugin.requestInstance(volumeOrder, this.openStackV3Token);

        // verify
        Mockito.verify(this.auditableHttpRequestClient).doGetRequest(Mockito.anyString(), Mockito.any(Token.class));
        Mockito.verify(this.auditableHttpRequestClient).doPostRequest(Mockito.anyString(), Mockito.any(Token.class),
                Mockito.any());
        Assert.assertEquals(FAKE_VOLUME_ID, instanceString);
    }

    // test case: requestInstance() should raise FogbowRasException in case requirement is not found
    @Test(expected = FogbowRasException.class)
    public void testRequestInstanceWithRequirementsFail() throws FogbowRasException, UnexpectedException, HttpResponseException {
        // set up
        VolumeOrder volumeOrder = new VolumeOrder(null, null, "fake-name", 2);
        Map<String, String> requirements = new HashMap<>();
        requirements.put("fake-capabilities", "fake-value");
        requirements.put("additional-fake-capabilities", "additional-fake-value");
        volumeOrder.setRequirements(requirements);

        Mockito.doReturn(FAKE_TYPES_JSON).when(this.auditableHttpRequestClient).doGetRequest(
                Mockito.anyString(), Mockito.any(Token.class));

        // exercise
        String instanceString = this.openStackVolumePlugin.requestInstance(volumeOrder, this.openStackV3Token);

        // verify
        Mockito.verify(this.auditableHttpRequestClient).doGetRequest(Mockito.anyString(), Mockito.any(Token.class));
    }

    // test case: Tests if generateJsonEntityToCreateInstance is returning the volume Json properly.
    @Test
    public void testGenerateJsonEntityToCreateInstance() {
        // exercise
        String entity = this.openStackVolumePlugin.generateJsonEntityToCreateInstance(FAKE_SIZE, FAKE_NAME, FAKE_VOLUME_TYPE);
        JSONObject jsonEntity = new JSONObject(entity);

        // verify
        Assert.assertEquals(FAKE_SIZE, jsonEntity.getJSONObject(OpenstackRestApiConstants.Volume.VOLUME_KEY_JSON)
                .getString(OpenstackRestApiConstants.Volume.SIZE_KEY_JSON));
    }

    // test case: Tests if given a volume Json, the getInstanceFromJson() returns the right VolumeInstance.
    @Test
    public void testGetInstanceFromJson() throws FogbowRasException, JSONException, UnexpectedException {
        // exercise
        VolumeInstance instance = this.openStackVolumePlugin.getInstanceFromJson(FAKE_VOLUME_JSON);

        // verify
        Assert.assertEquals(FAKE_VOLUME_ID, instance.getId());
    }

    // test case: Check if the request in getInstance() is executed properly with the right parameters.
    @Test
    public void testGetInstance() throws UnexpectedException, FogbowRasException, HttpResponseException {
        // set up
        Mockito.doReturn(FAKE_VOLUME_JSON).when(
                this.auditableHttpRequestClient).doGetRequest(Mockito.anyString(), Mockito.any(Token.class));

        // exercise
        VolumeInstance volumeInstance = this.openStackVolumePlugin.getInstance(FAKE_INSTANCE_ID,
                this.openStackV3Token);

        // verify
        Mockito.verify(this.auditableHttpRequestClient).doGetRequest(Mockito.anyString(), Mockito.any(Token.class));
        Assert.assertEquals(FAKE_NAME, volumeInstance.getName());
        Assert.assertEquals(Integer.parseInt(FAKE_SIZE), volumeInstance.getVolumeSize());
    }

    // test case: Check if the request in deleteInstance() is executed properly with the right parameters.
    @Test
    public void removeInstance() throws UnexpectedException, FogbowRasException, HttpResponseException {
        // set up
        Mockito.doNothing().when(this.auditableHttpRequestClient).doDeleteRequest(Mockito.anyString(),
                Mockito.any(Token.class));

        // exercise
        this.openStackVolumePlugin.deleteInstance(FAKE_INSTANCE_ID, this.openStackV3Token);

        // verify
        Mockito.verify(this.auditableHttpRequestClient).doDeleteRequest(Mockito.anyString(), Mockito.any(Token.class));
    }

    // test case: Deleting an instance without a project ID must raise FogbowRasException.
    @Test(expected = FogbowRasException.class)
    public void testRemoveInstanceWithoutProjectId() throws Exception {
        // set up
        this.openStackV3Token.setProjectId(null);

        // exercise
        this.openStackVolumePlugin.deleteInstance(FAKE_INSTANCE_ID, this.openStackV3Token);
    }

    @Test
    public void getInstanceState() {
        // TODO
    }
}
