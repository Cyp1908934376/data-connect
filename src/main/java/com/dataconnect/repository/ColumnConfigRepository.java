package com.dataconnect.repository;

import com.dataconnect.entity.ColumnConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ColumnConfigRepository extends JpaRepository<ColumnConfig, Long> {
    List<ColumnConfig> findByNameContaining(String keyword);
    List<ColumnConfig> findByColumnType(String columnType);
}
