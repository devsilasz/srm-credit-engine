package br.com.srm.creditengine.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FxRateRepository extends JpaRepository<FxRate, Long> {

  /** O {@code id} desempata duas taxas com a mesma vigência: vale a inserida por último. */
  Optional<FxRate> findFirstByPairAndValidFromLessThanEqualOrderByValidFromDescIdDesc(
      String pair, Instant now);
}
