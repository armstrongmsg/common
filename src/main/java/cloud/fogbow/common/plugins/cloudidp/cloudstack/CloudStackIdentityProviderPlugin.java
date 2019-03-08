package cloud.fogbow.common.plugins.cloudidp.cloudstack;

import cloud.fogbow.common.constants.CloudStackConstants;
import cloud.fogbow.common.constants.HttpMethod;
import cloud.fogbow.common.constants.Messages;
import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InvalidParameterException;
import cloud.fogbow.common.exceptions.UnexpectedException;
import cloud.fogbow.common.models.CloudStackUser;
import cloud.fogbow.common.plugins.cloudidp.CloudIdentityProviderPlugin;
import cloud.fogbow.common.util.HttpErrorToFogbowExceptionMapper;
import cloud.fogbow.common.util.connectivity.HttpRequestClient;
import cloud.fogbow.common.util.connectivity.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class CloudStackIdentityProviderPlugin implements CloudIdentityProviderPlugin<CloudStackUser> {
    private static final Logger LOGGER = Logger.getLogger(CloudStackIdentityProviderPlugin.class);

    private String cloudStackUrl;

    // Used in tests by Mockito
    public CloudStackIdentityProviderPlugin() {
    }

    public CloudStackIdentityProviderPlugin(String cloudStackUrl) {
        this.cloudStackUrl = cloudStackUrl;
    }

    @Override
    public CloudStackUser getCloudUser(Map<String, String> credentials) throws FogbowException {
        if ((credentials == null) || (credentials.get(CloudStackConstants.Identity.USERNAME_KEY_JSON) == null) ||
                (credentials.get(CloudStackConstants.Identity.PASSWORD_KEY_JSON) == null) ||
                credentials.get(CloudStackConstants.Identity.DOMAIN_KEY_JSON) == null) {
            throw new InvalidParameterException(Messages.Exception.NO_USER_CREDENTIALS);
        }

        LoginRequest request = createLoginRequest(credentials);
        // Since all cloudstack requests params are passed via url args, we do not need to
        // send a valid json body in the post request
        HttpResponse response = HttpRequestClient.doGenericRequest(HttpMethod.POST,
                request.getUriBuilder().toString(), new HashMap<>(), new HashMap<>());

        if (response.getHttpCode() > HttpStatus.SC_OK) {
            FogbowException exception = HttpErrorToFogbowExceptionMapper.map(response.getHttpCode(), response.getContent());
            throw exception;
        } else {
            LoginResponse loginResponse = LoginResponse.fromJson(response.getContent());
            return getCloudStackUser(loginResponse.getSessionKey());
        }
    }

    private LoginRequest createLoginRequest(Map<String, String> credentials) throws InvalidParameterException {
        String userId = credentials.get(CloudStackConstants.Identity.USERNAME_KEY_JSON);
        String password = credentials.get(CloudStackConstants.Identity.PASSWORD_KEY_JSON);
        String domain = credentials.get(CloudStackConstants.Identity.DOMAIN_KEY_JSON);

        LoginRequest loginRequest = new LoginRequest.Builder()
                .username(userId)
                .password(password)
                .domain(domain)
                .build(this.cloudStackUrl);

        return loginRequest;
    }

    private CloudStackUser getCloudStackUser(String sessionKey) throws FogbowException {
        ListAccountsRequest request = new ListAccountsRequest.Builder()
                .sessionKey(sessionKey)
                .build(this.cloudStackUrl);

        HttpResponse response = HttpRequestClient.doGenericRequest(HttpMethod.GET,
                request.getUriBuilder().toString(), new HashMap<>(), new HashMap<>());

        if (response.getHttpCode() > HttpStatus.SC_OK) {
            FogbowException exception = HttpErrorToFogbowExceptionMapper.map(response.getHttpCode(), response.getContent());
            throw exception;
        } else {
            try {
                ListAccountsResponse listAccountsResponse = ListAccountsResponse.fromJson(response.getContent());
                // Considering only one account/user per request
                ListAccountsResponse.User user = listAccountsResponse.getAccounts().get(0).getUsers().get(0);
                // Keeping the token-value separator as expected by the other cloudstack plugins
                String tokenValue = user.getApiKey() + CloudStackConstants.KEY_VALUE_SEPARATOR + user.getSecretKey();
                String userId = user.getId();
                String userName = getUserName(user);
                return new CloudStackUser(userId, userName, tokenValue);
            } catch (Exception e) {
                LOGGER.error(Messages.Error.UNABLE_TO_GET_TOKEN_FROM_JSON, e);
                throw new UnexpectedException(Messages.Error.UNABLE_TO_GET_TOKEN_FROM_JSON, e);
            }
        }
    }

    private String getUserName(ListAccountsResponse.User user) {
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        return (firstName != null && lastName != null) ? firstName + " " + lastName : user.getUsername();
    }
}