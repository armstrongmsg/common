package cloud.fogbow.common.util.cloud.cloudstack;

import cloud.fogbow.common.exceptions.UnauthorizedRequestException;
import cloud.fogbow.common.models.CloudToken;
import cloud.fogbow.common.util.connectivity.CloudHttpClient;
import cloud.fogbow.common.util.connectivity.GenericRequest;
import org.apache.http.client.utils.URIBuilder;

import java.net.URISyntaxException;

public class CloudStackHttpClient extends CloudHttpClient {

    public CloudStackHttpClient() {
    }

    @Override
    public GenericRequest prepareRequest(GenericRequest genericRequest, CloudToken token) {
        try {
            GenericRequest clonedRequest = (GenericRequest) genericRequest.clone();
            URIBuilder uriBuilder = new URIBuilder(clonedRequest.getUrl());
            CloudStackUrlUtil.sign(uriBuilder, token.getTokenValue());

            clonedRequest.setUrl(uriBuilder.toString());
            return clonedRequest;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (UnauthorizedRequestException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

}
