package cloud.fogbow.common.core.intercomponent;

import cloud.fogbow.common.core.BaseUnitTests;
import cloud.fogbow.common.core.OrderController;
import cloud.fogbow.common.core.OrderStateTransitioner;
import cloud.fogbow.common.core.PropertiesHolder;
import org.fogbowcloud.ras.core.*;
import cloud.fogbow.common.core.cloudconnector.CloudConnector;
import cloud.fogbow.common.core.cloudconnector.CloudConnectorFactory;
import cloud.fogbow.common.core.cloudconnector.RemoteCloudConnector;
import cloud.fogbow.common.core.constants.ConfigurationConstants;
import cloud.fogbow.common.core.models.Operation;
import cloud.fogbow.common.core.constants.SystemConstants;
import cloud.fogbow.common.core.datastore.DatabaseManager;
import cloud.fogbow.common.core.intercomponent.xmpp.PacketSenderHolder;
import cloud.fogbow.common.core.models.ResourceType;
import cloud.fogbow.common.core.models.instances.ComputeInstance;
import cloud.fogbow.common.core.models.instances.Instance;
import cloud.fogbow.common.core.models.orders.ComputeOrder;
import cloud.fogbow.common.core.models.orders.Order;
import cloud.fogbow.common.core.models.orders.OrderState;
import cloud.fogbow.common.core.models.quotas.ComputeQuota;
import cloud.fogbow.common.core.models.quotas.Quota;
import cloud.fogbow.common.core.models.quotas.allocation.ComputeAllocation;
import cloud.fogbow.common.models.FederationUser;
import cloud.fogbow.common.core.plugins.interoperability.genericrequest.GenericRequest;
import cloud.fogbow.common.core.plugins.interoperability.genericrequest.GenericRequestResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.xmpp.packet.IQ;

import java.util.ArrayList;

@RunWith(PowerMockRunner.class)
@PrepareForTest({DatabaseManager.class, CloudConnectorFactory.class, PacketSenderHolder.class})
public class RemoteFacadeTest extends BaseUnitTests {

    private static final String FAKE_INSTANCE_ID = "fake-instance-id";
    private static final String REQUESTING_MEMBER_ID = "fake-requesting-member";
    private static final String CLOUD_NAME = "default";

    private AaaController aaaController;
    private OrderController orderController;
    private RemoteFacade remoteFacade;
    private CloudConnector cloudConnector;

    @Before
    public void setUp() throws UnexpectedException {
        super.mockReadOrdersFromDataBase();

        this.orderController = Mockito.spy(new OrderController());
        this.aaaController = Mockito.mock(AaaController.class);

        this.remoteFacade = RemoteFacade.getInstance();
        this.remoteFacade.setOrderController(this.orderController);
        this.remoteFacade.setAaaController(this.aaaController);

        String aaaConfFilePath = HomeDir.getPath() + SystemConstants.AAA_CONF_FILE_NAME;
        AaaPluginsHolder aaaPluginsHolder = new AaaPluginsHolder();
        aaaPluginsHolder.setTokenGeneratorPlugin(AaaPluginInstantiator.getTokenGeneratorPlugin(aaaConfFilePath));
        aaaPluginsHolder.setFederationIdentityPlugin(AaaPluginInstantiator.getFederationIdentityPlugin(aaaConfFilePath));
        aaaPluginsHolder.setAuthenticationPlugin(AaaPluginInstantiator.getAuthenticationPlugin(aaaConfFilePath));
        aaaPluginsHolder.setAuthorizationPlugin(AaaPluginInstantiator.getAuthorizationPlugin(aaaConfFilePath));

        this.cloudConnector = Mockito.spy(new RemoteCloudConnector(REQUESTING_MEMBER_ID, CLOUD_NAME));
    }

