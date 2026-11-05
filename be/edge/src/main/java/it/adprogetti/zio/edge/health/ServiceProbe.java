package it.adprogetti.zio.edge.health;

import it.adprogetti.zio.contracts.message.ServiceStatus;

/** Reports the health of one Edge service in the heartbeat. Must be fast and must not throw. */
public interface ServiceProbe {

    ServiceStatus probe();
}
