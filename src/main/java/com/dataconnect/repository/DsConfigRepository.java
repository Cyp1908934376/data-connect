package com.dataconnect.repository;

import com.dataconnect.entity.DsConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DsConfigRepository extends JpaRepository<DsConfig, Long> {
    List<DsConfig> findBySourceType(String sourceType);
}
