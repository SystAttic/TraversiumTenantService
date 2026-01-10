package traversium.tenantservice.exceptions

open class TenantException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class TenantAlreadyExistsException(message: String) : TenantException(message)

class InvalidDomainException(message: String) : TenantException(message)

class DomainAlreadyExistsException(message: String) : TenantException(message)

class TenantNotFoundException(message: String) : TenantException(message)

class TenantCreationException(message: String, cause: Throwable? = null) : TenantException(message, cause)

class FirebaseException(message: String, cause: Throwable? = null) : TenantException(message, cause)

