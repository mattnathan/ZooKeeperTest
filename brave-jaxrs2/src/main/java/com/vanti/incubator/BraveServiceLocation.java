package com.vanti.incubator;

import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;

/**
 * Represents the location(s) of brave services.
 *
 * @author Matt
 */
public class BraveServiceLocation {
  public ImmutableList<HostAndPort> getLocations() {
    return ImmutableList.of(
        // this is a made up location that will be tried before the good location
        HostAndPort.fromParts("localhost", 6666),
        HostAndPort.fromParts("localhost", 8080),
        HostAndPort.fromParts("localhost", 9999)
    );
  }
}
