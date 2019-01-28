package cloud.fogbow.common.core;

import cloud.fogbow.common.exceptions.UnexpectedException;
import cloud.fogbow.common.util.HomeDir;
import cloud.fogbow.common.core.cloudconnector.CloudConnectorFactory;
import cloud.fogbow.common.core.cloudconnector.LocalCloudConnector;
import cloud.fogbow.common.core.constants.SystemConstants;
import cloud.fogbow.common.core.datastore.DatabaseManager;
import cloud.fogbow.common.core.models.orders.ComputeOrder;
import cloud.fogbow.common.core.models.orders.NetworkOrder;
import cloud.fogbow.common.core.models.orders.Order;
import cloud.fogbow.common.core.models.orders.OrderState;
import cloud.fogbow.common.core.models.InstanceStatus;
import cloud.fogbow.common.core.models.ResourceType;
import cloud.fogbow.common.core.models.instances.ComputeInstance;
import cloud.fogbow.common.core.models.instances.Instance;
import cloud.fogbow.common.core.models.instances.InstanceState;
import cloud.fogbow.common.core.models.linkedlists.ChainedList;
import cloud.fogbow.common.core.models.quotas.allocation.ComputeAllocation;
import cloud.fogbow.common.models.FederationUser;
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

import java.util.List;
import java.util.Map;

@RunWith(PowerMockRunner.class)
@PrepareForTest({DatabaseManager.class, CloudConnectorFactory.class})
public class OrderControllerTest extends BaseUnitTests {

    //FIXME: many of bellow tests, e.g testGetResourceInstance are too complicated. The problem is
    //they are setting up the scenario for the getOrder method (which they use). This set up
    //include changing the content of fulfilledOrdersList and activeOrdersMap.put
    //A simpler way of doing these tests is to spy the behaviour of the getOrder method.

    private static final String RESOURCES_PATH_TEST = "src/test/resources/private";
    private static final String FAKE_NAME = "fake-name";
    private static final String FAKE_ID = "fake-id";
    private static final String FAKE_USER = "fake-user";
    private static final String FAKE_IMAGE_NAME = "fake-image-name";
    private static final String FAKE_PUBLIC_KEY = "fake-public-key";

    private OrderController ordersController;
    private Map<String, Order> activeOrdersMap;
    private ChainedList openOrdersList;
    private ChainedList pendingOrdersList;
    private ChainedList spawningOrdersList;
    private ChainedList fulfilledOrdersList;
    private ChainedList failedAfterSuccessfulRequestOrdersList;
    private ChainedList failedOnRequestOrdersList;
    private ChainedList closedOrdersList;
    private String localMember = BaseUnitTests.LOCAL_MEMBER_ID;
    private LocalCloudConnector localCloudConnector;

    @Before
    public void setUp() throws UnexpectedException {
        PowerMockito.mockStatic(CloudConnectorFactory.class);
        // mocking database to return empty instances of SynchronizedDoublyLinkedList.
        super.mockReadOrdersFromDataBase();

        this.ordersController = new OrderController();

        SharedOrderHolders sharedOrderHolders = SharedOrderHolders.getInstance();

        String aaaConfFilePath = HomeDir.getPath() + SystemConstants.AAA_CONF_FILE_NAME;
        AaaPluginsHolder aaaPluginsHolder = new AaaPluginsHolder();
        aaaPluginsHolder.setTokenGeneratorPlugin(AaaPluginInstantiator.getTokenGeneratorPlugin(aaaConfFilePath));
        aaaPluginsHolder.setFederationIdentityPlugin(AaaPluginInstantiator.getFederationIdentityPlugin(aaaConfFilePath));
        aaaPluginsHolder.setAuthenticationPlugin(AaaPluginInstantiator.getAuthenticationPlugin(aaaConfFilePath));
        aaaPluginsHolder.setAuthorizationPlugin(AaaPluginInstantiator.getAuthorizationPlugin(aaaConfFilePath));

        this.localCloudConnector = Mockito.mock(LocalCloudConnector.class);

        // setting up the attributes.
        this.activeOrdersMap = sharedOrderHolders.getActiveOrdersMap();
        this.openOrdersList = sharedOrderHolders.getOpenOrdersList();
        this.pendingOrdersList = sharedOrderHolders.getPendingOrdersList();
        this.spawningOrdersList = sharedOrderHolders.getSpawningOrdersList();
        this.fulfilledOrdersList = sharedOrderHolders.getFulfilledOrdersList();
        this.failedAfterSuccessfulRequestOrdersList = sharedOrderHolders.getFailedAfterSuccessfulRequestOrdersList();
        this.closedOrdersList = sharedOrderHolders.getClosedOrdersList();
    }

