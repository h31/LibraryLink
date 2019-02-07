import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.spbstu.kspt.librarylink.*;

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
        List<Argument> args = Collections.singletonList(new InPlaceArgument(url, null));
        MethodCallRequest request = new MethodCallRequest("get", "requests", args,
                false, false);
        ProcessExchangeResponse peResponse = exchange.makeRequest(request);
        logger.info("Wrote get");
        return new Response(peResponse.getAssignedID());
    }

    Response get(String url, Headers headers) {
        List<Argument> args = Arrays.asList(
                new InPlaceArgument(url, null),
                new PersistenceArgument(headers, "headers"));
        MethodCallRequest request = new MethodCallRequest("get", "requests", args,
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
            MethodCallRequest request = new MethodCallRequest("status_code", getAssignedID(),
                    Collections.emptyList(), false, true, true);
            ProcessExchangeResponse response = exchange.makeRequest(request);
            return (int) response.getReturnValue();
        }

        byte[] content() {
            MethodCallRequest request = new MethodCallRequest("content", getAssignedID(),
                    Collections.emptyList(), false, true, true);
            ProcessExchangeResponse response = exchange.makeRequest(request);
            return Base64.getDecoder().decode((String) Objects.requireNonNull(response.getReturnValue()));
        }

        Map<String, String> headers() {
            MethodCallRequest request = new MethodCallRequest("headers", getAssignedID(),
                    Collections.emptyList(),  false, true, true);
            ProcessExchangeResponse response = exchange.makeRequest(request);
            return (Map<String, String>) response.getReturnValue();
        }
    }

    public class Headers extends Handle {
        public Headers() {
            ProcessExchangeResponse response = exchange.makeRequest(new MethodCallRequest("dict"));
            String storedName = response.getAssignedID();
            registerReference(storedName);
        }

        void update(String key, String value) {
            exchange.makeRequest(new EvalRequest("%s.update{%s: %s}",
                    Arrays.asList(new PersistenceArgument(this, null), new InPlaceArgument(key, null), new InPlaceArgument(value, null)),
                    false, getAssignedID()));
        }
    }
}
