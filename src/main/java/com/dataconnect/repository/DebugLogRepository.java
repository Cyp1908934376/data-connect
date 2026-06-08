package com.dataconnect.repository;

import com.dataconnect.entity.DebugLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DebugLogRepository extends JpaRepository<DebugLog, Long> {

    List<DebugLog> findByDsConfigIdOrderByCreateTimeDesc(Long dsConfigId);
}
