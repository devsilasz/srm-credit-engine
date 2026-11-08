package br.com.srm.creditengine.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

  Optional<Settlement> findByIdempotencyKey(UUID idempotencyKey);
}