    // test case: When calling the activateOrder method a new Order without state passed by
    // parameter, it must return to Open OrderState after its activation.
    @Test
    public void testRemoteActivateOrder() throws FogbowRasException, UnexpectedException {
        // set up
        FederationUser federationUser = createFederationUser();
        Order order = createOrder(federationUser);
        Assert.assertNull(order.getOrderState());

        Mockito.doNothing().when(this.aaaController).authorize(Mockito.anyString(), Mockito.eq(federationUser),
                Mockito.eq(Operation.CREATE), Mockito.eq(ResourceType.COMPUTE));

        // exercise
        this.remoteFacade.activateOrder(REQUESTING_MEMBER_ID, order);

        // verify
        Assert.assertEquals(OrderState.OPEN, order.getOrderState());
    }

    // test case: When calling the getResourceInstance method, it must return an Instance of the
    // OrderID passed per parameter.
    @Test
    @Ignore
    public void testRemoteGetResourceInstance() throws Exception {
        // set up
        FederationUser federationUser = createFederationUser();
        Order order = createOrder(federationUser);

        Mockito.doNothing().when(this.aaaController).authorize("default", Mockito.eq(federationUser),
                Mockito.eq(Operation.CREATE), Mockito.eq(ResourceType.COMPUTE));

        Instance excepted = new ComputeInstance(FAKE_INSTANCE_ID);

        Mockito.doReturn(excepted).when(this.orderController)
                .getResourceInstance(Mockito.eq(order.getId()));

        // exercise
        Instance instance = this.remoteFacade.getResourceInstance(REQUESTING_MEMBER_ID, order.getId(), federationUser,
                ResourceType.COMPUTE);

        // verify
        Mockito.verify(this.orderController, Mockito.times(1))
                .getResourceInstance(Mockito.eq(order.getId()));

        Assert.assertSame(excepted, instance);
    }

    // test case: When calling the deleteOrder method with an Order passed as parameter, it must
    // return its OrderState to Closed.
    @Ignore
    @Test
    public void testRemoteDeleteOrder() throws Exception {
        // set up
        FederationUser federationUser = createFederationUser();
        Order order = createOrder(federationUser);
        OrderStateTransitioner.activateOrder(order);

        CloudConnectorFactory cloudConnectorFactory = Mockito.mock(CloudConnectorFactory.class);
        Mockito.when(cloudConnectorFactory.getCloudConnector(Mockito.anyString(), "default"))
                .thenReturn(this.cloudConnector);
        IQ response = new IQ();

        Mockito.doNothing().when(this.aaaController).authorize("default", Mockito.eq(federationUser),
                Mockito.eq(Operation.CREATE), Mockito.eq(ResourceType.COMPUTE));

        // exercise
        this.remoteFacade.deleteOrder(REQUESTING_MEMBER_ID, order.getId(), federationUser, ResourceType.COMPUTE);

        // verify
        Mockito.verify(this.aaaController, Mockito.times(1)).authorize(CLOUD_NAME,
                Mockito.any(FederationUser.class), Mockito.any(Operation.class),
                Mockito.any(ResourceType.class));

        Assert.assertEquals(OrderState.CLOSED, order.getOrderState());
    }

