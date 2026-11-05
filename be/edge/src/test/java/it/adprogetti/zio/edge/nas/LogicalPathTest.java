package it.adprogetti.zio.edge.nas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class LogicalPathTest {

    @Test
    void aCleanPathIsParsed() {
        LogicalPath p = LogicalPath.parse("archivio:/Commesse/2026/Rossi & Figli");
        assertThat(p.share()).isEqualTo("archivio");
        assertThat(p.segments()).containsExactly("Commesse", "2026", "Rossi & Figli");
        assertThat(p.text()).isEqualTo("archivio:/Commesse/2026/Rossi & Figli");
        assertThat(LogicalPath.parse("archivio:/").segments()).isEmpty();
        assertThat(LogicalPath.parse("archivio:/a/b/").segments()).containsExactly("a", "b");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "archivio:/../outside/secret.txt", "archivio:/a/../../b", "archivio:/a/./b", "archivio:/..", "archivio:/.",
            "archivio:/a//b", "archivio://a", "/etc/passwd", "archivio:etc/passwd", "archivio:\\Windows\\System32",
            "archivio:/a\\..\\b", "C:/Windows", "archivio:/C:/Windows", "", "archivio", ":/a", "Archivio:/a",
            "archivio:/a/b\u0000c", "archivio:/a/b\nc", "archivio:/a/b\tc", "archivio:/bad name ",
            "archivio:/trailing.", "archivio:/\u2215etc", "archivio:/a\uff0f..\uff0fb", "archivio:/\uff0e\uff0e",
            "archivio:/.zio-tender", "archivio:/.zio-part-1", "archivio:/a/\u007fb" })
    void dangerousPathsAreRefused(String text) {
        assertThatThrownBy(() -> LogicalPath.parse(text)).isInstanceOf(NasException.class)
                .extracting(e -> ((NasException) e).code()).isEqualTo(NasException.Code.INVALID_PATH);
    }

    @Test
    void nullAndOversizedPathsAreRefused() {
        assertThatThrownBy(() -> LogicalPath.parse(null)).isInstanceOf(NasException.class);
        assertThatThrownBy(() -> LogicalPath.parse("archivio:/" + "a".repeat(256))).isInstanceOf(NasException.class);
        assertThatThrownBy(() -> LogicalPath.parse("archivio:/" + "a/".repeat(600))).isInstanceOf(NasException.class);
    }

    @Test
    void percentEncodingIsNotDecoded() {
        // a literal file name, not a traversal: it will be looked up as is, inside the root
        assertThat(LogicalPath.parse("archivio:/..%2f..%2fetc").segments()).containsExactly("..%2f..%2fetc");
    }

    @Test
    void startsWithComparesWholeSegments() {
        LogicalPath p = LogicalPath.parse("archivio:/Protocolli2/x");
        assertThat(p.startsWith(List.of("Protocolli"))).isFalse();
        assertThat(LogicalPath.parse("archivio:/Protocolli/x").startsWith(List.of("Protocolli"))).isTrue();
    }
}
