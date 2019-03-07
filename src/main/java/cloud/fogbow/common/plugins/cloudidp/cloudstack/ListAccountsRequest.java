package cloud.fogbow.common.plugins.cloudidp.cloudstack;

import cloud.fogbow.common.exceptions.InvalidParameterException;
import cloud.fogbow.common.util.connectivity.cloud.cloudstack.CloudStackRequest;

public class ListAccountsRequest extends CloudStackRequest {
    public static final String LIST_ACCOUNTS_COMMAND = "listAccounts";
    public static final String SESSION_KEY = "sessionkey";

    private ListAccountsRequest(Builder builder) throws InvalidParameterException {
        super(builder.cloudStackUrl);
        addParameter(SESSION_KEY, builder.sessionKey);
    }

    @Override
    public String toString() {
        return super.toString();
    }

    @Override
    public String getCommand() {
        return LIST_ACCOUNTS_COMMAND;
    }

    public static class Builder {
        private String cloudStackUrl;
        private String sessionKey;

        public Builder sessionKey(String sessionKey) {
            this.sessionKey = sessionKey;
            return this;
        }

        public ListAccountsRequest build(String cloudStackUrl) throws InvalidParameterException {
            this.cloudStackUrl = cloudStackUrl;
            return new ListAccountsRequest(this);
        }

    }
}
