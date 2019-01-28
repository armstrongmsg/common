package cloud.fogbow.common.core.plugins.interoperability.openstack.compute.v2;

import com.google.gson.Gson;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import cloud.fogbow.common.core.PropertiesHolder;
import org.fogbowcloud.ras.core.exceptions.*;
import cloud.fogbow.common.core.models.ResourceType;
import cloud.fogbow.common.core.models.instances.ComputeInstance;
import cloud.fogbow.common.core.models.instances.InstanceState;
import cloud.fogbow.common.core.models.orders.ComputeOrder;
import org.fogbowcloud.ras.core.models.tokens.OpenStackV3Token;
import org.fogbowcloud.ras.core.models.tokens.Token;
import cloud.fogbow.common.core.plugins.interoperability.openstack.OpenStackStateMapper;
import cloud.fogbow.common.core.plugins.interoperability.openstack.network.v2.OpenStackNetworkPlugin;
import cloud.fogbow.common.core.plugins.interoperability.util.LaunchCommandGenerator;
import cloud.fogbow.common.util.connectivity.AuditableHttpRequestClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.IOException;
import java.util.*;

public class OpenStackComputePluginTest {

    private OpenStackComputePlugin computePlugin;
    private OpenStackV3Token openStackV3Token;
    private LaunchCommandGenerator launchCommandGeneratorMock;
    private AuditableHttpRequestClient auditableHttpRequestClientMock;
    private Properties propertiesMock;
    private PropertiesHolder propertiesHolderMock;
    private ArgumentCaptor<String> argString = ArgumentCaptor.forClass(String.class);
    private ArgumentCaptor<String> argBodyString = ArgumentCaptor.forClass(String.class);
    private ArgumentCaptor<Token> argToken = ArgumentCaptor.forClass(Token.class);
    private final String defaultNetworkId = "fake-default-network-id";
    private final String imageId = "image-id";
    private final String publicKey = "public-key";
    private final String idKeyName = "493315b3-dd01-4b38-974f-289570f8e7ee";
    private final String bestFlavorId = "best-flavor";
    private final int bestCpu = 2;
    private final int bestMemory = 1024;

    private static final String FAKE_TOKEN_PROVIDER = "fake-token-provider";
    private static final String FAKE_TOKEN_VALUE = "fake-token-value";
    private static final String FAKE_USER_ID = "fake-user-id";
    private static final String FAKE_NAME = "fake-name";
    private static final String FAKE_PROJECT_ID = "fake-project-id";

    private final int bestDisk = 8;
    private final String privateNetworkId = "fake-private-network-id";
    private final String userData = "userDataFromLauchCommand";
    private final JSONObject rootKeypairJson = generateRootKeyPairJson(idKeyName, publicKey);
    private final List<String> networksId = new ArrayList<String>();
    private final List<String> responseNetworkIds = new ArrayList<String>(networksId);
    private final String idInstanceName = "12345678-dd01-4b38-974f-289570f8e7ee";
    private final String expectedInstanceId = "instance-id-00";
    private final String expectedInstanceIdJson = generateInstaceId(expectedInstanceId);
    private final String localIpAddress = "localIpAddress";
    private final String instanceId = "compute-instance-id";
    private final String requirementTag = "besttag";
    private final String requirementValue = "bestvalue";
    private final int vCPU = 10;
    private final int ram = 15;
    private final int disk = 20;
    private String computeEndpoint;
    private String flavorEndpoint;
    private String flavorExtraSpecsEndpoint;
    private String osKeyPairEndpoint;
    private String hostName = "hostName";
    private String flavorId = "flavorId";
    private final String computeNovaV2UrlKey = "compute-nova-v2-url-key";
    private String openstackStateActive = OpenStackStateMapper.ACTIVE_STATUS;

    @Before
    public void setUp() throws Exception {
        this.propertiesHolderMock = Mockito.mock(PropertiesHolder.class);
        this.propertiesMock = Mockito.mock(Properties.class);
        this.auditableHttpRequestClientMock = Mockito.mock(AuditableHttpRequestClient.class);
        this.launchCommandGeneratorMock = Mockito.mock(LaunchCommandGenerator.class);
        this.networksId.add(privateNetworkId);
        this.responseNetworkIds.add(defaultNetworkId);
        this.responseNetworkIds.add(privateNetworkId);

        String tenantId = "tenant-id";
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(OpenStackComputePlugin.PROJECT_ID, tenantId);
        this.openStackV3Token = new OpenStackV3Token(FAKE_TOKEN_PROVIDER, FAKE_TOKEN_VALUE, FAKE_USER_ID, FAKE_NAME, FAKE_PROJECT_ID, null);

        this.computePlugin = Mockito.spy(new OpenStackComputePlugin(this.propertiesMock,
                this.launchCommandGeneratorMock, this.auditableHttpRequestClientMock));
        this.osKeyPairEndpoint = computeNovaV2UrlKey + OpenStackComputePlugin.COMPUTE_V2_API_ENDPOINT
                + this.openStackV3Token.getProjectId()
                + OpenStackComputePlugin.SUFFIX_ENDPOINT_KEYPAIRS;
        this.computeEndpoint = computeNovaV2UrlKey + OpenStackComputePlugin.COMPUTE_V2_API_ENDPOINT
                + this.openStackV3Token.getProjectId()
                + OpenStackComputePlugin.SERVERS;
        this.flavorEndpoint = this.computeNovaV2UrlKey + OpenStackComputePlugin.COMPUTE_V2_API_ENDPOINT
                + this.openStackV3Token.getProjectId()
                + OpenStackComputePlugin.SUFFIX_ENDPOINT_FLAVORS;

        this.flavorExtraSpecsEndpoint = this.computeNovaV2UrlKey + OpenStackComputePlugin.COMPUTE_V2_API_ENDPOINT
                + this.openStackV3Token.getProjectId()
                + OpenStackComputePlugin.SUFFIX_ENDPOINT_FLAVORS
                + "/%s"
                + OpenStackComputePlugin.SUFFIX_FLAVOR_EXTRA_SPECS;

        Mockito.when(this.propertiesMock.getProperty(OpenStackComputePlugin.COMPUTE_NOVAV2_URL_KEY))
                .thenReturn(this.computeNovaV2UrlKey);
        Mockito.when(this.propertiesMock.getProperty(OpenStackComputePlugin.DEFAULT_NETWORK_ID_KEY))
                .thenReturn(defaultNetworkId);
        Mockito.when(propertiesHolderMock.getProperties()).thenReturn(propertiesMock);
    }

