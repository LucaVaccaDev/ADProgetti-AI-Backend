package it.adprogetti.zio.edge.nas;

import java.time.Clock;

/** Builds the NAS reader without Spring, for trials on a real disk. The constructors are package-private on purpose. */
public final class NasTestFactory {

    private NasTestFactory() {
    }

    public static NasReader reader(NasProperties props) {
        return new NasReader(new NasPathResolver(props, new NasAvailability(props, Clock.systemUTC())), props);
    }
}
