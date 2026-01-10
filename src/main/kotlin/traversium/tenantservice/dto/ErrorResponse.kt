package traversium.tenantservice.dto

import java.time.OffsetDateTime

data class ErrorResponse(
    val message: String,
    val status: Int,
    val errorCode: String,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
    val path: String? = null
)