    // test case: When pass an Order with id null, it must raise an InvalidParameterException.
    @Test(expected = InvalidParameterException.class) // verify
    public void testDeleteOrderThrowsInvalidParameterException()
            throws Exception {

        // set up
        Order order = Mockito.mock(Order.class);
        String orderId = order.getId();

        // verify
        Assert.assertNull(orderId);

        // exercise
        this.ordersController.deleteOrder(orderId);
    }

    // test case: when try to delete an Order closed, it must raise an InstanceNotFoundException.
    @Test(expected = InstanceNotFoundException.class) // verify
    public void testDeleteClosedOrderThrowsInstanceNotFoundException()
            throws Exception {

        // set up
        String orderId = getComputeOrderCreationId(OrderState.CLOSED);

        // exercise
        this.ordersController.deleteOrder(orderId);
    }

    // test case: Checks if getInstancesStatus() returns exactly the same list of instances that
    // were added on the lists.
    @Test
    public void testGetAllInstancesStatus() throws InvalidParameterException {
        // set up
        FederationUser federationUser = new FederationUser("fake-token-provider",
                "token-value", "fake-id", "fake-user");
        ComputeOrder computeOrder = new ComputeOrder();
        computeOrder.setFederationUser(federationUser);
        computeOrder.setRequester(this.localMember);
        computeOrder.setProvider(this.localMember);
        computeOrder.setOrderStateInTestMode(OrderState.FULFILLED);
        computeOrder.setCachedInstanceState(InstanceState.READY);

        ComputeOrder computeOrder2 = new ComputeOrder();
        computeOrder2.setFederationUser(federationUser);
        computeOrder2.setRequester(this.localMember);
        computeOrder2.setProvider(this.localMember);
        computeOrder2.setOrderStateInTestMode(OrderState.FAILED_AFTER_SUCCESSUL_REQUEST);
        computeOrder2.setCachedInstanceState(InstanceState.FAILED);

        this.activeOrdersMap.put(computeOrder.getId(), computeOrder);
        this.fulfilledOrdersList.addItem(computeOrder);

        this.activeOrdersMap.put(computeOrder2.getId(), computeOrder2);
        this.failedAfterSuccessfulRequestOrdersList.addItem(computeOrder2);

        InstanceStatus statusOrder = new InstanceStatus(computeOrder.getId(),
                computeOrder.getProvider(), computeOrder.getCloudName(), computeOrder.getCachedInstanceState());
        InstanceStatus statusOrder2 = new InstanceStatus(computeOrder2.getId(),
                computeOrder2.getProvider(), computeOrder.getCloudName(), computeOrder2.getCachedInstanceState());

        // exercise
        List<InstanceStatus> instances = this.ordersController.getInstancesStatus(federationUser,
                ResourceType.COMPUTE);

        // verify
        Assert.assertTrue(instances.contains(statusOrder));
        Assert.assertTrue(instances.contains(statusOrder2));
        Assert.assertEquals(2, instances.size());
    }

    // test case: Checks if getOrder() returns exactly the same order that
    // were added on the list.
    @Test
    public void testGetOrder() throws UnexpectedException, FogbowRasException {
        // set up
        String orderId = getComputeOrderCreationId(OrderState.OPEN);

        FederationUser federationUser = new FederationUser("fake-token-provider",
                "token-value", "fake-id", "fake-user");

        // exercise
        ComputeOrder computeOrder = (ComputeOrder) this.ordersController.getOrder(orderId);

        // verify
        Assert.assertEquals(computeOrder, this.openOrdersList.getNext());
    }

    // test case: Get a not active Order, must throw InstanceNotFoundException.
    @Test(expected = InstanceNotFoundException.class) // verify
    public void testGetInactiveOrder() throws InstanceNotFoundException {
        // set up
        Order order = createOrder(LOCAL_MEMBER_ID, LOCAL_MEMBER_ID);
        String orderId = order.getId();

        // verify
        Assert.assertNull(this.activeOrdersMap.get(orderId));

        // exercise
        this.ordersController.getOrder(orderId);
    }

    // test case: Getting order with when federationUser is null must throw InstanceNotFoundException.
    @Test(expected = InstanceNotFoundException.class)
    public void testGetInvalidOrder() throws FogbowRasException {
        // setup
        FederationUser federationUser = new FederationUser("fake-token-provider",
                "token-value", "fake-id", "fake-user");

        // exercise
        this.ordersController.getOrder("invalid-order-id");
    }

