package it.adprogetti.zio.edge.ai;

import java.util.List;
import org.springframework.stereotype.Component;

/** Default until the projects module exists; the projects module replaces this bean with the real list. */
@Component
class EmptySensitiveTerms implements SensitiveTermsSource {

    @Override
    public List<String> terms() {
        return List.of();
    }
}
