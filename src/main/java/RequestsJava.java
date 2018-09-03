import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class RequestsJava {
    private Logger logger = LoggerFactory.getLogger(RequestsJava.class);

    private ProcessDataExchange exchange;

    public RequestsJava() {
        ReceiverRunner runner = LibraryLink.runner;
        exchange = new SimpleTextProcessDataExchange(runner, runner.getRequestGenerator());
    }

    public RequestsJava(ReceiverRunner runner,
                        ProcessDataExchange exchange) {
        this.exchange = exchange;
    }

    Response get(String url) {
        List<Argument> args = Collections.singletonList(new StringArgument(url, null));
        Request request = new Request("get", "requests", args, "requests",
                false, false);
        ProcessExchangeResponse peResponse = exchange.makeRequest(request);
        logger.info("Wrote get");
        return new Response(peResponse.getAssignedID());
    }

    Response get(String url, Headers headers) {
        List<Argument> args = Arrays.asList(
                new StringArgument(url, null),
                new ReferenceArgument(headers.storedName, "headers"));
        Request request = new Request("get", "requests", args, "requests",
                false, false);
        ProcessExchangeResponse peResponse = exchange.makeRequest(request);
        logger.info("Wrote get");
        return new Response(peResponse.getAssignedID());
    }

    Headers getHeaders() {
        return new Headers();
    }

    class Response extends Handle {
        Response(String storedName) {
            super(storedName);
        }
        int statusCode() {
            Request request = new Request("status_code", getAssignedID(),
                    Collections.emptyList(), "", false, true, true);
            ProcessExchangeResponse response = exchange.makeRequest(request);
            return (int) response.getReturnValue();
        }

        byte[] content() {
            Request request = new Request("content", getAssignedID(),
                    Collections.emptyList(), "", false, true, true);
            ProcessExchangeResponse response = exchange.makeRequest(request);
            return Base64.getDecoder().decode((String) Objects.requireNonNull(response.getReturnValue()));
        }

        Map<String, String> headers() {
            Request request = new Request("headers", getAssignedID(),
                    Collections.emptyList(), "", false, true, true);
            ProcessExchangeResponse response = exchange.makeRequest(request);
            return (Map<String, String>) response.getReturnValue();
        }
    }

    class Headers extends Handle {
        String storedName;
        public Headers() {
            ProcessExchangeResponse response = exchange.makeRequest(new Request("dict"));
            storedName = response.getAssignedID();
            registerReference(storedName);
        }

        void update(String key, String value) {
            exchange.makeRequest(new Request("update", storedName,
                    Collections.singletonList(new RawArgument(String.format("{\"%s\": \"%s\"}", key, value), null))));
        }
    }
}
