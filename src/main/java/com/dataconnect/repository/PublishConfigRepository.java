package com.dataconnect.repository;

import com.dataconnect.entity.PublishConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublishConfigRepository extends JpaRepository<PublishConfig, Long> {
    List<PublishConfig> findByStatus(String status);
    List<PublishConfig> findByFlowConfigId(Long flowConfigId);
    boolean existsByPort(Integer port);
    PublishConfig findByPort(Integer port);
}
