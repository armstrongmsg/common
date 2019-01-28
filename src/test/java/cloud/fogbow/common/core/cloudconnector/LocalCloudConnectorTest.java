package cloud.fogbow.common.core.cloudconnector;

import cloud.fogbow.common.exceptions.InvalidParameterException;
import cloud.fogbow.common.exceptions.UnexpectedException;
import cloud.fogbow.common.models.CloudToken;
import cloud.fogbow.common.models.FederationUser;
import cloud.fogbow.common.core.BaseUnitTests;
import cloud.fogbow.common.core.SharedOrderHolders;
import cloud.fogbow.common.core.datastore.DatabaseManager;
import cloud.fogbow.common.core.models.ResourceType;
import cloud.fogbow.common.core.models.images.Image;
import cloud.fogbow.common.core.models.instances.*;
import cloud.fogbow.common.core.models.orders.*;
import org.fogbowcloud.ras.core.models.instances.*;
import cloud.fogbow.common.core.models.linkedlists.SynchronizedDoublyLinkedList;
import org.fogbowcloud.ras.core.models.orders.*;
import cloud.fogbow.common.core.models.quotas.ComputeQuota;
import cloud.fogbow.common.core.models.quotas.allocation.ComputeAllocation;
import cloud.fogbow.common.core.plugins.interoperability.genericrequest.GenericRequest;
import cloud.fogbow.common.core.plugins.interoperability.genericrequest.GenericRequestPlugin;
import cloud.fogbow.common.core.plugins.interoperability.genericrequest.GenericRequestResponse;
import cloud.fogbow.common.core.plugins.mapper.FederationToLocalMapperPlugin;
import org.fogbowcloud.ras.core.plugins.interoperability.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.times;

@RunWith(PowerMockRunner.class)
@PrepareForTest({DatabaseManager.class})
public class LocalCloudConnectorTest extends BaseUnitTests {

    private static final String FAKE_INSTANCE_ID = "fake-instance-id";
    private static final String FAKE_VOLUME_ID = "fake-volume-id";
    private static final String FAKE_ORDER_ID = "fake-order-id";
    private static final String FAKE_IMAGE_ID = "fake-image-id";
    private static final String FAKE_IMAGE_NAME = "fake-image-name";
    private static final String FAKE_COMPUTE_ID = "fake-compute-id";
    private static final String FAKE_USER_ID = "fake-user-id";
    private static final String FAKE_PROVIDER = "fake-provider";
    private static final int VCPU_TOTAL = 2;
    private static final int RAM_TOTAL = 2048;
    private static final int INSTANCES_TOTAL = 2;
    private static final int VCPU_USED = 1;
    private static final int RAM_USED = 1024;
    private static final int INSTANCES_USED = 1;

    private LocalCloudConnector localCloudConnector;

    private ComputePlugin computePlugin;
    private AttachmentPlugin attachmentPlugin;
    private NetworkPlugin networkPlugin;
    private VolumePlugin volumePlugin;
    private ImagePlugin imagePlugin;
    private ComputeQuotaPlugin computeQuotaPlugin;
    private PublicIpPlugin publicIpPlugin;
    private GenericRequestPlugin genericRequestPlugin;
    private FederationToLocalMapperPlugin federationToLocalMapperPlugin;

    private Order order;
    private Image image;
    private FederationUser federationUser;

    private NetworkInstance networkInstance;
    private VolumeInstance volumeInstance;
    private AttachmentInstance attachmentInstance;
    private ComputeInstance computeInstance;

