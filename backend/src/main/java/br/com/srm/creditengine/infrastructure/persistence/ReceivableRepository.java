package br.com.srm.creditengine.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceivableRepository extends JpaRepository<Receivable, Long> {}
