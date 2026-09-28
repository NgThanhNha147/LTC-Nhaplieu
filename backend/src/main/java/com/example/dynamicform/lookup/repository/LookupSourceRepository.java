package com.example.dynamicform.lookup.repository;

import com.example.dynamicform.lookup.domain.LookupSource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface LookupSourceRepository extends JpaRepository<LookupSource, UUID> {
    Optional<LookupSource> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
}
