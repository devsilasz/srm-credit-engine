package br.com.srm.creditengine;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Relógio real com deslocamento, para envelhecer a taxa de câmbio sem esperar. */
public class MutableClock extends Clock {

  private final ZoneId zone = ZoneId.of("America/Sao_Paulo");
  private volatile Duration offset = Duration.ZERO;

  public void advance(Duration duration) {
    offset = offset.plus(duration);
  }

  public void reset() {
    offset = Duration.ZERO;
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Instant instant() {
    return Instant.now().plus(offset);
  }
}
