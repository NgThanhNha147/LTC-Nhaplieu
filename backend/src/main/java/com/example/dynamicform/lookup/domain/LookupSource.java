package com.example.dynamicform.lookup.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lookup_source", schema = "app_meta")
@Getter @Setter @NoArgsConstructor
public class LookupSource {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 100) private String code;
    @Column(nullable = false) private String name;
    @Column(nullable = false, length = 30) private String sourceType;
    @Column(nullable = false, length = 63) private String sourceSchema;
    @Column(nullable = false, length = 63) private String sourceTable;
    @Column(nullable = false, length = 63) private String valueColumn;
    @Column(nullable = false, length = 63) private String labelColumn;
    @Column(length = 63) private String activeColumn;
    @Column(length = 63) private String parentColumn;
    @Column(length = 63) private String sortColumn;
    @Column(nullable = false, length = 30) private String status;
    @Column(nullable = false) private Instant createdAt;
}
