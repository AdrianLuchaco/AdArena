package com.adarena.site.repository;

import com.adarena.site.domain.SitePreview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SitePreviewRepository extends JpaRepository<SitePreview, UUID> {

    Optional<SitePreview> findByUrl(String url);

    List<SitePreview> findByUrlIn(Collection<String> urls);
}
