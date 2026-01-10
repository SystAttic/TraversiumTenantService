package traversium.tenantservice.exceptions

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import traversium.tenantservice.dto.ErrorResponse

@RestControllerAdvice
class RestExceptionHandler {

    @ExceptionHandler(TenantAlreadyExistsException::class)
    fun handleTenantAlreadyExists(
        ex: TenantAlreadyExistsException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            message = ex.message ?: "Tenant already exists",
            status = HttpStatus.CONFLICT.value(),
            errorCode = "TENANT_ALREADY_EXISTS",
            path = request.getDescription(false).removePrefix("uri=")
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse)
    }

    @ExceptionHandler(DomainAlreadyExistsException::class)
    fun handleDomainAlreadyExists(
        ex: DomainAlreadyExistsException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            message = ex.message ?: "Domain already exists",
            status = HttpStatus.CONFLICT.value(),
            errorCode = "DOMAIN_ALREADY_EXISTS",
            path = request.getDescription(false).removePrefix("uri=")
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse)
    }

    @ExceptionHandler(InvalidDomainException::class)
    fun handleInvalidDomain(
        ex: InvalidDomainException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            message = ex.message ?: "Invalid domain",
            status = HttpStatus.BAD_REQUEST.value(),
            errorCode = "INVALID_DOMAIN",
            path = request.getDescription(false).removePrefix("uri=")
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    @ExceptionHandler(TenantNotFoundException::class)
    fun handleTenantNotFound(
        ex: TenantNotFoundException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            message = ex.message ?: "Tenant not found",
            status = HttpStatus.NOT_FOUND.value(),
            errorCode = "TENANT_NOT_FOUND",
            path = request.getDescription(false).removePrefix("uri=")
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse)
    }

    @ExceptionHandler(TenantCreationException::class)
    fun handleTenantCreationException(
        ex: TenantCreationException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            message = ex.message ?: "Failed to create tenant",
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            errorCode = "TENANT_CREATION_FAILED",
            path = request.getDescription(false).removePrefix("uri=")
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }

    @ExceptionHandler(FirebaseException::class)
    fun handleFirebaseException(
        ex: FirebaseException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            message = ex.message ?: "Firebase operation failed",
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            errorCode = "FIREBASE_ERROR",
            path = request.getDescription(false).removePrefix("uri=")
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            message = ex.message ?: "An unexpected error occurred",
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            errorCode = "INTERNAL_SERVER_ERROR",
            path = request.getDescription(false).removePrefix("uri=")
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }
}