    // test case: Checks if given an order getResourceInstance() returns its instance.
    @Test
    public void testGetResourceInstance() throws Exception {
        // set up
        LocalCloudConnector localCloudConnector = Mockito.mock(LocalCloudConnector.class);

        CloudConnectorFactory cloudConnectorFactory = Mockito.mock(CloudConnectorFactory.class);
        Mockito.when(cloudConnectorFactory.getCloudConnector(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(localCloudConnector);

        Order order = createOrder(LOCAL_MEMBER_ID, LOCAL_MEMBER_ID);
        order.setOrderStateInTestMode(OrderState.FULFILLED);

        this.fulfilledOrdersList.addItem(order);
        this.activeOrdersMap.put(order.getId(), order);

        String instanceId = "instanceid";
        Instance orderInstance = Mockito.spy(new ComputeInstance(instanceId));
        orderInstance.setState(InstanceState.READY);
        order.setInstanceId(instanceId);

        Mockito.doReturn(orderInstance).when(localCloudConnector)
                .getInstance(Mockito.any(Order.class));

        PowerMockito.mockStatic(CloudConnectorFactory.class);
        BDDMockito.given(CloudConnectorFactory.getInstance()).willReturn(cloudConnectorFactory);

        // exercise
        Instance instance = this.ordersController.getResourceInstance(order.getId());

        // verify
        Assert.assertEquals(orderInstance, instance);
    }

    // test case: Tests if getUserAllocation() returns the ComputeAllocation properly.
    @Test
    public void testGetUserAllocation() throws UnexpectedException, InvalidParameterException {
        // set up
        FederationUser federationUser = new FederationUser("fake-token-provider",
                "token-value", "fake-id", "fake-user");
        ComputeOrder computeOrder = new ComputeOrder();
        computeOrder.setFederationUser(federationUser);
        computeOrder.setRequester(this.localMember);
        computeOrder.setProvider(this.localMember);
        computeOrder.setOrderStateInTestMode(OrderState.FULFILLED);

        computeOrder.setActualAllocation(new ComputeAllocation(1, 2, 3));

        this.activeOrdersMap.put(computeOrder.getId(), computeOrder);
        this.fulfilledOrdersList.addItem(computeOrder);

        // exercise
        ComputeAllocation allocation = (ComputeAllocation) this.ordersController.getUserAllocation(
                this.localMember, federationUser, ResourceType.COMPUTE);

        // verify
        Assert.assertEquals(computeOrder.getActualAllocation().getInstances(),
                allocation.getInstances());
        Assert.assertEquals(computeOrder.getActualAllocation().getRam(), allocation.getRam());
        Assert.assertEquals(computeOrder.getActualAllocation().getvCPU(), allocation.getvCPU());
    }


    // test case: Tests if getUserAllocation() throws UnexpectedException when there is no order
    // with the ResourceType specified.
    @Test(expected = UnexpectedException.class)
    public void testGetUserAllocationWithInvalidInstanceType()
            throws UnexpectedException, InvalidParameterException {
        // set up
        FederationUser federationUser = new FederationUser("fake-token-provider",
                "token-value", "fake-id", "fake-user");
        NetworkOrder networkOrder = new NetworkOrder();
        networkOrder.setFederationUser(federationUser);
        networkOrder.setRequester(this.localMember);
        networkOrder.setProvider(this.localMember);
        networkOrder.setOrderStateInTestMode(OrderState.FULFILLED);

        this.fulfilledOrdersList.addItem(networkOrder);
        this.activeOrdersMap.put(networkOrder.getId(), networkOrder);

        // exercise
        this.ordersController.getUserAllocation(this.localMember, federationUser, ResourceType.NETWORK);
    }

    // test case: Checks if deleting a failed order, this one will be moved to the closed orders
    // list.
    @Test
    public void testDeleteOrderStateFailed()
            throws Exception {
        // set up
        String orderId = getComputeOrderCreationId(OrderState.FAILED_AFTER_SUCCESSUL_REQUEST);
        ComputeOrder computeOrder = (ComputeOrder) this.activeOrdersMap.get(orderId);

        CloudConnectorFactory cloudConnectorFactory = Mockito.mock(CloudConnectorFactory.class);
        PowerMockito.when(CloudConnectorFactory.getInstance()).thenReturn(cloudConnectorFactory);
        Mockito.when(cloudConnectorFactory.getCloudConnector(Mockito.anyString(), Mockito.anyString())).thenReturn(localCloudConnector);

        // verify
        Assert.assertNotNull(this.failedAfterSuccessfulRequestOrdersList.getNext());
        Assert.assertNull(this.closedOrdersList.getNext());

        // exercise
        this.ordersController.deleteOrder(orderId);

        // verify
        Order order = this.closedOrdersList.getNext();
        this.failedAfterSuccessfulRequestOrdersList.resetPointer();

        Assert.assertNull(this.failedAfterSuccessfulRequestOrdersList.getNext());
        Assert.assertNotNull(order);
        Assert.assertEquals(computeOrder, order);
        Assert.assertEquals(OrderState.CLOSED, order.getOrderState());
    }

    // test case: Checks if deleting a fulfilled order, this one will be moved to the closed orders
    // list.
    @Test
    public void testDeleteOrderStateFulfilled()
            throws Exception {
        // set up
        String orderId = getComputeOrderCreationId(OrderState.FULFILLED);
        ComputeOrder computeOrder = (ComputeOrder) this.activeOrdersMap.get(orderId);

        CloudConnectorFactory cloudConnectorFactory = Mockito.mock(CloudConnectorFactory.class);
        PowerMockito.when(CloudConnectorFactory.getInstance()).thenReturn(cloudConnectorFactory);
        Mockito.when(cloudConnectorFactory.getCloudConnector(Mockito.anyString(), Mockito.anyString())).thenReturn(localCloudConnector);

        // verify
        Assert.assertNotNull(this.fulfilledOrdersList.getNext());
        Assert.assertNull(this.closedOrdersList.getNext());

        // exercise
        this.ordersController.deleteOrder(orderId);

        // verify
        Order order = this.closedOrdersList.getNext();

        Assert.assertNull(this.fulfilledOrdersList.getNext());
        Assert.assertNotNull(order);
        Assert.assertEquals(computeOrder, order);
        Assert.assertEquals(OrderState.CLOSED, order.getOrderState());
    }

    // test case: Checks if deleting a spawning order, this one will be moved to the closed orders
    // list.
    @Test
    public void testDeleteOrderStateSpawning()
            throws Exception {
        // set up
        String orderId = getComputeOrderCreationId(OrderState.SPAWNING);
        ComputeOrder computeOrder = (ComputeOrder) this.activeOrdersMap.get(orderId);

        CloudConnectorFactory cloudConnectorFactory = Mockito.mock(CloudConnectorFactory.class);
        PowerMockito.when(CloudConnectorFactory.getInstance()).thenReturn(cloudConnectorFactory);
        Mockito.when(cloudConnectorFactory.getCloudConnector(Mockito.anyString(), Mockito.anyString())).thenReturn(localCloudConnector);

        // verify
        Assert.assertNotNull(this.spawningOrdersList.getNext());
        Assert.assertNull(this.closedOrdersList.getNext());

        // exercise
        this.ordersController.deleteOrder(orderId);

        // verify
        Order order = this.closedOrdersList.getNext();

        Assert.assertNull(this.spawningOrdersList.getNext());
        Assert.assertNotNull(order);
        Assert.assertEquals(computeOrder, order);
        Assert.assertEquals(OrderState.CLOSED, order.getOrderState());
    }

    // test case: Checks if deleting a pending order, this one will be moved to the closed orders
    // list.
    @Test
    public void testDeleteOrderStatePending()
            throws Exception {
        // set up
        String orderId = getComputeOrderCreationId(OrderState.PENDING);
        ComputeOrder computeOrder = (ComputeOrder) this.activeOrdersMap.get(orderId);

        CloudConnectorFactory cloudConnectorFactory = Mockito.mock(CloudConnectorFactory.class);
        PowerMockito.when(CloudConnectorFactory.getInstance()).thenReturn(cloudConnectorFactory);
        Mockito.when(cloudConnectorFactory.getCloudConnector(Mockito.anyString(), Mockito.anyString())).thenReturn(localCloudConnector);

        // verify
        Assert.assertNotNull(this.pendingOrdersList.getNext());
        Assert.assertNull(this.closedOrdersList.getNext());

        // exercise
        this.ordersController.deleteOrder(orderId);

        // verify
        Order order = this.closedOrdersList.getNext();
        Assert.assertNull(this.pendingOrdersList.getNext());

        Assert.assertNotNull(order);
        Assert.assertEquals(computeOrder, order);
        Assert.assertEquals(OrderState.CLOSED, order.getOrderState());
    }

    // test case: Checks if deleting a open order, this one will be moved to the closed orders list.
    @Test
    public void testDeleteOrderStateOpen()
            throws Exception {
        // set up
        String orderId = getComputeOrderCreationId(OrderState.OPEN);
        ComputeOrder computeOrder = (ComputeOrder) this.activeOrdersMap.get(orderId);

        CloudConnectorFactory cloudConnectorFactory = Mockito.mock(CloudConnectorFactory.class);
        PowerMockito.when(CloudConnectorFactory.getInstance()).thenReturn(cloudConnectorFactory);
        Mockito.when(cloudConnectorFactory.getCloudConnector(Mockito.anyString(), Mockito.anyString())).thenReturn(localCloudConnector);

        // verify
        Assert.assertNotNull(this.openOrdersList.getNext());
        Assert.assertNull(this.closedOrdersList.getNext());

        // exercise
        this.ordersController.deleteOrder(orderId);

        // verify
        Order order = this.closedOrdersList.getNext();

        Assert.assertNull(this.openOrdersList.getNext());
        Assert.assertNotNull(order);
        Assert.assertEquals(computeOrder, order);
        Assert.assertEquals(OrderState.CLOSED, order.getOrderState());
    }

    // test case: Deleting a null order must return a FogbowRasException.
    @Test(expected = FogbowRasException.class) // verify
    public void testDeleteNullOrder()
            throws Exception {
        // exercise
        this.ordersController.deleteOrder(null);
    }

    // test case: Getting an order with an unexisting id must throw an InstanceNotFoundException
    @Test(expected = InstanceNotFoundException.class)
    public void testGetOrderWithInvalidId() throws InstanceNotFoundException {
        this.ordersController.getOrder("invalid-order-id");
    }

    // test case: Getting an order passing a different ResourceType must raise InstanceNotFoundException.
    // ToDO: The refactor in ApplicationFacade moved the this logic out from OrderController; this test should be moved elsewhere.
    @Ignore
    @Test(expected = InstanceNotFoundException.class)
    public void testGetOrderWithInvalidInstanceType() throws FogbowRasException, UnexpectedException {
        // set up
        String orderId = getComputeOrderCreationId(OrderState.OPEN);

        // exercise
        this.ordersController.getOrder(orderId);
    }

    // test case: Getting order with when invalid federationUser (any fedUser with another ID)
    // must throw InstanceNotFoundException.
    // ToDO: The refactor in ApplicationFacade moved the this logic out from OrderController; this test should be moved elsewhere.
    @Ignore
    @Test(expected = UnauthorizedRequestException.class)
    public void testGetOrderWithInvalidFedUser() throws FogbowRasException {
        // set up
        String orderId = getComputeOrderCreationId(OrderState.OPEN);

        // exercise
        this.ordersController.getOrder(orderId);
    }

    private String getComputeOrderCreationId(OrderState orderState) throws InvalidParameterException {
        String orderId;
        FederationUser federationUser = new FederationUser("fake-token-provider",
                "token-value", "fake-id", "fake-user");

        ComputeOrder computeOrder = Mockito.spy(new ComputeOrder());
        computeOrder.setFederationUser(federationUser);
        computeOrder.setRequester(this.localMember);
        computeOrder.setProvider(this.localMember);
        computeOrder.setOrderStateInTestMode(orderState);

        orderId = computeOrder.getId();

        this.activeOrdersMap.put(orderId, computeOrder);

        switch (orderState) {
            case OPEN:
                this.openOrdersList.addItem(computeOrder);
                break;
            case PENDING:
                this.pendingOrdersList.addItem(computeOrder);
                break;
            case SPAWNING:
                this.spawningOrdersList.addItem(computeOrder);
                break;
            case FULFILLED:
                this.fulfilledOrdersList.addItem(computeOrder);
                break;
            case FAILED_AFTER_SUCCESSUL_REQUEST:
                this.failedAfterSuccessfulRequestOrdersList.addItem(computeOrder);
                break;
            case FAILED_ON_REQUEST:
                this.failedOnRequestOrdersList.addItem(computeOrder);
                break;
            case CLOSED:
                this.closedOrdersList.addItem(computeOrder);
            default:
                break;
        }

        return orderId;
    }

    private Order createLocalOrder() {
        FederationUser federationUser = Mockito.mock(FederationUser.class);
        String imageName = "fake-image-name";
        String requestingMember = "";
        String providingMember = "";
        String publicKey = "fake-public-key";
        String instanceName = "fake-instance-name";

        Order localOrder =
                new ComputeOrder(
                        federationUser,
                        requestingMember,
                        providingMember,
                        "default", instanceName,
                        8,
                        1024,
                        30,
                        imageName,
                        null,
                        publicKey,
                        null);

        return localOrder;
    }
}
