package com.publifi.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Persistable;

import java.util.Objects;
import java.util.UUID;

/**
 * Base de todas las entidades con ID UUID.
 * <p>
 * El ID se genera en Java al crear el objeto (no en la BD). Ventajas: lo conocemos antes de
 * guardar (útil para claves de idempotencia y eventos) y equals/hashCode son estables.
 * <p>
 * Implementa {@link Persistable} para que Spring Data sepa que una entidad recién creada es
 * nueva aunque ya tenga ID; si no, haría un SELECT innecesario antes de cada INSERT.
 */
@MappedSuperclass
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Transient
    private boolean isNew = true;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        return Objects.equals(id, ((BaseEntity) o).getId());
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }
}
