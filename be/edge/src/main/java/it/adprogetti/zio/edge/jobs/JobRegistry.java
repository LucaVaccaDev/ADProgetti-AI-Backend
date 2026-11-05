package it.adprogetti.zio.edge.jobs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The handlers by job type. Two handlers for the same type is a configuration error. */
@Component
public class JobRegistry {

    private final Map<String, JobHandler> handlers = new HashMap<>();

    JobRegistry(List<JobHandler> all) {
        for (JobHandler handler : all) {
            if (handlers.putIfAbsent(handler.type(), handler) != null) {
                throw new IllegalStateException("Two handlers for job type " + handler.type());
            }
        }
    }

    public Optional<JobHandler> find(String type) {
        return Optional.ofNullable(handlers.get(type));
    }
}
