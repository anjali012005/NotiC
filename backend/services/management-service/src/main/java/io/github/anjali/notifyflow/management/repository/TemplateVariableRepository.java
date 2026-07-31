package io.github.anjali.notifyflow.management.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.anjali.notifyflow.management.entity.TemplateVariable;

public interface TemplateVariableRepository extends JpaRepository<TemplateVariable, UUID> {
    List<TemplateVariable> findByVersionId(UUID versionId);
}