    @Before
    public void setUp() throws FogbowRasException, UnexpectedException {

        // mocking databaseManager
        DatabaseManager databaseManager = Mockito.mock(DatabaseManager.class);
        Mockito.when(databaseManager.readActiveOrders(OrderState.OPEN)).thenReturn(new SynchronizedDoublyLinkedList());
        Mockito.when(databaseManager.readActiveOrders(OrderState.SPAWNING)).thenReturn(new SynchronizedDoublyLinkedList());
        Mockito.when(databaseManager.readActiveOrders(OrderState.FAILED_AFTER_SUCCESSUL_REQUEST)).thenReturn(new SynchronizedDoublyLinkedList());
        Mockito.when(databaseManager.readActiveOrders(OrderState.FAILED_ON_REQUEST)).thenReturn(new SynchronizedDoublyLinkedList());
        Mockito.when(databaseManager.readActiveOrders(OrderState.FULFILLED)).thenReturn(new SynchronizedDoublyLinkedList());
        Mockito.when(databaseManager.readActiveOrders(OrderState.PENDING)).thenReturn(new SynchronizedDoublyLinkedList());
        Mockito.when(databaseManager.readActiveOrders(OrderState.CLOSED)).thenReturn(new SynchronizedDoublyLinkedList());
        PowerMockito.mockStatic(DatabaseManager.class);
        BDDMockito.given(DatabaseManager.getInstance()).willReturn(databaseManager);

        // mocking class attributes
        FederationToLocalMapperPlugin mapperPlugin = Mockito.mock(FederationToLocalMapperPlugin.class);

        this.federationUser = Mockito.mock(FederationUser.class);
        this.computePlugin = Mockito.mock(ComputePlugin.class);
        this.attachmentPlugin = Mockito.mock(AttachmentPlugin.class);
        this.networkPlugin = Mockito.mock(NetworkPlugin.class);
        this.volumePlugin = Mockito.mock(VolumePlugin.class);
        this.imagePlugin = Mockito.mock(ImagePlugin.class);
        this.computeQuotaPlugin = Mockito.mock(ComputeQuotaPlugin.class);
        this.publicIpPlugin = Mockito.mock(PublicIpPlugin.class);
        this.genericRequestPlugin = Mockito.mock(GenericRequestPlugin.class);
        this.federationToLocalMapperPlugin = Mockito.mock(FederationToLocalMapperPlugin.class);

        // mocking federation user token calls
        Mockito.when(federationUser.getUserId()).thenReturn(FAKE_USER_ID);

        // mocking instances/image and the return of getID method
        this.networkInstance = Mockito.mock(NetworkInstance.class);
        Mockito.when(networkInstance.getId()).thenReturn(FAKE_INSTANCE_ID);

        this.volumeInstance = Mockito.mock(VolumeInstance.class);
        Mockito.when(volumeInstance.getId()).thenReturn(FAKE_INSTANCE_ID);

        this.attachmentInstance = Mockito.mock(AttachmentInstance.class);
        Mockito.when(attachmentInstance.getId()).thenReturn(FAKE_INSTANCE_ID);
        Mockito.when(attachmentInstance.getComputeId()).thenReturn(FAKE_COMPUTE_ID);
        Mockito.when(attachmentInstance.getVolumeId()).thenReturn(FAKE_VOLUME_ID);

        this.computeInstance = Mockito.mock(ComputeInstance.class);
        Mockito.when(computeInstance.getId()).thenReturn(FAKE_INSTANCE_ID);

        this.image = Mockito.mock(Image.class);
        Mockito.when(image.getId()).thenReturn(FAKE_IMAGE_ID);

        // mocking interoperabilityPluginsHolder to return the correct plugin for each call
        Mockito.when(mapperPlugin.map(Mockito.any(FederationUser.class))).thenReturn(new CloudToken("", "", ""));

        // starting the object we want to test
        this.localCloudConnector = new LocalCloudConnector("default");
        this.localCloudConnector.setAttachmentPlugin(this.attachmentPlugin);
        this.localCloudConnector.setComputePlugin(this.computePlugin);
        this.localCloudConnector.setComputeQuotaPlugin(this.computeQuotaPlugin);
        this.localCloudConnector.setImagePlugin(this.imagePlugin);
        this.localCloudConnector.setMapperPlugin(this.federationToLocalMapperPlugin);
        this.localCloudConnector.setNetworkPlugin(this.networkPlugin);
        this.localCloudConnector.setPublicIpPlugin(this.publicIpPlugin);
        this.localCloudConnector.setVolumePlugin(this.volumePlugin);
        this.localCloudConnector.setGenericRequestPlugin(this.genericRequestPlugin);
    }

    // test case: When calling the method getNetworkInstanceIdsFromNetworkOrderIds(), it must return
    // the collection of NetworkInstancesIds corresponding to the list of NetworkOrderIds in the order received.
    @Test
    public void testGetNetworkInstanceIdsFromNetworkOrderIds() throws InvalidParameterException {
        // set up
        NetworkOrder networkOrder = Mockito.mock(NetworkOrder.class);
        FederationUser federationUser = new FederationUser(
                "fake-tokenProvider", "fake-token-value", "fake-user-id", "fake-user-name");
        Mockito.when(networkOrder.getFederationUser()).thenReturn(federationUser);

        Mockito.doReturn(FAKE_ORDER_ID).when(networkOrder).getId();
        Mockito.doReturn(FAKE_INSTANCE_ID).when(networkOrder).getInstanceId();

        SharedOrderHolders.getInstance().getActiveOrdersMap().put(networkOrder.getId(), networkOrder);

        List<String> networkOrderIdsList = new ArrayList<>();
        networkOrderIdsList.add(FAKE_ORDER_ID);

        ComputeOrder computeOrder = new ComputeOrder();
        computeOrder.setFederationUser(federationUser);
        computeOrder.setNetworkIds(networkOrderIdsList);

        List<String> expectedList = new ArrayList<>();
        expectedList.add(FAKE_INSTANCE_ID);

        // exercise 1
        List<String> returnedList = this.localCloudConnector.getNetworkInstanceIdsFromNetworkOrderIds(computeOrder);

        // verify
        Assert.assertEquals(expectedList, returnedList);
    }


