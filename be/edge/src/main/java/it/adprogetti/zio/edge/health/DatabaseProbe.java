package it.adprogetti.zio.edge.health;

import it.adprogetti.zio.contracts.message.ServiceStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class DatabaseProbe implements ServiceProbe {

    private final JdbcClient jdbc;

    DatabaseProbe(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ServiceStatus probe() {
        try {
            jdbc.sql("select 1").query(Integer.class).single();
            return new ServiceStatus("database", ServiceStatus.State.UP, null);
        } catch (RuntimeException e) {
            return new ServiceStatus("database", ServiceStatus.State.DOWN, "UNREACHABLE");
        }
    }
}
