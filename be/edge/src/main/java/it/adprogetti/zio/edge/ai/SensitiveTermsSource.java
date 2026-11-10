package it.adprogetti.zio.edge.ai;

import java.util.List;

/**
 * Names that must never reach an external AI service (Allegato A.2): job names, clients, and so on. The projects
 * module will provide the real list; until then there are none, and the other filter rules still apply.
 */
public interface SensitiveTermsSource {

    List<String> terms();
}
