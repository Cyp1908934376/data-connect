package com.dataconnect.repository;

import com.dataconnect.entity.FlowConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlowConfigRepository extends JpaRepository<FlowConfig, Long> {

    List<FlowConfig> findByNameContaining(String keyword);
}