    // test case: Request a compute instance when the plugin returns a correct id
    @Test
    public void testRequestComputeInstance() throws FogbowRasException, UnexpectedException {
        // set up
        this.order = Mockito.mock(ComputeOrder.class);
        Mockito.when(this.order.getFederationUser()).thenReturn(this.federationUser);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.COMPUTE);
        Mockito.when(computePlugin.requestInstance(Mockito.any(ComputeOrder.class), Mockito.any(Token.class))).thenReturn(FAKE_INSTANCE_ID);

        // exercise
        String returnedInstanceId = this.localCloudConnector.requestInstance(order);

        // verify
        Assert.assertEquals(FAKE_INSTANCE_ID, returnedInstanceId);
        Mockito.verify(computePlugin, times(1)).requestInstance(Mockito.any(ComputeOrder.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).requestInstance(Mockito.any(VolumeOrder.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).requestInstance(Mockito.any(AttachmentOrder.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).requestInstance(Mockito.any(NetworkOrder.class), Mockito.any(Token.class));
    }

    // test case: Request an attachment instance Mockito.when the plugin returns a correct id
    @Test
    public void testRequestAttachmentInstance() throws FogbowRasException, UnexpectedException {

        // set up
        ComputeOrder source = Mockito.mock(ComputeOrder.class);
        VolumeOrder target = Mockito.mock(VolumeOrder.class);
        Mockito.when(source.getFederationUser()).thenReturn(this.federationUser);
        Mockito.when(source.getProvider()).thenReturn(FAKE_PROVIDER);
        Mockito.when(target.getFederationUser()).thenReturn(this.federationUser);
        Mockito.when(target.getProvider()).thenReturn(FAKE_PROVIDER);
        SharedOrderHolders.getInstance().getActiveOrdersMap().put(FAKE_COMPUTE_ID, source);
        SharedOrderHolders.getInstance().getActiveOrdersMap().put(FAKE_VOLUME_ID, target);
        this.order = Mockito.mock(AttachmentOrder.class);
        Mockito.when(this.order.getFederationUser()).thenReturn(this.federationUser);
        Mockito.when(this.order.getProvider()).thenReturn(FAKE_PROVIDER);
        Mockito.when(((AttachmentOrder) this.order).getComputeId()).thenReturn(FAKE_COMPUTE_ID);
        Mockito.when(((AttachmentOrder) this.order).getVolumeId()).thenReturn(FAKE_VOLUME_ID);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.ATTACHMENT);
        Mockito.when(attachmentPlugin.requestInstance(Mockito.any(AttachmentOrder.class), Mockito.any(Token.class))).thenReturn(FAKE_INSTANCE_ID);

        //exercise
        String returnedInstanceId = this.localCloudConnector.requestInstance(order);

        // verify
        Assert.assertEquals(FAKE_INSTANCE_ID, returnedInstanceId);
        Mockito.verify(computePlugin, times(0)).requestInstance(Mockito.any(ComputeOrder.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).requestInstance(Mockito.any(VolumeOrder.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(1)).requestInstance(Mockito.any(AttachmentOrder.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).requestInstance(Mockito.any(NetworkOrder.class), Mockito.any(Token.class));

        // exercise
        this.localCloudConnector.requestInstance(order);

        // tear down
        SharedOrderHolders.getInstance().getActiveOrdersMap().clear();
    }

    // test case: Request a volume instance Mockito.when the plugin returns a correct id
    @Test
    public void testRequestVolumeInstance() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(VolumeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.VOLUME);
        Mockito.when(volumePlugin.requestInstance(Mockito.any(VolumeOrder.class), Mockito.any(Token.class))).thenReturn(FAKE_INSTANCE_ID);

        //exercise
        String returnedInstanceId = this.localCloudConnector.requestInstance(order);

        // verify
        Assert.assertEquals(FAKE_INSTANCE_ID, returnedInstanceId);
        Mockito.verify(computePlugin, times(0)).requestInstance(Mockito.any(ComputeOrder.class), Mockito.any(Token.class));
        Mockito.verify(computePlugin, times(0)).requestInstance(Mockito.any(ComputeOrder.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(1)).requestInstance(Mockito.any(VolumeOrder.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).requestInstance(Mockito.any(AttachmentOrder.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).requestInstance(Mockito.any(NetworkOrder.class), Mockito.any(Token.class));
    }

    // test case: Request a network instance Mockito.when the plugin returns a correct id
    @Test
    public void testRequestNetworkInstance() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(NetworkOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.NETWORK);
        Mockito.when(networkPlugin.requestInstance(Mockito.any(NetworkOrder.class), Mockito.any(Token.class))).thenReturn(FAKE_INSTANCE_ID);

        //exercise
        String returnedInstanceId = this.localCloudConnector.requestInstance(order);

        // verify
        Assert.assertEquals(FAKE_INSTANCE_ID, returnedInstanceId);
        Mockito.verify(computePlugin, times(0)).requestInstance(Mockito.any(ComputeOrder.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).requestInstance(Mockito.any(VolumeOrder.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).requestInstance(Mockito.any(AttachmentOrder.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(1)).requestInstance(Mockito.any(NetworkOrder.class), Mockito.any(Token.class));
    }

    // test case: If plugin returns a null instance id, the method requestInstance() must throw an exception
    @Test(expected = UnexpectedException.class)
    public void testExceptionNullComputeInstanceId() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(ComputeOrder.class);
        Mockito.when(this.order.getFederationUser()).thenReturn(this.federationUser);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.COMPUTE);
        Mockito.when(computePlugin.requestInstance(Mockito.any(ComputeOrder.class), Mockito.any(Token.class))).thenReturn(null);

        // exercise
        this.localCloudConnector.requestInstance(order);
    }

    // test case: If plugin returns a null instance id, the method requestInstance() must throw an exception
    @Test(expected = UnexpectedException.class)
    public void testExceptionNullNetworkInstanceId() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(NetworkOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.NETWORK);
        Mockito.when(networkPlugin.requestInstance(Mockito.any(NetworkOrder.class), Mockito.any(Token.class))).thenReturn(null);

        // exercise
        this.localCloudConnector.requestInstance(order);
    }

    // test case: If plugin returns a null instance id, the method requestInstance() must throw an exception
    @Test(expected = UnexpectedException.class)
    public void testExceptionNullAttachmentInstanceId() throws FogbowRasException, UnexpectedException {
        // set up
        ComputeOrder source = Mockito.mock(ComputeOrder.class);
        VolumeOrder target = Mockito.mock(VolumeOrder.class);
        Mockito.when(source.getFederationUser()).thenReturn(this.federationUser);
        Mockito.when(source.getProvider()).thenReturn(FAKE_PROVIDER);
        Mockito.when(target.getFederationUser()).thenReturn(this.federationUser);
        Mockito.when(target.getProvider()).thenReturn(FAKE_PROVIDER);
        SharedOrderHolders.getInstance().getActiveOrdersMap().put(FAKE_COMPUTE_ID, source);
        SharedOrderHolders.getInstance().getActiveOrdersMap().put(FAKE_VOLUME_ID, target);
        this.order = Mockito.mock(AttachmentOrder.class);
        Mockito.when(this.order.getFederationUser()).thenReturn(this.federationUser);
        Mockito.when(this.order.getProvider()).thenReturn(FAKE_PROVIDER);
        Mockito.when(((AttachmentOrder) this.order).getComputeId()).thenReturn(FAKE_COMPUTE_ID);
        Mockito.when(((AttachmentOrder) this.order).getVolumeId()).thenReturn(FAKE_VOLUME_ID);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.ATTACHMENT);
        Mockito.when(attachmentPlugin.requestInstance(Mockito.any(AttachmentOrder.class), Mockito.any(Token.class))).thenReturn(null);

        // exercise
        this.localCloudConnector.requestInstance(order);

        // tear down
        SharedOrderHolders.getInstance().getActiveOrdersMap().clear();
    }

    // test case: If plugin returns a null instance id, the method requestInstance() must throw an exception
    @Test(expected = UnexpectedException.class)
    public void testExceptionNullVolumeInstanceId() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(VolumeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.VOLUME);
        Mockito.when(volumePlugin.requestInstance(Mockito.any(VolumeOrder.class), Mockito.any(Token.class))).thenReturn(null);

        // exercise
        this.localCloudConnector.requestInstance(order);
    }

    // test case: The order has an InstanceID, so the method getResourceInstance() is called.
    @Test
    public void testGetNetworkInstance() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(NetworkOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.NETWORK);
        Mockito.when(this.order.getInstanceId()).thenReturn(FAKE_INSTANCE_ID);
        Mockito.when(networkPlugin.getInstance(Mockito.any(String.class), Mockito.any(Token.class))).thenReturn(this.networkInstance);

        // exercise
        String returnedInstanceId = this.localCloudConnector.getInstance(order).getId();

        // verify
        Assert.assertEquals(FAKE_INSTANCE_ID, returnedInstanceId);
        Mockito.verify(computePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(1)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: The order has an InstanceID, so the method getResourceInstance() is called.
    @Test
    public void testGetVolumeInstance() throws FogbowRasException, UnexpectedException {

        //set up
        this.order = Mockito.mock(VolumeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.VOLUME);
        Mockito.when(this.order.getInstanceId()).thenReturn(FAKE_INSTANCE_ID);
        Mockito.when(volumePlugin.getInstance(Mockito.any(String.class), Mockito.any(Token.class))).thenReturn(this.volumeInstance);

        // exercise
        String returnedInstanceId = this.localCloudConnector.getInstance(order).getId();

        // verify
        Assert.assertEquals(FAKE_INSTANCE_ID, returnedInstanceId);
        Mockito.verify(computePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(1)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: The order has an InstanceID, so the method getResourceInstance() is called.
    @Test
    public void testGetAttachmentInstance() throws FogbowRasException, UnexpectedException {
        // set up
        ComputeOrder compute = Mockito.mock(ComputeOrder.class);
        VolumeOrder volume = Mockito.mock(VolumeOrder.class);
        SharedOrderHolders.getInstance().getActiveOrdersMap().put(FAKE_COMPUTE_ID, compute);
        SharedOrderHolders.getInstance().getActiveOrdersMap().put(FAKE_VOLUME_ID, volume);
        this.order = Mockito.mock(AttachmentOrder.class);
        Mockito.when(((AttachmentOrder) this.order).getVolumeId()).thenReturn(FAKE_VOLUME_ID);
        Mockito.when(((AttachmentOrder) this.order).getComputeId()).thenReturn(FAKE_COMPUTE_ID);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.ATTACHMENT);
        Mockito.when(this.order.getInstanceId()).thenReturn(FAKE_INSTANCE_ID);
        Mockito.when(attachmentPlugin.getInstance(Mockito.any(String.class), Mockito.any(Token.class)))
                .thenReturn(this.attachmentInstance);

        //exercise
        String returnedInstanceId = this.localCloudConnector.getInstance(order).getId();

        // verify
        Assert.assertEquals(FAKE_INSTANCE_ID, returnedInstanceId);
        Mockito.verify(computePlugin, times(0)).getInstance(Mockito.any(String.class),
                Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).getInstance(Mockito.any(String.class),
                Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(1)).getInstance(Mockito.any(String.class),
                Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).getInstance(Mockito.any(String.class),
                Mockito.any(Token.class));

        // tear down
        SharedOrderHolders.getInstance().getActiveOrdersMap().clear();
    }

    // test case: The order has an InstanceID, so the method getResourceInstance() is called.
    // addReverseTunnelInfo() is called in this case.
    @Test
    public void testGetComputeInstance() throws FogbowRasException, UnexpectedException {

        // set up

        // Avoid to test addReverseTunnelInfoMethod behaviour
        LocalCloudConnector localCloudConnectorSpy = Mockito.spy(this.localCloudConnector);

        this.order = Mockito.mock(ComputeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.COMPUTE);
        Mockito.when(this.order.getInstanceId()).thenReturn(FAKE_INSTANCE_ID);
        Mockito.when(computePlugin.getInstance(Mockito.any(String.class), Mockito.any(Token.class))).thenReturn(this.computeInstance);

        // exercise
        String returnedInstanceId = localCloudConnectorSpy.getInstance(order).getId();

        // verify
        Assert.assertEquals(FAKE_INSTANCE_ID, returnedInstanceId);
        Mockito.verify(computePlugin, times(1)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: If order instance is CLOSED, an exception must be throw
    @Test(expected = org.fogbowcloud.ras.core.exceptions.InstanceNotFoundException.class)
    public void testGetInstanceWithClosedOrder() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(NetworkOrder.class);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.CLOSED);

        //exercise
        this.localCloudConnector.getInstance(order);
    }

    // test case: If order instance is DEACTIVATED, an exception must be throw
    @Test(expected = org.fogbowcloud.ras.core.exceptions.InstanceNotFoundException.class)
    public void testGetInstanceWithDeactivatedOrder() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(NetworkOrder.class);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.DEACTIVATED);

        // exercise
        this.localCloudConnector.getInstance(order);
    }

    // test case: The order doesn't have an InstanceID, so an empty NetworkInstance is returned with the same id of order.
    // The order state is OPEN, so the instance state must be DISPATCHED.
    @Test
    public void testGetEmptyNetworkInstanceWithOpenOrder() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(NetworkOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.NETWORK);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.OPEN);

        // exercise
        Instance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.DISPATCHED, instance.getState());
        Mockito.verify(computePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty NetworkInstance is returned with the same id of order.
    // The order state is PENDING, so the instance state must be DISPATCHED.
    @Test
    public void testGetEmptyNetworkInstanceWithPendingOrder() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(NetworkOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.NETWORK);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.PENDING);

        // exercise
        Instance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.DISPATCHED, instance.getState());
        Mockito.verify(computePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty NetworkInstance is returned with the same id of order.
    // The order state is FAILED_AFTER_SUCCESSUL_REQUEST, so the instance state must be FAILED_AFTER_SUCCESSUL_REQUEST.
    @Test
    public void testGetEmptyNetworkInstanceWithFailedOrder() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(NetworkOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.NETWORK);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.FAILED_AFTER_SUCCESSUL_REQUEST);

