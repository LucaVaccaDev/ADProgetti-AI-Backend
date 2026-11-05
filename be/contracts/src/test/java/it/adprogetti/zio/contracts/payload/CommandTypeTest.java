package it.adprogetti.zio.contracts.payload;

import static org.assertj.core.api.Assertions.assertThat;

import it.adprogetti.zio.contracts.command.CommandType;
import org.junit.jupiter.api.Test;

class CommandTypeTest {

    @Test
    void knownCodeIsResolvedWithItsPermission() {
        assertThat(CommandType.fromCode("ai.model.assign")).contains(CommandType.AI_MODEL_ASSIGN);
        assertThat(CommandType.AI_MODEL_ASSIGN.requiredPermission()).isEqualTo("SETTINGS_MANAGE");
    }

    @Test
    void unknownCodeIsNotResolved() {
        assertThat(CommandType.fromCode("nas.delete")).isEmpty();
        assertThat(CommandType.fromCode(null)).isEmpty();
    }
}
