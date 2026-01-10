package traversium.tenantservice.db.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import traversium.tenantservice.db.model.Tenant

@Repository
interface TenantRepository : JpaRepository<Tenant, Long> {
    fun findByTenantId(tenantId: String): Tenant?
    fun findByName(name: String): Tenant?
    fun findByDomain(domain: String): Tenant?
    fun existsByTenantId(tenantId: String): Boolean
    fun existsByName(name: String): Boolean
    fun existsByDomain(domain: String): Boolean
}

