package cloud.fogbow.common.util.cloud.opennebula;

import cloud.fogbow.common.constants.Messages;
import cloud.fogbow.common.exceptions.*;
import cloud.fogbow.common.util.PropertiesUtil;
import org.apache.log4j.Logger;
import org.opennebula.client.*;
import org.opennebula.client.group.Group;
import org.opennebula.client.group.GroupPool;
import org.opennebula.client.image.Image;
import org.opennebula.client.image.ImagePool;
import org.opennebula.client.secgroup.SecurityGroup;
import org.opennebula.client.template.TemplatePool;
import org.opennebula.client.user.User;
import org.opennebula.client.user.UserPool;
import org.opennebula.client.vm.VirtualMachine;
import org.opennebula.client.vnet.VirtualNetwork;

import java.util.Properties;

public class OpenNebulaClientFactory {

    private final static Logger LOGGER = Logger.getLogger(OpenNebulaClientFactory.class);

    public static final String RESPONSE_NOT_AUTHORIZED = "Not authorized";
    private static final String RESPONSE_DONE = "DONE";
    public static final String FIELD_RESPONSE_LIMIT = "limit";
    public static final String FIELD_RESPONSE_QUOTA = "quota";
    public static final String RESPONSE_NOT_ENOUGH_FREE_MEMORY = "Not enough free memory";
    public static final String RESPONSE_NO_SPACE_LEFT_ON_DEVICE = "No space left on device";
    private static final String OPENNEBULA_RPC_ENDPOINT_URL = "opennebula_rpc_endpoint";
    public static final int CHMOD_PERMISSION_744 = 744;

    private String endpoint;

	public OpenNebulaClientFactory(String confFilePath) {
		Properties properties = PropertiesUtil.readProperties(confFilePath);
		this.endpoint = properties.getProperty(OPENNEBULA_RPC_ENDPOINT_URL);
	}

	public Client createClient(String federationTokenValue) throws UnexpectedException {
		try {
			return new Client(federationTokenValue, this.endpoint);
		} catch (ClientConfigurationException e) {
			LOGGER.error(Messages.Error.ERROR_WHILE_CREATING_CLIENT, e);
			throw new UnexpectedException();
		}
	}

    public Group createGroup(Client client, int groupId) throws UnauthorizedRequestException, UnexpectedException {
		GroupPool groupPool = (GroupPool) generateOnePool(client, GroupPool.class);
    	OneResponse response = groupPool.info();
        if (response.isError()) {
            LOGGER.error(String.format(Messages.Error.ERROR_WHILE_GETTING_INFO_ABOUT_GROUP_S_S, response.getErrorMessage()));
            throw new UnexpectedException(response.getErrorMessage());
        }
    	Group group = groupPool.getById(groupId);
    	if (group == null){
			throw new UnauthorizedRequestException();
		}
    	group.info();		
		return group;
    }

	public ImagePool createImagePool(Client client) throws UnexpectedException {
        ImagePool imagePool = (ImagePool) generateOnePool(client, ImagePool.class);
		OneResponse response = imagePool.infoAll();
		if (response.isError()) {
			LOGGER.error(String.format(Messages.Error.ERROR_WHILE_GETTING_INFO_ABOUT_TEMPLATES_S, response.getErrorMessage()));
			throw new UnexpectedException(response.getErrorMessage());
		}
		LOGGER.info(String.format(Messages.Info.TEMPLATE_POOL_LENGTH_S, imagePool.getLength()));
		return imagePool;
	}

	public VirtualMachine createVirtualMachine(Client client, String virtualMachineId)
			throws UnauthorizedRequestException, InstanceNotFoundException, InvalidParameterException {

        VirtualMachine virtualMachine = (VirtualMachine) generateOnePoolElement(client, virtualMachineId, VirtualMachine.class);
		OneResponse response = virtualMachine.info();

		if (response.isError()) {
			String message = response.getErrorMessage();
			LOGGER.error(message);
			// Not authorized to perform
			if (message.contains(RESPONSE_NOT_AUTHORIZED)) {
				throw new UnauthorizedRequestException();
			}
			// Error getting virtual machine
			throw new InstanceNotFoundException(message);
		} else if (RESPONSE_DONE.equals(virtualMachine.stateStr())) {
			// The instance is not active anymore
			throw new InstanceNotFoundException();
		}
		return virtualMachine;
	}

