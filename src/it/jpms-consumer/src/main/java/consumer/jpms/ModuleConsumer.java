package consumer.jpms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.alaptseu.jsonpresence.JsonFieldPresence;

/** Compiles on the module path against the library's automatic module name. */
public final class ModuleConsumer {
    private ModuleConsumer() {
    }

    public static void main(String[] arguments) {
        if (!compare().path("name").isNull()) {
            throw new IllegalStateException("Expected explicit null in effective JSON");
        }
        System.out.println("jpms-consumer-ok");
    }

    public static JsonNode compare() {
        ObjectMapper mapper = new ObjectMapper();
        return JsonFieldPresence.compareTrees(
                        mapper.createObjectNode().put("name", "before"),
                        mapper.createObjectNode().putNull("name"))
                .effectiveJson();
    }
}
