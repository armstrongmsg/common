package cloud.fogbow.common.util.connectivity;

public class GenericRequestHttpResponse extends GenericRequestResponse {
    private int httpCode;

    public GenericRequestHttpResponse(String content, int httpCode) {
        super(content);
        this.httpCode = httpCode;
    }

    public int getHttpCode() {
        return httpCode;
    }

    public void setHttpCode(int httpCode) {
        this.httpCode = httpCode;
    }
}
