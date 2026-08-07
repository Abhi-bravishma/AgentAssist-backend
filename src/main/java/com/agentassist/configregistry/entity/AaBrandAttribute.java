package com.agentassist.configregistry.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Brand value for a project ({@code project_id} set) or the global default
 * ({@code project_id} NULL). Plain FK column, no association — every lookup
 * is by (projectId, attrKey).
 */
@Entity
@Table(name = "aa_brand_attribute")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AaBrandAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "attr_key", nullable = false, length = 100)
    private String attrKey;

    @Column(name = "attr_value", nullable = false)
    private String attrValue;
}