        // exercise
        Instance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.FAILED, instance.getState());
        Mockito.verify(computePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty VolumeInstance is returned with the same id of order.
    // The order state is OPEN, so the instance state must be DISPATCHED.
    @Test
    public void testGetEmptyVolumeInstanceWithOpenOrder() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(VolumeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.VOLUME);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.OPEN);

        // exercise
        Instance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.DISPATCHED, instance.getState());
        Mockito.verify(computePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty VolumeInstance is returned with the same id of order.
    // The order state is PENDING, so the instance state must be DISPATCHED.
    @Test
    public void testGetEmptyVolumeInstanceWithPendingOrder() throws FogbowRasException, UnexpectedException {

        //set up
        this.order = Mockito.mock(VolumeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.VOLUME);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.PENDING);

        // exercise
        Instance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.DISPATCHED, instance.getState());
        Mockito.verify(computePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty VolumeInstance is returned with the same id of order.
    // The order state is FAILED_AFTER_SUCCESSUL_REQUEST, so the instance state must be FAILED_AFTER_SUCCESSUL_REQUEST.
    @Test
    public void testGetEmptyVolumeInstanceWithFailedOrder() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(VolumeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.VOLUME);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.FAILED_AFTER_SUCCESSUL_REQUEST);

        // exercise
        Instance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.FAILED, instance.getState());
        Mockito.verify(computePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty AttachmentInstance is returned with the same id of order.
    // The order state is OPEN, so the instance state must be DISPATCHED.
    @Test
    public void testGetEmptyAttachmentInstanceWithOpenOrder() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(AttachmentOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.ATTACHMENT);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.OPEN);

        // exercise
        Instance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.DISPATCHED, instance.getState());
        Mockito.verify(computePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty AttachmentInstance is returned with the same id of order.
    // The order state is PENDING, so the instance state must be DISPATCHED.
    @Test
    public void testGetEmptyAttachmentInstanceWithPendingOrder() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(AttachmentOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.ATTACHMENT);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.PENDING);

        // exercise
        Instance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.DISPATCHED, instance.getState());
        Mockito.verify(computePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty AttachmentInstance is returned with the same id of order.
    // The order state is FAILED_AFTER_SUCCESSUL_REQUEST, so the instance state must be FAILED_AFTER_SUCCESSUL_REQUEST.
    @Test
    public void testGetEmptyAttachmentInstanceWithFailedOrder() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(AttachmentOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.ATTACHMENT);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.FAILED_AFTER_SUCCESSUL_REQUEST);

        // exercise
        Instance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.FAILED, instance.getState());
        Mockito.verify(computePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty ComputeInstance is returned with the same id of order.
    // The order state is OPEN, so the instance state must be DISPATCHED.
    @Test
    public void testGetEmptyComputeInstanceWithOpenOrder() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(ComputeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.COMPUTE);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.OPEN);

        // exercise
        Instance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.DISPATCHED, instance.getState());
        Mockito.verify(computePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty ComputeInstance is returned with the same id of order.
    // The order state is PENDING, so the instance state must be DISPATCHED.
    @Test
    public void testGetEmptyComputeInstanceWithPendingOrder() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(ComputeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.COMPUTE);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.PENDING);

        // exercise
        Instance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.DISPATCHED, instance.getState());
        Mockito.verify(computePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty ComputeInstance is returned with the same id of order.
    // The order state is FAILED_AFTER_SUCCESSUL_REQUEST, so the instance state must be FAILED_AFTER_SUCCESSUL_REQUEST.
    @Test
    public void testGetEmptyComputeInstanceWithFailedOrder() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(ComputeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.COMPUTE);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.FAILED_AFTER_SUCCESSUL_REQUEST);

        // exercise
        Instance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.FAILED, instance.getState());
        Mockito.verify(computePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).getInstance(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: Try to delete an instance without instance id. Nothing happens
    @Test
    public void testDeleteInstanceWithoutInstanceID() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(ComputeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.COMPUTE);

        // exercise
        this.localCloudConnector.deleteInstance(order);

        // verify
        Mockito.verify(computePlugin, times(0)).deleteInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).deleteInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).deleteInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).deleteInstance(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: Deleting a compute instance with ID. Compute plugin must be called.
    @Test
    public void testDeleteComputeInstance() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(ComputeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.COMPUTE);
        Mockito.when(this.order.getInstanceId()).thenReturn(FAKE_INSTANCE_ID);

        // exercise
        this.localCloudConnector.deleteInstance(order);

        // verify
        Mockito.verify(computePlugin, times(1)).deleteInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).deleteInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).deleteInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).deleteInstance(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: Deleting a volume instance with ID. Volume plugin must be called.
    @Test
    public void testDeleteVolumeInstance() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(VolumeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.VOLUME);
        Mockito.when(this.order.getInstanceId()).thenReturn(FAKE_INSTANCE_ID);

        // exercise
        this.localCloudConnector.deleteInstance(order);

        // verify
        Mockito.verify(computePlugin, times(0)).deleteInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(1)).deleteInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).deleteInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).deleteInstance(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: Deleting a network instance with ID. Network plugin must be called.
    @Test
    public void testDeleteNetworkInstance() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(NetworkOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.NETWORK);
        Mockito.when(this.order.getInstanceId()).thenReturn(FAKE_INSTANCE_ID);

        // exercise
        this.localCloudConnector.deleteInstance(order);

        // verify
        Mockito.verify(computePlugin, times(0)).deleteInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).deleteInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(0)).deleteInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(1)).deleteInstance(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: Deleting a attachment instance with ID. Attachment plugin must be called.
    @Test
    public void testDeleteAttachmentInstance() throws FogbowRasException, UnexpectedException {

        // set up
        this.order = Mockito.mock(AttachmentOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.ATTACHMENT);
        Mockito.when(this.order.getInstanceId()).thenReturn(FAKE_INSTANCE_ID);

        // exercise
        this.localCloudConnector.deleteInstance(order);

        // verify
        Mockito.verify(computePlugin, times(0)).deleteInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(volumePlugin, times(0)).deleteInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(attachmentPlugin, times(1)).deleteInstance(Mockito.any(String.class), Mockito.any(Token.class));
        Mockito.verify(networkPlugin, times(0)).deleteInstance(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: Getting an image. Image plugin must be called
    @Test
    public void testGetImage() throws FogbowRasException, UnexpectedException {

        // set up
        Mockito.when(this.imagePlugin.getImage(Mockito.any(String.class), Mockito.any(Token.class))).thenReturn(this.image);

        // exercise
        String returnedImageId = this.localCloudConnector.getImage(FAKE_IMAGE_ID, federationUser).getId();

        // verify
        Assert.assertEquals(FAKE_IMAGE_ID, returnedImageId);
        Mockito.verify(imagePlugin, times(1)).getImage(Mockito.any(String.class), Mockito.any(Token.class));
    }

    // test case: Getting a null image. Image plugin must be called
    @Test
    public void testGetNullImage() throws FogbowRasException, UnexpectedException {

        // set up
        Mockito.when(this.imagePlugin.getImage(Mockito.any(String.class), Mockito.any(Token.class))).thenReturn(null);

        // exercise
        Image image = this.localCloudConnector.getImage(FAKE_IMAGE_ID, federationUser);

        // verify
        Assert.assertNull(image);
        Mockito.verify(imagePlugin, times(1)).getImage(Mockito.any(String.class), Mockito.any(Token.class));
    }


    // test case: Getting user compute quota. Compute quota plugin must be called.
    @Test
    public void testGetUserComputeQuota() throws FogbowRasException, UnexpectedException {

        // set up
        ComputeAllocation fakeTotalComputeAllocation = new ComputeAllocation(VCPU_TOTAL, RAM_TOTAL, INSTANCES_TOTAL);
        ComputeAllocation fakeUsedComputeAllocation = new ComputeAllocation(VCPU_USED, RAM_USED, INSTANCES_USED);
        ComputeQuota fakeComputeQuota = new ComputeQuota(fakeTotalComputeAllocation, fakeUsedComputeAllocation);
        Mockito.when(this.computeQuotaPlugin.getUserQuota(Mockito.any(Token.class))).thenReturn(fakeComputeQuota);

        // exercise
        ComputeQuota quota = (ComputeQuota) this.localCloudConnector.getUserQuota(federationUser, ResourceType.COMPUTE);

        // verify
        Assert.assertEquals(VCPU_TOTAL, quota.getTotalQuota().getvCPU());
        Assert.assertEquals(RAM_TOTAL, quota.getTotalQuota().getRam());
        Assert.assertEquals(INSTANCES_TOTAL, quota.getTotalQuota().getInstances());
        Assert.assertEquals(VCPU_USED, quota.getUsedQuota().getvCPU());
        Assert.assertEquals(RAM_USED, quota.getUsedQuota().getRam());
        Assert.assertEquals(INSTANCES_USED, quota.getUsedQuota().getInstances());
        Mockito.verify(computeQuotaPlugin, times(1)).getUserQuota(Mockito.any(Token.class));
    }

    // test case: If the instance type isn't of Compute type, an exception must be throw
    @Test(expected = UnexpectedException.class)
    public void testGetUserVolumeQuotaException() throws FogbowRasException, UnexpectedException {

        // exercise
        this.localCloudConnector.getUserQuota(federationUser, ResourceType.VOLUME);
    }

    // test case: If the instance type isn't of Compute type, an exception must be throw
    @Test(expected = UnexpectedException.class)
    public void testGetUserAttachmentQuotaException() throws FogbowRasException, UnexpectedException {

        // exercise
        this.localCloudConnector.getUserQuota(federationUser, ResourceType.ATTACHMENT);
    }

    // test case: If the instance type isn't of Compute type, an exception must be throw
    @Test(expected = UnexpectedException.class)
    public void testGetUserNetworkQuotaException() throws FogbowRasException, UnexpectedException {

        // exercise
        this.localCloudConnector.getUserQuota(federationUser, ResourceType.NETWORK);
    }

    // test case: Getting all images. Image plugin must be called
    @Test
    public void testGetAllImages() throws FogbowRasException, UnexpectedException {

        // set up
        Map<String, String> fakeMapImages = new HashMap<>();
        fakeMapImages.put(FAKE_IMAGE_ID, FAKE_IMAGE_NAME);
        Mockito.when(this.imagePlugin.getAllImages(Mockito.any(Token.class))).thenReturn(fakeMapImages);

        // exercise
        Map<String, String> returnedImages = this.localCloudConnector.getAllImages(federationUser);

        // verify
        Assert.assertEquals(FAKE_IMAGE_NAME, returnedImages.get(FAKE_IMAGE_ID));
        Assert.assertEquals(1, returnedImages.size());
        Mockito.verify(imagePlugin, times(1)).getAllImages(Mockito.any(Token.class));
    }

    // test case: The return of getAllImages must be null. Image plugin must be called.
    @Test
    public void testGetAllImagesNullReturn() throws FogbowRasException, UnexpectedException {

        // set up
        Mockito.when(this.imagePlugin.getAllImages(Mockito.any(Token.class))).thenReturn(null);

        // exercise
        Map<String, String> returnedImages = this.localCloudConnector.getAllImages(federationUser);

        // verify
        Assert.assertNull(returnedImages);
        Mockito.verify(imagePlugin, times(1)).getAllImages(Mockito.any(Token.class));
    }

    // test case: Generic requests should map the federationTokenUser and redirect the request to GenericRequestPlugin.
    @Test
    public void testGenericRequest() throws UnexpectedException, FogbowRasException {
        // set up
        Token tokenMock = Mockito.mock(Token.class);
        Mockito.doReturn(tokenMock).when(federationToLocalMapperPlugin).map(Mockito.eq(federationUser));
        Mockito.doReturn(Mockito.mock(GenericRequestResponse.class)).when(genericRequestPlugin).
                redirectGenericRequest(Mockito.any(GenericRequest.class), Mockito.eq(tokenMock));

        // exercise
        localCloudConnector.genericRequest(Mockito.mock(GenericRequest.class), federationUser);

        // verify
        Mockito.verify(federationToLocalMapperPlugin,
                Mockito.times(1)).map(Mockito.any(FederationUser.class));
        Mockito.verify(genericRequestPlugin, Mockito.times(1)).
                redirectGenericRequest(Mockito.any(GenericRequest.class), Mockito.eq(tokenMock));
    }

}
