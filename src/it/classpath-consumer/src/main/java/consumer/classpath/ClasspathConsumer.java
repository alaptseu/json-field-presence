package consumer.classpath;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.alaptseu.jsonpresence.JsonComparisonReport;
import io.github.alaptseu.jsonpresence.JsonFieldPresence;
import io.github.alaptseu.jsonpresence.JsonParsingOptions;

/** Compiles against the library and its transitive Jackson dependency. */
public final class ClasspathConsumer {
    private ClasspathConsumer() {
    }

    public static void main(String[] arguments) {
        if (!compare().path("name").isNull()) {
            throw new IllegalStateException("Expected explicit null in effective JSON");
        }
        System.out.println("classpath-consumer-ok");
    }

    public static JsonNode compare() {
        JsonFieldPresence analyzer = JsonFieldPresence.builder()
                .parsingOptions(JsonParsingOptions.builder()
                        .maxDocumentLength(1_000)
                        .build())
                .build();
        JsonComparisonReport report = analyzer.analyzeJson(
                "{\"name\":\"before\"}", "{\"name\":null}");
        return report.effectiveJson();
    }
}
