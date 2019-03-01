package cloud.fogbow.common.util.cloud.openstack;

import cloud.fogbow.common.constants.HttpConstants;
import cloud.fogbow.common.constants.HttpMethod;
import cloud.fogbow.common.constants.OpenStackConstants;
import cloud.fogbow.common.models.CloudToken;
import cloud.fogbow.common.util.connectivity.GenericRequest;
import cloud.fogbow.common.util.connectivity.CloudHttpClient;

import java.util.Map;

public class OpenStackHttpClient extends CloudHttpClient {

    public OpenStackHttpClient() {
    }

    @Override
    public GenericRequest prepareRequest(GenericRequest genericRequest, CloudToken token) {
        GenericRequest clonedRequest = (GenericRequest) genericRequest.clone();
        Map<String, String> headers = clonedRequest.getHeaders();

        headers.put(OpenStackConstants.X_AUTH_TOKEN_KEY, token.getTokenValue());

        if (genericRequest.getMethod().equals(HttpMethod.GET)
                || genericRequest.getMethod().equals(HttpMethod.POST)) {
            headers.put(HttpConstants.CONTENT_TYPE_KEY, HttpConstants.JSON_CONTENT_TYPE_KEY);
            headers.put(HttpConstants.ACCEPT_KEY, HttpConstants.JSON_CONTENT_TYPE_KEY);
        }
        return clonedRequest;
    }
}
