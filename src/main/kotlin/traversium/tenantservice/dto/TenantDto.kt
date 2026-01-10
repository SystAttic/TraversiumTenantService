package traversium.tenantservice.dto

import java.time.OffsetDateTime

data class TenantDto(
    val id: Long,
    val tenantId: String,
    val firebaseTenantId: String?, // Firebase-generated tenant ID for authentication
    val name: String,
    val description: String?,
    val domain: String,
    val createdAt: OffsetDateTime,
    val adminEmail: String?,
    val isActive: Boolean
)

