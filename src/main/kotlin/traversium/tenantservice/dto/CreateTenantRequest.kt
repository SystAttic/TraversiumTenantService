package traversium.tenantservice.dto

data class CreateTenantRequest(
    val name: String, // 4-20 characters, lowercase
    val description: String? = null
)