	public VirtualNetwork createVirtualNetwork(Client client, String virtualNetworkId)
			throws UnauthorizedRequestException, InstanceNotFoundException, InvalidParameterException {

        VirtualNetwork virtualNetwork = (VirtualNetwork) generateOnePoolElement(client, virtualNetworkId, VirtualNetwork.class);
		OneResponse response = virtualNetwork.info();
		if (response.isError()) {
			String message = response.getErrorMessage();
			LOGGER.error(message);
			// Not authorized to perform
			if (message.contains(RESPONSE_NOT_AUTHORIZED)) {
				throw new UnauthorizedRequestException();
			}
			// Error getting virtual network
			throw new InstanceNotFoundException(message);
		}
		return virtualNetwork;
	}

	public TemplatePool createTemplatePool(Client client) throws UnexpectedException {
		TemplatePool templatePool = (TemplatePool) generateOnePool(client, TemplatePool.class);
		OneResponse response = templatePool.infoAll();
		if (response.isError()) {
			LOGGER.error(String.format(Messages.Error.ERROR_WHILE_GETTING_INFO_ABOUT_TEMPLATES_S, response.getErrorMessage()));
			throw new UnexpectedException(response.getErrorMessage());
		}
		LOGGER.info(String.format(Messages.Info.TEMPLATE_POOL_LENGTH_S, templatePool.getLength()));
		return templatePool;
	}

	public UserPool createUserPool(Client client) throws UnexpectedException {
		UserPool userpool = (UserPool) generateOnePool(client, UserPool.class);
 		OneResponse response = userpool.info();
 		if (response.isError()) {
 			LOGGER.error(String.format(Messages.Error.ERROR_WHILE_GETTING_INFO_ABOUT_USERS_S, response.getErrorMessage()));
			throw new UnexpectedException(response.getErrorMessage());
 		}
 		LOGGER.info(String.format(Messages.Info.USER_POOL_LENGTH_S, userpool.getLength()));
		return userpool;
	}

    public User getUser(UserPool userPool, String userName) throws UnauthorizedRequestException, UnexpectedException {
 		User user = findUserByName(userPool, userName);
 		OneResponse response = user.info();
 		if (response.isError()) {
 			LOGGER.error(String.format(Messages.Error.ERROR_WHILE_GETTING_INFO_ABOUT_USER_S_S, user.getId(), response.getErrorMessage()));
            throw new UnexpectedException(response.getErrorMessage());
 		}
 		return user;
    }   
    
    public SecurityGroup createSecurityGroup(Client client, String securityGroupId)
    			throws UnauthorizedRequestException, InvalidParameterException, InstanceNotFoundException {

    	SecurityGroup securityGroup = (SecurityGroup) generateOnePoolElement(client, securityGroupId, SecurityGroup.class);
 		OneResponse response = securityGroup.info();
 		if (response.isError()) {
			String message = response.getErrorMessage();
			LOGGER.error(message);
			// Not authorized to perform
			if (message.contains(RESPONSE_NOT_AUTHORIZED)) {
				throw new UnauthorizedRequestException();
			}
			// Error getting virtual network
			throw new InstanceNotFoundException(message);
 		}
 		return securityGroup;
    }

	public String allocateImage(Client client, String template, Integer datastoreId) throws InvalidParameterException {
		OneResponse response = Image.allocate(client, template, datastoreId);
		if (response.isError()) {
			String message = response.getErrorMessage();
			LOGGER.error(String.format(Messages.Error.ERROR_WHILE_CREATING_IMAGE_FROM_TEMPLATE_S, template));
			LOGGER.error(String.format(Messages.Error.ERROR_MESSAGE_IS_S, message));
			throw new InvalidParameterException(message);
		}
		Image.chmod(client, response.getIntMessage(), CHMOD_PERMISSION_744);
		return response.getMessage();
	}

