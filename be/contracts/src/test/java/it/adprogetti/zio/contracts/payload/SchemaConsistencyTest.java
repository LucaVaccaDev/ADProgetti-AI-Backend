package it.adprogetti.zio.contracts.payload;

import static org.assertj.core.api.Assertions.assertThat;

import it.adprogetti.zio.contracts.message.CommandAck;
import it.adprogetti.zio.contracts.message.EdgeHeartbeat;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The published JSON schemas must list exactly the fields of the Java records. */
class SchemaConsistencyTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private void check(String file, Class<? extends Record> type) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/schemas/" + file)) {
            assertThat(in).as(file).isNotNull();
            JsonNode schema = MAPPER.readTree(in);
            Set<String> fields = new TreeSet<>();
            Arrays.stream(type.getRecordComponents()).forEach(c -> fields.add(c.getName()));
            Set<String> required = new TreeSet<>();
            schema.get("required").forEach(n -> required.add(n.asString()));
            Set<String> properties = new TreeSet<>(schema.get("properties").propertyNames());
            assertThat(required).as(file + " required").isEqualTo(fields);
            assertThat(properties).as(file + " properties").isEqualTo(fields);
            assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
        }
    }

    @Test
    void schemasMatchRecords() throws Exception {
        check("ai-model-test.v1.json", AiModelTestPayload.class);
        check("ai-model-assign.v1.json", AiModelAssignPayload.class);
        check("command-ack.v1.json", CommandAck.class);
        check("edge-heartbeat.v1.json", EdgeHeartbeat.class);
    }
}