    // test case: If a RequestInstance method works as expected
    @Test
    public void testRequestInstance() throws IOException, FogbowRasException, UnexpectedException {

        // set up
        mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
        ComputeOrder computeOrder = new ComputeOrder(null, null, null, "default", null,
                bestCpu, bestMemory, bestDisk, imageId, null, publicKey, networksId);
        JSONObject computeJson = generateJsonRequest(imageId, bestFlavorId, userData, idKeyName, responseNetworkIds,
                idInstanceName);
        Mockito.when(this.auditableHttpRequestClientMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
                this.argBodyString.capture())).thenReturn("");
        Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
        Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
        Mockito.doReturn(expectedInstanceIdJson).when(this.auditableHttpRequestClientMock).doPostRequest(argString.capture(),
                argToken.capture(), argBodyString.capture());

        // exercise
        String instanceId = this.computePlugin.requestInstance(computeOrder, this.openStackV3Token);

        // verify
        Assert.assertEquals(this.argString.getAllValues().get(0), this.osKeyPairEndpoint);
        Assert.assertEquals(this.argToken.getAllValues().get(0), this.openStackV3Token);
        JSONAssert.assertEquals(this.argBodyString.getAllValues().get(0).toString(), rootKeypairJson.toString(), false);

        Assert.assertEquals(this.argString.getAllValues().get(1), computeEndpoint);
        Assert.assertEquals(this.argToken.getAllValues().get(1), this.openStackV3Token);
        JSONAssert.assertEquals(this.argBodyString.getAllValues().get(1).toString(), computeJson.toString(), false);