    // test case: When calling the getUserQuota method with valid parameters, it must return the
    // User Quota from that.
    @Test
    @Ignore
    public void testRemoteGetUserQuota() throws Exception {
        // set up
        FederationUser federationUser = createFederationUser();

        Mockito.doNothing().when(this.aaaController).authorize(CLOUD_NAME, Mockito.eq(federationUser),
                Mockito.eq(Operation.CREATE), Mockito.eq(ResourceType.COMPUTE));

        CloudConnectorFactory cloudConnectorFactory = Mockito.mock(CloudConnectorFactory.class);

        PowerMockito.mockStatic(CloudConnectorFactory.class);
        BDDMockito.given(CloudConnectorFactory.getInstance()).willReturn(cloudConnectorFactory);

        Mockito.when(cloudConnectorFactory.getCloudConnector(REQUESTING_MEMBER_ID, CLOUD_NAME))
                .thenReturn(this.cloudConnector);

        ComputeAllocation totalQuota = new ComputeAllocation(8, 2048, 2);
        ComputeAllocation usedQuota = new ComputeAllocation(4, 1024, 1);

        Quota quota = new ComputeQuota(totalQuota, usedQuota);

        Mockito.doReturn(quota).when(this.cloudConnector).getUserQuota(Mockito.eq(federationUser),
                Mockito.eq(ResourceType.COMPUTE));

        // exercise
        Quota expected = this.remoteFacade.getUserQuota(REQUESTING_MEMBER_ID, CLOUD_NAME, federationUser,
                ResourceType.COMPUTE);

        // verify
        Mockito.verify(this.aaaController, Mockito.times(1)).authorize(CLOUD_NAME,
                Mockito.any(FederationUser.class), Mockito.any(Operation.class),
                Mockito.any(ResourceType.class));

        Mockito.verify(this.cloudConnector, Mockito.times(1))
                .getUserQuota(Mockito.eq(federationUser), Mockito.eq(ResourceType.COMPUTE));

        Assert.assertNotNull(expected);
        Assert.assertEquals(expected.getTotalQuota(), quota.getTotalQuota());
        Assert.assertEquals(expected.getUsedQuota(), quota.getUsedQuota());
    }

    // test case: Verifies generic request behavior inside Remote Facade, i.e. it needs to authenticate and authorize
    // request, and also get the correct cloud connector, before passing the generic request.
    @Test
    public void testGenericRequest() throws Exception {
        // set up
        String localMemberId = PropertiesHolder.getInstance().getProperty(ConfigurationConstants.LOCAL_MEMBER_ID_KEY);
        FederationUser federationUser = createFederationUser();
        Mockito.doNothing().when(this.aaaController).remoteAuthenticateAndAuthorize(Mockito.anyString(),
                Mockito.eq(federationUser), Mockito.anyString(), Mockito.eq(Operation.GENERIC_REQUEST),
                Mockito.eq(ResourceType.GENERIC_REQUEST));

        CloudConnectorFactory cloudConnectorFactory = Mockito.mock(CloudConnectorFactory.class);

        PowerMockito.mockStatic(CloudConnectorFactory.class);
        BDDMockito.given(CloudConnectorFactory.getInstance()).willReturn(cloudConnectorFactory);

        Mockito.doReturn(Mockito.mock(GenericRequestResponse.class)).when(cloudConnector).
                genericRequest(Mockito.any(GenericRequest.class), Mockito.eq(federationUser));

        Mockito.when(cloudConnectorFactory.getCloudConnector(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(this.cloudConnector);

        // exercise
        remoteFacade.genericRequest(REQUESTING_MEMBER_ID, CLOUD_NAME,
                Mockito.mock(GenericRequest.class), federationUser);

        // verify
        Mockito.verify(aaaController, Mockito.times(1)).remoteAuthenticateAndAuthorize(Mockito.anyString(),
                Mockito.eq(federationUser), Mockito.anyString(), Mockito.eq(Operation.GENERIC_REQUEST),
                Mockito.eq(ResourceType.GENERIC_REQUEST));

        Mockito.verify(cloudConnectorFactory, Mockito.times(1)).
                getCloudConnector(Mockito.eq(localMemberId), Mockito.eq(CLOUD_NAME));

        Mockito.verify(cloudConnector, Mockito.times(1)).
                genericRequest(Mockito.any(GenericRequest.class), Mockito.eq(federationUser));
    }

    @Test
    public void testRemoteGetImage() {
        // TODO implement test
    }

    @Test
    public void testRemoteGetAllImages() {
        // TODO implement test
    }

    @Test
    public void testRemoteHandleRemoteEvent() {
        // TODO implement test
    }

    private Order createOrder(FederationUser token) {
        return new ComputeOrder(token, REQUESTING_MEMBER_ID, "fake-providing-member", CLOUD_NAME, "fake-instance-name",
                -1, -1, -1, "fake-image-id", null, "fake-public-key", new ArrayList<String>());
    }

    private FederationUser createFederationUser() {
        return new FederationUser("fake-token-provider", "fake-token", "fake-id", "fogbow");
    }

}
