package com.agentassist.configregistry;

import org.hibernate.boot.model.relational.Namespace;
import org.hibernate.boot.model.relational.Sequence;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.spi.SchemaFilter;
import org.hibernate.tool.schema.spi.SchemaFilterProvider;

/**
 * Keeps Hibernate's ddl-auto tooling AWAY from the Liquibase-owned aa_*
 * tables (plan §4.5, decided the hard way): with {@code ddl-auto: update},
 * Hibernate 6 compared the entity mappings against the Liquibase DDL and
 * issued {@code alter table ... modify column ... varchar(255)} against the
 * CLOB columns — MySQL refused where long prompt data already existed
 * ("Data too long"), but would have silently SHRUNK columns whose seeded
 * values happened to fit. Excluding the tables here means Liquibase is the
 * single owner of the aa_* schema; legacy tables keep their historical
 * ddl-auto behaviour.
 *
 * <p>Registered via {@code hibernate.hbm2ddl.schema_filter_provider} in
 * application.yaml.</p>
 */
public class AaSchemaFilterProvider implements SchemaFilterProvider {

    private static final SchemaFilter EXCLUDE_AA = new SchemaFilter() {
        @Override
        public boolean includeNamespace(Namespace namespace) {
            return true;
        }

        @Override
        public boolean includeTable(Table table) {
            return table.getName() == null
                    || !table.getName().toLowerCase().startsWith("aa_");
        }

        @Override
        public boolean includeSequence(Sequence sequence) {
            return true;
        }
    };

    @Override
    public SchemaFilter getCreateFilter() {
        return EXCLUDE_AA;
    }

    @Override
    public SchemaFilter getDropFilter() {
        return EXCLUDE_AA;
    }

    @Override
    public SchemaFilter getMigrateFilter() {
        return EXCLUDE_AA;
    }

    @Override
    public SchemaFilter getValidateFilter() {
        return EXCLUDE_AA;
    }

    @Override
    public SchemaFilter getTruncatorFilter() {
        return EXCLUDE_AA;
    }
}
