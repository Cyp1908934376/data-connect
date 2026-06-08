package com.dataconnect.repository;

import com.dataconnect.entity.FlowConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FlowConfigRepository extends JpaRepository<FlowConfig, Long> {
}
