package traversium.tenantservice.dto

data class CreateAdminUserRequest(
    val email: String,
    val password: String,
    val displayName: String
)

