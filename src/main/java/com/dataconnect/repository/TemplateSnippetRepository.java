package com.dataconnect.repository;

import com.dataconnect.entity.TemplateSnippet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemplateSnippetRepository extends JpaRepository<TemplateSnippet, Long> {

    List<TemplateSnippet> findAllByOrderByGroupNameAscSortOrderAsc();
}