        Assert.assertEquals(expectedInstanceId, instanceId);
    }

    // test case: create instance with extra requirement
    @Test
    public void testRequestInstanceWithRequirements() throws IOException, FogbowRasException, UnexpectedException {

        // set up
        mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
        ComputeOrder computeOrder = new ComputeOrder(null, null, null, "default", null,
                bestCpu, bestMemory, bestDisk, imageId, null, publicKey, networksId);
        Map<String, String> requirements = new HashMap<>();
        requirements.put(requirementTag, requirementValue);
        computeOrder.setRequirements(requirements);
        JSONObject computeJson = generateJsonRequest(imageId, bestFlavorId, userData, idKeyName, responseNetworkIds,
                idInstanceName);
        Mockito.when(this.auditableHttpRequestClientMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
                this.argBodyString.capture())).thenReturn("");
        Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
        Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
        Mockito.doReturn(expectedInstanceIdJson).when(this.auditableHttpRequestClientMock).doPostRequest(argString.capture(),
                argToken.capture(), argBodyString.capture());

        // exercise
        String instanceId = this.computePlugin.requestInstance(computeOrder, this.openStackV3Token);

        // verify
        Assert.assertEquals(this.argString.getAllValues().get(0), this.osKeyPairEndpoint);
        Assert.assertEquals(this.argToken.getAllValues().get(0), this.openStackV3Token);
        JSONAssert.assertEquals(this.argBodyString.getAllValues().get(0).toString(), rootKeypairJson.toString(), false);

        Assert.assertEquals(this.argString.getAllValues().get(1), computeEndpoint);
        Assert.assertEquals(this.argToken.getAllValues().get(1), this.openStackV3Token);
        JSONAssert.assertEquals(this.argBodyString.getAllValues().get(1).toString(), computeJson.toString(), false);

        Assert.assertEquals(expectedInstanceId, instanceId);
    }

    // test case: if there is no flavor available with all order requirements, raise exception
    @Test(expected = NoAvailableResourcesException.class)
    public void testRequestInstanceRequirementsNotFound() throws IOException, FogbowRasException, UnexpectedException {
        // set up
        mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
        ComputeOrder computeOrder = new ComputeOrder(null, null, null, "default", null,
                bestCpu, bestMemory, bestDisk, imageId, null, publicKey, networksId);
        Map<String, String> requirements = new HashMap<>();
        requirements.put(requirementTag, requirementValue);
        requirements.put("additionalReqTag", "additionalReqValue");
        computeOrder.setRequirements(requirements);
        Mockito.when(this.auditableHttpRequestClientMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
                this.argBodyString.capture())).thenReturn("");
        Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
        Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
        Mockito.doReturn(expectedInstanceIdJson).when(this.auditableHttpRequestClientMock).doPostRequest(argString.capture(),
                argToken.capture(), argBodyString.capture());

        // exercise
        String instanceId = this.computePlugin.requestInstance(computeOrder, this.openStackV3Token);
    }

    // test case: Check if a getInstance builds a compute instance from http response properly
    @Test
    public void testGetInstance() throws FogbowRasException, UnexpectedException, HttpResponseException {
        // set up
        InstanceState fogbowState = OpenStackStateMapper.map(ResourceType.COMPUTE, openstackStateActive);
        String newComputeEndpoint = this.computeEndpoint + "/" + instanceId;
        String computeInstanceJson = generateComputeInstanceJson(instanceId, hostName, localIpAddress, flavorId, openstackStateActive);
        List<String> ipAddresses = new ArrayList<>();
        ipAddresses.add(localIpAddress);

        ComputeInstance expectedComputeInstance = new ComputeInstance(instanceId, fogbowState, hostName, vCPU, ram, disk, ipAddresses);

        Mockito.when(this.auditableHttpRequestClientMock.doGetRequest(newComputeEndpoint, this.openStackV3Token)).thenReturn(computeInstanceJson);
        mockGetFlavorsRequest(flavorId, vCPU, ram, disk);

        // exercise
        ComputeInstance pluginComputeInstance = this.computePlugin.getInstance(instanceId, this.openStackV3Token);

        // verify
        Assert.assertEquals(expectedComputeInstance.getName(), pluginComputeInstance.getName());
        Assert.assertEquals(expectedComputeInstance.getId(), pluginComputeInstance.getId());
        Assert.assertEquals(expectedComputeInstance.getIpAddresses(), pluginComputeInstance.getIpAddresses());
        Assert.assertEquals(expectedComputeInstance.getDisk(), pluginComputeInstance.getDisk());
        Assert.assertEquals(expectedComputeInstance.getMemory(), pluginComputeInstance.getMemory());
        Assert.assertEquals(expectedComputeInstance.getState(), pluginComputeInstance.getState());
        Assert.assertEquals(expectedComputeInstance.getvCPU(), pluginComputeInstance.getvCPU());
    }

    // test case: If a DeleteInstance method works as expected
    @Test
    public void testDeleteInstance() throws HttpResponseException, FogbowRasException, UnexpectedException {
        // set up
        String deleteEndpoint = this.computeEndpoint + "/" + instanceId;
        Mockito.doNothing().when(this.auditableHttpRequestClientMock).doDeleteRequest(this.argString.capture(),
                this.argToken.capture());

        // exercise
        this.computePlugin.deleteInstance(instanceId, this.openStackV3Token);

        // verify
        Assert.assertEquals(this.argString.getValue(), deleteEndpoint);
        Assert.assertEquals(this.argToken.getValue(), this.openStackV3Token);
    }

    // test case: GetInstance should throw Unauthorized if a http request is Forbidden
    @Test(expected = UnauthorizedRequestException.class)
    public void testGetInstanceOnForbidden() throws FogbowRasException, UnexpectedException, HttpResponseException {
        // set up
        String newComputeEndpoint = this.computeEndpoint + "/" + instanceId;
        Mockito.when(this.auditableHttpRequestClientMock.doGetRequest(newComputeEndpoint, this.openStackV3Token))
                .thenThrow(new HttpResponseException(HttpStatus.SC_FORBIDDEN, ""));

        // exercise/verify
        this.computePlugin.getInstance(instanceId, this.openStackV3Token);
    }

    // test case: DeleteInstance should return Unauthorized is a http request is Forbidden
    @Test(expected = UnauthorizedRequestException.class)
    public void testDeleteInstanceTestOnForbidden() throws HttpResponseException, FogbowRasException, UnexpectedException {
        // set up
        String deleteEndpoint = this.computeEndpoint + "/" + instanceId;
        Mockito.doThrow(new HttpResponseException(HttpStatus.SC_FORBIDDEN, "")).when(this.auditableHttpRequestClientMock)
                .doDeleteRequest(deleteEndpoint, this.openStackV3Token);

        // exercise
        this.computePlugin.deleteInstance(instanceId, this.openStackV3Token);
    }

    // test case: Request Instance should throw Unauthenticated if a http request is Anauthorized
    @Test(expected = UnauthenticatedUserException.class)
    public void testRequestInstanceOnAnauthorizedComputePost() throws IOException, FogbowRasException, UnexpectedException {
        // set up
        ComputeOrder computeOrder = new ComputeOrder(null, null, null, "default", null, bestCpu, bestMemory, bestDisk, "", null, "",
                null);

        mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
        Mockito.when(this.auditableHttpRequestClientMock.doPostRequest(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn("");
        Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(Mockito.any())).thenReturn("");
        Mockito.when(this.auditableHttpRequestClientMock.doPostRequest(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new HttpResponseException(HttpStatus.SC_UNAUTHORIZED, ""));

        // exercise
        this.computePlugin.requestInstance(computeOrder, this.openStackV3Token);
    }

    // test case: RequestInstance should still work even if there is no public key as parameter
    @Test
    public void testRequestInstanceWhenPublicKeyIsNull() throws IOException, FogbowRasException, UnexpectedException {
        // set up
        String publicKey = null;
        String idKeyName = null;
        ComputeOrder computeOrder = new ComputeOrder(null, null, null, "default", null,
                bestCpu, bestMemory, bestDisk, imageId, null, publicKey, networksId);
        JSONObject computeJson = generateJsonRequest(imageId, bestFlavorId, userData, idKeyName, responseNetworkIds,
                idInstanceName);

        mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
        Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
        Mockito.doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
        Mockito.when(this.auditableHttpRequestClientMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
                this.argBodyString.capture())).thenReturn(expectedInstanceIdJson);

        // exercise
        String instanceId = this.computePlugin.requestInstance(computeOrder, this.openStackV3Token);

        // verify
        Assert.assertEquals(computeEndpoint, this.argString.getValue());
        Assert.assertEquals(this.openStackV3Token, this.argToken.getValue());
        JSONAssert.assertEquals(computeJson.toString(), this.argBodyString.getValue(), false);
        Assert.assertEquals(expectedInstanceId, instanceId);
    }

    // test case: RequestInstance should throw InvalidParameter when KeyName post returns Bad Request
    @Test(expected = InvalidParameterException.class)
    public void testRequestInstanceOnBadRequestKeyNamePost() throws IOException, FogbowRasException, UnexpectedException {
        // set up
        ComputeOrder computeOrder = new ComputeOrder(null, null, null,
                "default", null, bestCpu, bestMemory, bestDisk, imageId, null, publicKey, null);

        mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
        Mockito.when(this.auditableHttpRequestClientMock.doPostRequest(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new HttpResponseException(HttpStatus.SC_BAD_REQUEST, ""));
        Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
        Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();

        // exercise
        this.computePlugin.requestInstance(computeOrder, this.openStackV3Token);
    }

    // test case: Request Instance should throw Unauthenticated when delete key is Anauthorized
    @Test(expected = UnauthenticatedUserException.class)
    public void testRequestInstanceWhenDeleteKeyUnauthorized() throws IOException, FogbowRasException, UnexpectedException {
        // set up
        ComputeOrder computeOrder = new ComputeOrder(null, null, null,
                "default", null, bestCpu, bestMemory, bestDisk, imageId, null, publicKey, null);

        mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
        Mockito.when(this.auditableHttpRequestClientMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
                this.argString.capture())).thenReturn("");
        Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
        Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
        Mockito.when(this.auditableHttpRequestClientMock.doPostRequest(argString.capture(), argToken.capture(),
                argString.capture())).thenReturn(expectedInstanceIdJson);
        Mockito.doThrow(new HttpResponseException(HttpStatus.SC_UNAUTHORIZED, "")).when(this.auditableHttpRequestClientMock)
                .doDeleteRequest(Mockito.any(), Mockito.any());

        // exercise
        this.computePlugin.requestInstance(computeOrder, this.openStackV3Token);
    }

    // test case: test if Hardware Requirements caching works as expected
    @Test
    public void testRequestInstanceHardwareRequirementsCaching() throws IOException, FogbowRasException, UnexpectedException {
        // set up
        ComputeOrder computeOrder = new ComputeOrder(null, null, null,
                "default", null, bestCpu, bestMemory, bestDisk, imageId, null, publicKey, networksId);
        JSONObject computeJson = generateJsonRequest(imageId, bestFlavorId, userData, idKeyName, responseNetworkIds,
                idInstanceName);

        mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
        Mockito.when(this.auditableHttpRequestClientMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
                this.argBodyString.capture())).thenReturn("");
        Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
        Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
        Mockito.doReturn(expectedInstanceIdJson).when(this.auditableHttpRequestClientMock).doPostRequest(argString.capture(),
                argToken.capture(), argBodyString.capture());

        //exercise 1
        String instanceId = this.computePlugin.requestInstance(computeOrder, this.openStackV3Token);

        //verify 1
        Assert.assertEquals(this.argString.getAllValues().get(0), this.osKeyPairEndpoint);
        Assert.assertEquals(this.argToken.getAllValues().get(0), this.openStackV3Token);
        JSONAssert.assertEquals(this.argBodyString.getAllValues().get(0).toString(), rootKeypairJson.toString(), false);

        Assert.assertEquals(this.argString.getAllValues().get(1), computeEndpoint);
        Assert.assertEquals(this.argToken.getAllValues().get(1), this.openStackV3Token);
        JSONAssert.assertEquals(this.argBodyString.getAllValues().get(1).toString(), computeJson.toString(), false);

        Assert.assertEquals(expectedInstanceId, instanceId);

        // exercise 2
        instanceId = this.computePlugin.requestInstance(computeOrder, this.openStackV3Token);

        // verify 2
        Assert.assertEquals(this.argString.getAllValues().get(0), this.osKeyPairEndpoint);
        Assert.assertEquals(this.argToken.getAllValues().get(0), this.openStackV3Token);
        JSONAssert.assertEquals(this.argBodyString.getAllValues().get(0).toString(), rootKeypairJson.toString(), false);

        Assert.assertEquals(this.argString.getAllValues().get(1), computeEndpoint);
        Assert.assertEquals(this.argToken.getAllValues().get(1), this.openStackV3Token);
        JSONAssert.assertEquals(this.argBodyString.getAllValues().get(1).toString(), computeJson.toString(), false);

        Assert.assertEquals(expectedInstanceId, instanceId);
    }

    // test case: Get Instance should throw NoAvailableResources when compute flavor id from request is not stored locally so that
    // we can't get memory, cpu and disk information
    @Test(expected = NoAvailableResourcesException.class)
    public void testGetInstanceWhenThereIsNoFlavorIdOnGetFlavorById() throws FogbowRasException, UnexpectedException, HttpResponseException {
        // set up
        String newComputeEndpoint = this.computeEndpoint + "/" + instanceId;
        String computeInstanceJson = generateComputeInstanceJson(instanceId, hostName, localIpAddress, flavorId,
                openstackStateActive);
        Mockito.when(this.auditableHttpRequestClientMock.doGetRequest(newComputeEndpoint, this.openStackV3Token))
                .thenReturn(computeInstanceJson);
        mockGetFlavorsRequest(flavorId + "wrong", vCPU, ram, disk);

        // exercise
        this.computePlugin.getInstance(instanceId, this.openStackV3Token);
    }

    // test case: Get Instance should still work even if there is no address field on response
    @Test
    public void testGetInstanceWhenThereIsNoAddressFieldOnResponse() throws FogbowRasException, UnexpectedException, HttpResponseException {
        // set up
        InstanceState fogbowState = OpenStackStateMapper.map(ResourceType.COMPUTE, openstackStateActive);
        String localIpAddress = null;
        String newComputeEndpoint = this.computeEndpoint + "/" + instanceId;
        ComputeInstance expectedComputeInstance = new ComputeInstance(instanceId, fogbowState, hostName, vCPU, ram, disk, new ArrayList());
        String computeInstanceJson = generateComputeInstanceJsonWithoutAddressField(instanceId, hostName, localIpAddress, flavorId, openstackStateActive);
        Mockito.when(this.auditableHttpRequestClientMock.doGetRequest(newComputeEndpoint, this.openStackV3Token)).thenReturn(computeInstanceJson);
        mockGetFlavorsRequest(flavorId, vCPU, ram, disk);

        // exercise
        ComputeInstance pluginComputeInstance = this.computePlugin.getInstance(instanceId, this.openStackV3Token);

        // verify
        Assert.assertEquals(expectedComputeInstance.getName(), pluginComputeInstance.getName());
        Assert.assertEquals(expectedComputeInstance.getId(), pluginComputeInstance.getId());
        Assert.assertEquals(expectedComputeInstance.getIpAddresses(), pluginComputeInstance.getIpAddresses());
        Assert.assertEquals(expectedComputeInstance.getDisk(), pluginComputeInstance.getDisk());
        Assert.assertEquals(expectedComputeInstance.getMemory(), pluginComputeInstance.getMemory());
        Assert.assertEquals(expectedComputeInstance.getState(), pluginComputeInstance.getState());
        Assert.assertEquals(expectedComputeInstance.getvCPU(), pluginComputeInstance.getvCPU());
    }

    // test case: Get Instance should still work even if there is no provider network field on response
    @Test
    public void testGetInstanceWhenThereIsNoProviderNetworkFieldOnResponse() throws FogbowRasException, UnexpectedException, HttpResponseException {
        // set up
        InstanceState fogbowState = OpenStackStateMapper.map(ResourceType.COMPUTE, openstackStateActive);
        String newComputeEndpoint = this.computeEndpoint + "/" + instanceId;
        String computeInstanceJson = generateComputeInstanceJsonWithoutProviderNetworkField(instanceId, hostName, localIpAddress, flavorId, openstackStateActive);
        ComputeInstance expectedComputeInstance = new ComputeInstance(instanceId, fogbowState, hostName, vCPU, ram, disk, new ArrayList());

        Mockito.when(this.auditableHttpRequestClientMock.doGetRequest(newComputeEndpoint, this.openStackV3Token)).thenReturn(computeInstanceJson);
        mockGetFlavorsRequest(flavorId, vCPU, ram, disk);

        // exercise
        ComputeInstance pluginComputeInstance = this.computePlugin.getInstance(instanceId, this.openStackV3Token);

        // verify
        Assert.assertEquals(expectedComputeInstance.getName(), pluginComputeInstance.getName());
        Assert.assertEquals(expectedComputeInstance.getId(), pluginComputeInstance.getId());
        Assert.assertEquals(expectedComputeInstance.getIpAddresses(), pluginComputeInstance.getIpAddresses());
        Assert.assertEquals(expectedComputeInstance.getDisk(), pluginComputeInstance.getDisk());
        Assert.assertEquals(expectedComputeInstance.getMemory(), pluginComputeInstance.getMemory());
        Assert.assertEquals(expectedComputeInstance.getState(), pluginComputeInstance.getState());
        Assert.assertEquals(expectedComputeInstance.getvCPU(), pluginComputeInstance.getvCPU());
    }

    // test case: Request Instance should throw NoAvailableResources when there is no cpu that meets the criteria
    @Test(expected = NoAvailableResourcesException.class)
    public void testRequestInstanceWhenThereIsNoFlavorAvailableForCPU() throws IOException, FogbowRasException, UnexpectedException {
        // set up
        int worst = -1;
        ComputeOrder computeOrder = new ComputeOrder(null, null, null, "default", null, bestCpu, bestMemory, bestDisk, imageId, null,
                publicKey, null);

        mockGetFlavorsRequest(bestFlavorId, bestCpu + worst, bestMemory, bestDisk);
        Mockito.when(this.auditableHttpRequestClientMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
                this.argString.capture())).thenReturn("");
        Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
        Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
        Mockito.doReturn(expectedInstanceIdJson).when(this.auditableHttpRequestClientMock).doPostRequest(argString.capture(),
                argToken.capture(), argString.capture());

        // exercise
        this.computePlugin.requestInstance(computeOrder, this.openStackV3Token);
    }

    // test case: Request Instance should throw NoAvailableResources when there is no memory that meets the criteria
    @Test(expected = NoAvailableResourcesException.class)
    public void testRequestInstanceWhenThereIsNoFlavorAvailableForMemory() throws IOException, FogbowRasException, UnexpectedException {
        // set up
        int worst = -1;
        ComputeOrder computeOrder = new ComputeOrder(null, null, null, "default", null, bestCpu, bestMemory, bestDisk, imageId, null,
                publicKey, null);

        mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory + worst, bestDisk);
        Mockito.when(this.auditableHttpRequestClientMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
                this.argString.capture())).thenReturn("");
        Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
        Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
        Mockito.doReturn(expectedInstanceIdJson).when(this.auditableHttpRequestClientMock).doPostRequest(argString.capture(),
                argToken.capture(), argString.capture());

        // exercise
        this.computePlugin.requestInstance(computeOrder, this.openStackV3Token);
    }

    // test case: Request Instance should throw NoAvailableResources when there is no disk that meets the criteria
    @Test(expected = NoAvailableResourcesException.class)
    public void testRequestInstanceWhenThereIsNoFlavorAvailableForDisk() throws IOException, FogbowRasException, UnexpectedException {
        // set up
        int worst = -1;
        ComputeOrder computeOrder = new ComputeOrder(null, null, null, "default", null, bestCpu, bestMemory, bestDisk, imageId, null,
                publicKey, null);

        mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk + worst);
        Mockito.when(this.auditableHttpRequestClientMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
                this.argString.capture())).thenReturn("");
        Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
        Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
        Mockito.doReturn(expectedInstanceIdJson).when(this.auditableHttpRequestClientMock).doPostRequest(argString.capture(),
                argToken.capture(), argString.capture());

        // exercise
        this.computePlugin.requestInstance(computeOrder, this.openStackV3Token);
    }

    // test case: Request Instance should still work even if there is no user data (null)
    @Test
    public void testRequestInstanceWhenThereIsNoUserData() throws IOException, FogbowRasException, UnexpectedException {
        // set up
        ComputeOrder computeOrder = new ComputeOrder(null, null, null, "default", null, bestCpu, bestMemory, bestDisk, imageId, null,
                publicKey, networksId);
        mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
        Mockito.when(this.auditableHttpRequestClientMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
                this.argBodyString.capture())).thenReturn("");
        Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
        Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
        JSONObject computeJson = generateJsonRequest(imageId, bestFlavorId, userData, idKeyName, responseNetworkIds,
                idInstanceName);
        Mockito.doReturn(expectedInstanceIdJson).when(this.auditableHttpRequestClientMock).doPostRequest(argString.capture(),
                argToken.capture(), argBodyString.capture());

        // exercise
        String instanceId = this.computePlugin.requestInstance(computeOrder, this.openStackV3Token);

        // verify
        Assert.assertEquals(this.osKeyPairEndpoint, this.argString.getAllValues().get(0));
        Assert.assertEquals(this.openStackV3Token, this.argToken.getAllValues().get(0));
        JSONAssert.assertEquals(rootKeypairJson.toString(), this.argBodyString.getAllValues().get(0).toString(), false);

        Assert.assertEquals(computeEndpoint, this.argString.getAllValues().get(1));
        Assert.assertEquals(this.openStackV3Token, this.argToken.getAllValues().get(1));
        JSONAssert.assertEquals(computeJson.toString(), this.argBodyString.getAllValues().get(1).toString(), false);

        Assert.assertEquals(expectedInstanceId, instanceId);
    }

    // test case: Compute networksId should always contain a default network id even if there is no network id in a compute order
    @Test
    public void testRequestInstanceWhenThereIsNoNetworkId() throws IOException, FogbowRasException, UnexpectedException {
        // set up
        List<String> networksId = null;
        ComputeOrder computeOrder = new ComputeOrder(null, null, null, "default", null, bestCpu, bestMemory, bestDisk, imageId, null,
                publicKey, networksId);
        responseNetworkIds.remove(this.privateNetworkId);

        JSONObject computeJson = generateJsonRequest(imageId, bestFlavorId, userData, idKeyName, responseNetworkIds,
                idInstanceName);

        mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
        Mockito.when(this.auditableHttpRequestClientMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
                this.argBodyString.capture())).thenReturn("");
        Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
        Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
        Mockito.doReturn(expectedInstanceIdJson).when(this.auditableHttpRequestClientMock).doPostRequest(argString.capture(),
                argToken.capture(), argBodyString.capture());

        // exercise
        String instanceId = this.computePlugin.requestInstance(computeOrder, this.openStackV3Token);

        // verify
        Assert.assertEquals(this.osKeyPairEndpoint, this.argString.getAllValues().get(0));
        Assert.assertEquals(this.openStackV3Token, this.argToken.getAllValues().get(0));
        JSONAssert.assertEquals(rootKeypairJson.toString(), this.argBodyString.getAllValues().get(0).toString(), false);

        Assert.assertEquals(computeEndpoint, this.argString.getAllValues().get(1));
        Assert.assertEquals(this.openStackV3Token, this.argToken.getAllValues().get(1));
        JSONAssert.assertEquals(computeJson.toString(), this.argBodyString.getAllValues().get(1).toString(), false);

        Assert.assertEquals(expectedInstanceId, instanceId);
    }

    // test case: Request Instance should throw InvalidParameter if the Get Request on updateFlavors returns Bad Request
    // while getting flavors id
    @Test(expected = InvalidParameterException.class)
    public void testRequestInstanceWhenFlavorsIdRequestException() throws HttpResponseException, FogbowRasException, UnexpectedException {
        // set up
        ComputeOrder computeOrder = new ComputeOrder(null, null, null, "default", null, bestCpu, bestMemory, bestDisk, imageId, null,
                publicKey, networksId);
        Mockito.when(this.auditableHttpRequestClientMock.doGetRequest(Mockito.any(), Mockito.any()))
                .thenThrow(new HttpResponseException(HttpStatus.SC_BAD_REQUEST, ""));
        Mockito.when(this.auditableHttpRequestClientMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
                this.argString.capture())).thenReturn("");
        Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
        Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
        Mockito.doReturn(expectedInstanceIdJson).when(this.auditableHttpRequestClientMock).doPostRequest(argString.capture(),
                argToken.capture(), argString.capture());

        // exercise
        this.computePlugin.requestInstance(computeOrder, this.openStackV3Token);
    }

    // test case: Request Instance should throw InvalidParameter if the Get Request on updateFlavors returns Bad Request
    // while getting flavor specification
    @Test(expected = InvalidParameterException.class)
    public void testRequestInstanceWhenSpecificFlavorRequestException() throws IOException, FogbowRasException, UnexpectedException {
        // set up
        String newEndpoint = flavorEndpoint + "/" + bestFlavorId;
        ComputeOrder computeOrder = new ComputeOrder(null, null, null, "default", null, bestCpu, bestMemory, bestDisk, imageId, null,
                publicKey, networksId);
        mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
        Mockito.doThrow(new HttpResponseException(HttpStatus.SC_BAD_REQUEST, "")).when(this.auditableHttpRequestClientMock)
                .doGetRequest(newEndpoint, this.openStackV3Token);

        Mockito.when(this.auditableHttpRequestClientMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
                this.argString.capture())).thenReturn("");
        Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
        Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
        Mockito.doReturn(expectedInstanceIdJson).when(this.auditableHttpRequestClientMock).doPostRequest(argString.capture(),
                argToken.capture(), argString.capture());

        // exercise
        this.computePlugin.requestInstance(computeOrder, this.openStackV3Token);
    }

    /*
     * This method mocks the behavior of a http flavor request by mocking GET"/flavors" and GET"/flavors/id" and adds
     * bestFlavorId as a flavor from this response in addition to other flavors. Besides that, bestFlavorId will be
     * the flavor with best Vcpu, memory and disk from this response. 
     */
    private void mockGetFlavorsRequest(String bestFlavorId, int bestVcpu, int bestMemory, int bestDisk) throws HttpResponseException, UnavailableProviderException {

        int qtdFlavors = 100;

        Mockito.when(this.auditableHttpRequestClientMock.doGetRequest(flavorEndpoint, this.openStackV3Token))
                .thenReturn(generateJsonFlavors(qtdFlavors, bestFlavorId));

        for (int i = 0; i < qtdFlavors - 1; i++) {
            String flavorId = "flavor" + Integer.toString(i);
            String newEndpoint = flavorEndpoint + "/" + flavorId;
            String newSpecsEndpoint = String.format(this.flavorExtraSpecsEndpoint, flavorId);
            String flavorJson = generateJsonFlavor(
                    flavorId,
                    "nameflavor" + Integer.toString(i),
                    Integer.toString(Math.max(1, bestVcpu - 1 - i)),
                    Integer.toString(Math.max(1, bestMemory - 1 - i)),
                    Integer.toString(Math.max(1, bestDisk - 1 - i)));
            String flavorSpecJson = generateJsonFlavorExtraSpecs("tag" + i, "value" + i);
            Mockito.when(this.auditableHttpRequestClientMock.doGetRequest(newEndpoint, this.openStackV3Token))
                    .thenReturn(flavorJson);
            // Mocks /flavors/{flavor_id}/os-extra_specs
            Mockito.when(this.auditableHttpRequestClientMock.doGetRequest(newSpecsEndpoint, this.openStackV3Token))
                    .thenReturn(flavorSpecJson);
        }

        String newEndpoint = flavorEndpoint + "/" + bestFlavorId;
        String flavorJson = generateJsonFlavor(
                bestFlavorId,
                "nameflavor" + bestFlavorId,
                Integer.toString(bestVcpu),
                Integer.toString(bestMemory),
                Integer.toString(bestDisk));
        String bestflavorSpecJson = generateJsonFlavorExtraSpecs(this.requirementTag, this.requirementValue);
        String newSpecsEndpoint = String.format(this.flavorExtraSpecsEndpoint, bestFlavorId);

        Mockito.when(this.auditableHttpRequestClientMock.doGetRequest(newEndpoint, this.openStackV3Token))
                .thenReturn(flavorJson);
        Mockito.when(this.auditableHttpRequestClientMock.doGetRequest(newSpecsEndpoint, this.openStackV3Token))
                .thenReturn(bestflavorSpecJson);
    }

    //mocks GET"/flavors"
    private String generateJsonFlavors(int qtd, String bestFlavorId) {
        Map<String, Object> jsonFlavorsMap = new HashMap<String, Object>();
        List<Map<String, String>> jsonArrayFlavors = new ArrayList<Map<String, String>>();
        for (int i = 0; i < qtd - 1; i++) {
            Map<String, String> flavor = new HashMap<String, String>();
            flavor.put(OpenStackComputePlugin.ID_JSON_FIELD, "flavor" + Integer.toString(i));
            jsonArrayFlavors.add(flavor);
        }

        Map<String, String> flavor = new HashMap<String, String>();
        flavor.put(OpenStackComputePlugin.ID_JSON_FIELD, bestFlavorId);
        jsonArrayFlavors.add(flavor);

        jsonFlavorsMap.put(OpenStackComputePlugin.FLAVOR_JSON_KEY, jsonArrayFlavors);
        Gson gson = new Gson();
        return gson.toJson(jsonFlavorsMap);
    }

    private String generateJsonFlavor(String id, String name, String vcpu, String memory, String disk) {
        Map<String, Object> flavorMap = new HashMap<String, Object>();
        Map<String, String> flavorAttributes = new HashMap<String, String>();
        flavorAttributes.put(OpenStackComputePlugin.ID_JSON_FIELD, id);
        flavorAttributes.put(OpenStackComputePlugin.NAME_JSON_FIELD, name);
        flavorAttributes.put(OpenStackComputePlugin.DISK_JSON_FIELD, disk);
        flavorAttributes.put(OpenStackComputePlugin.MEMORY_JSON_FIELD, memory);
        flavorAttributes.put(OpenStackComputePlugin.VCPU_JSON_FIELD, vcpu);
        flavorMap.put(OpenStackComputePlugin.FLAVOR_JSON_OBJECT, flavorAttributes);
        return new Gson().toJson(flavorMap);
    }

    private String generateJsonFlavorExtraSpecs(String tag, String value) {
        Map<String, Object> flavorExtraSpecsMap = new HashMap<String, Object>();
        Map<String, String> flavorExtraSpecs = new HashMap<String, String>();
        flavorExtraSpecs.put(tag, value);
        flavorExtraSpecsMap.put(OpenStackComputePlugin.FLAVOR_EXTRA_SPECS_JSON_FIELD, flavorExtraSpecs);
        return new Gson().toJson(flavorExtraSpecsMap);
    }

    private String generateInstaceId(String id) {
        Map<String, String> instanceMap = new HashMap<String, String>();
        instanceMap.put(OpenStackComputePlugin.ID_JSON_FIELD, id);
        Map<String, Object> root = new HashMap<String, Object>();
        root.put(OpenStackComputePlugin.SERVER_JSON_FIELD, instanceMap);
        return new Gson().toJson(root);
    }

    private JSONObject generateRootKeyPairJson(String keyName, String publicKey) {
        JSONObject keypair = new JSONObject();
        keypair.put(OpenStackComputePlugin.NAME_JSON_FIELD, keyName);
        keypair.put(OpenStackComputePlugin.PUBLIC_KEY_JSON_FIELD, publicKey);
        JSONObject root = new JSONObject();
        root.put(OpenStackComputePlugin.KEYPAIR_JSON_FIELD, keypair);
        return root;
    }

    private JSONObject generateJsonRequest(String imageId, String flavorId, String userData, String keyName, List<String> networkIds, String randomUUID) {
        JSONObject server = new JSONObject();

        // when the user doesn't provide a network, the default network should be added in the request
        if (networkIds.isEmpty()) {
            networkIds.add(defaultNetworkId);
        }

        server.put(OpenStackComputePlugin.NAME_JSON_FIELD, OpenStackComputePlugin.FOGBOW_INSTANCE_NAME + randomUUID);
        server.put(OpenStackComputePlugin.IMAGE_JSON_FIELD, imageId);
        server.put(OpenStackComputePlugin.FLAVOR_REF_JSON_FIELD, flavorId);
        server.put(OpenStackComputePlugin.USER_DATA_JSON_FIELD, userData);

        JSONArray networks = new JSONArray();

        for (String id : networkIds) {
            JSONObject netId = new JSONObject();
            netId.put(OpenStackComputePlugin.UUID_JSON_FIELD, id);
            networks.put(netId);
        }
        server.put(OpenStackComputePlugin.NETWORK_JSON_FIELD, networks);

        if (networkIds.size() > 0) {
            for (String networkId : networkIds) {
                if (!networkId.equals(defaultNetworkId)) {
                    JSONArray securityGroups = new JSONArray();
                    JSONObject securityGroup = new JSONObject();
                    String securityGroupName = OpenStackNetworkPlugin.getSGNameForPrivateNetwork(networkId);
                    securityGroup.put(OpenStackComputePlugin.NAME_JSON_FIELD, securityGroupName);
                    securityGroups.put(securityGroup);
                    server.put(OpenStackComputePlugin.SECURITY_JSON_FIELD, securityGroups);
                }
            }
        }

        if (keyName != null) {
            server.put(OpenStackComputePlugin.KEY_JSON_FIELD, keyName);
        }

        JSONObject root = new JSONObject();
        root.put(OpenStackComputePlugin.SERVER_JSON_FIELD, server);
        return root;
    }


    private String generateComputeInstanceJson(String instanceId, String hostName, String localIpAddress, String flavorId, String status) {
        Map<String, Object> root = new HashMap<String, Object>();
        Map<String, Object> computeInstance = new HashMap<String, Object>();
        computeInstance.put(OpenStackComputePlugin.ID_JSON_FIELD, instanceId);
        computeInstance.put(OpenStackComputePlugin.NAME_JSON_FIELD, hostName);

        Map<String, Object> addressField = new HashMap<String, Object>();
        List<Object> providerNetworkArray = new ArrayList<Object>();
        Map<String, String> providerNetwork = new HashMap<String, String>();
        providerNetwork.put(OpenStackComputePlugin.ADDR_FIELD, localIpAddress);
        providerNetworkArray.add(providerNetwork);
        addressField.put(OpenStackComputePlugin.PROVIDER_NETWORK_FIELD, providerNetworkArray);

        Map<String, Object> flavor = new HashMap<String, Object>();
        flavor.put(OpenStackComputePlugin.FLAVOR_ID_JSON_FIELD, flavorId);

        computeInstance.put(OpenStackComputePlugin.FLAVOR_JSON_FIELD, flavor);
        computeInstance.put(OpenStackComputePlugin.ADDRESS_FIELD, addressField);
        computeInstance.put(OpenStackComputePlugin.STATUS_JSON_FIELD, status);

        root.put(OpenStackComputePlugin.SERVER_JSON_FIELD, computeInstance);
        return new Gson().toJson(root);
    }

    private String generateComputeInstanceJsonWithoutAddressField(String instanceId, String hostName, String localIpAddress, String flavorId, String status) {
        Map<String, Object> root = new HashMap<String, Object>();
        Map<String, Object> computeInstance = new HashMap<String, Object>();
        computeInstance.put(OpenStackComputePlugin.ID_JSON_FIELD, instanceId);
        computeInstance.put(OpenStackComputePlugin.NAME_JSON_FIELD, hostName);

        List<Object> providerNetworkArray = new ArrayList<Object>();
        Map<String, String> providerNetwork = new HashMap<String, String>();
        providerNetwork.put(OpenStackComputePlugin.ADDR_FIELD, localIpAddress);
        providerNetworkArray.add(providerNetwork);

        Map<String, Object> flavor = new HashMap<String, Object>();
        flavor.put(OpenStackComputePlugin.FLAVOR_ID_JSON_FIELD, flavorId);

        computeInstance.put(OpenStackComputePlugin.FLAVOR_JSON_FIELD, flavor);
        computeInstance.put(OpenStackComputePlugin.STATUS_JSON_FIELD, status);

        root.put(OpenStackComputePlugin.SERVER_JSON_FIELD, computeInstance);
        return new Gson().toJson(root);
    }

    private String generateComputeInstanceJsonWithoutProviderNetworkField(String instanceId, String hostName, String localIpAddress, String flavorId, String status) {
        Map<String, Object> root = new HashMap<String, Object>();
        Map<String, Object> computeInstance = new HashMap<String, Object>();
        computeInstance.put(OpenStackComputePlugin.ID_JSON_FIELD, instanceId);
        computeInstance.put(OpenStackComputePlugin.NAME_JSON_FIELD, hostName);

        Map<String, Object> addressField = new HashMap<String, Object>();

        Map<String, Object> flavor = new HashMap<String, Object>();
        flavor.put(OpenStackComputePlugin.FLAVOR_ID_JSON_FIELD, flavorId);

        computeInstance.put(OpenStackComputePlugin.FLAVOR_JSON_FIELD, flavor);
        computeInstance.put(OpenStackComputePlugin.ADDRESS_FIELD, addressField);
        computeInstance.put(OpenStackComputePlugin.STATUS_JSON_FIELD, status);

        root.put(OpenStackComputePlugin.SERVER_JSON_FIELD, computeInstance);
        return new Gson().toJson(root);
    }
}