	public String allocateSecurityGroup(Client client, String template) throws InvalidParameterException {
		OneResponse response = SecurityGroup.allocate(client, template);
		if (response.isError()) {
			String message = response.getErrorMessage();
			LOGGER.error(String.format(Messages.Error.ERROR_WHILE_CREATING_SECURITY_GROUPS_FROM_TEMPLATE_S, template));
			LOGGER.error(String.format(Messages.Error.ERROR_MESSAGE_IS_S, message));
			throw new InvalidParameterException();
		}
		SecurityGroup.chmod(client, response.getIntMessage(), CHMOD_PERMISSION_744);
		return response.getMessage();
	}


	public String allocateVirtualMachine(Client client, String template)
			throws QuotaExceededException, NoAvailableResourcesException, InvalidParameterException {
		
		OneResponse response = VirtualMachine.allocate(client, template);
		if (response.isError()) {
			String message = response.getErrorMessage();
			LOGGER.error(String.format(Messages.Error.ERROR_WHILE_INSTATIATING_INSTANCE_FROM_TEMPLATE_S, template));
			LOGGER.error(String.format(Messages.Error.ERROR_MESSAGE_IS_S, message));
			if (message.contains(FIELD_RESPONSE_LIMIT) && message.contains(FIELD_RESPONSE_QUOTA)) {
				throw new QuotaExceededException();
			}
			if ((message.contains(RESPONSE_NOT_ENOUGH_FREE_MEMORY))
					|| (message.contains(RESPONSE_NO_SPACE_LEFT_ON_DEVICE))) {
				throw new NoAvailableResourcesException();
			}
			throw new InvalidParameterException(message);
		}
		VirtualMachine.chmod(client, response.getIntMessage(), CHMOD_PERMISSION_744);
		return response.getMessage();
	}

	public String allocateVirtualNetwork(Client client, String template) throws InvalidParameterException {
		OneResponse response = VirtualNetwork.allocate(client, template);
		if (response.isError()) {
			String message = response.getErrorMessage();
			LOGGER.error(String.format(Messages.Error.ERROR_WHILE_CREATING_NETWORK_FROM_TEMPLATE_S, template));
			LOGGER.error(String.format(Messages.Error.ERROR_MESSAGE_IS_S, message));
			throw new InvalidParameterException();
		}
		VirtualNetwork.chmod(client, response.getIntMessage(), CHMOD_PERMISSION_744);
		return response.getMessage();
	}

	private User findUserByName(UserPool userPool, String userName) throws UnauthorizedRequestException {
		for (User user : userPool) {
			if (userName.equals(user.getName())){
				return user;
			}
		}
		throw new UnauthorizedRequestException();
	}


	public PoolElement generateOnePoolElement(Client client, String poolElementId, Class classType) throws InvalidParameterException {
		int id;
		try {
			id = Integer.parseInt(poolElementId);
		} catch (Exception e) {
			LOGGER.error(String.format(Messages.Error.ERROR_WHILE_CONVERTING_INSTANCE_ID, poolElementId), e);
			throw new InvalidParameterException(Messages.Exception.INVALID_PARAMETER);
		}

		if (classType.isAssignableFrom(SecurityGroup.class)) {
			return new SecurityGroup(id , client);
		} else if (classType.isAssignableFrom(VirtualMachine.class)) {
		    return new VirtualMachine(id, client);
        } else if (classType.isAssignableFrom(VirtualNetwork.class)) {
		    return new VirtualNetwork(id, client);
        }

		return null;
	}

	public Pool generateOnePool(Client client, Class classType) {
		if (classType.isAssignableFrom(TemplatePool.class)) {
			return new GroupPool(client);
		} else if (classType.isAssignableFrom(GroupPool.class)) {
			return new GroupPool(client);
		} else if (classType.isAssignableFrom(TemplatePool.class)) {
		    return new TemplatePool(client);
        } else if (classType.isAssignableFrom(ImagePool.class)) {
            return new ImagePool(client);
        } else if (classType.isAssignableFrom(UserPool.class)) {
		    return new UserPool(client);
        }

		return null;
	}

}
