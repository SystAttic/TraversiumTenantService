package traversium.tenantservice.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import traversium.tenantservice.dto.CreateTenantRequest
import traversium.tenantservice.dto.CreateAdminUserRequest
import traversium.tenantservice.dto.ErrorResponse
import traversium.tenantservice.dto.TenantDto
import traversium.tenantservice.exceptions.TenantException
import traversium.tenantservice.service.TenantService

@RestController
@RequestMapping("/rest/v1/tenants")
@Tag(name = "Tenants", description = "Endpoints for managing tenants")
class TenantController(
    private val tenantService: TenantService
) {
    private val logger = LoggerFactory.getLogger(TenantController::class.java)

    @PostMapping
    @Operation(
        operationId = "createTenant",
        summary = "Create a new tenant",
        description = "Creates a new tenant with a dedicated schema and Firebase tenant",
        responses = [
            ApiResponse(
                responseCode = "201",
                description = "Tenant created successfully",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = TenantDto::class)
                )]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Bad Request - Invalid tenant data",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = ErrorResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "409",
                description = "Conflict - Tenant or domain already exists",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = ErrorResponse::class)
                )]
            )
        ]
    )
    fun createTenant(@RequestBody request: CreateTenantRequest): ResponseEntity<TenantDto> {
        val tenant = tenantService.createTenant(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(tenant)
    }

    @GetMapping
    @Operation(
        operationId = "getAllTenants",
        summary = "Get all tenants",
        description = "Retrieves a list of all tenants"
    )
    fun getAllTenants(): ResponseEntity<List<TenantDto>> {
        val tenants = tenantService.getAllTenants()
        return ResponseEntity.ok(tenants)
    }

    @GetMapping("/name/{name}")
    @Operation(
        operationId = "getTenantByName",
        summary = "Get tenant by name",
        description = "Retrieves a tenant by its name and returns the tenant ID (Firebase tenant ID) for authentication",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Tenant found",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = TenantDto::class)
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Tenant not found",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = ErrorResponse::class)
                )]
            )
        ]
    )
    fun getTenantByName(@PathVariable name: String): ResponseEntity<TenantDto> {
        logger.info("Getting tenant by name: $name")
        val tenant = tenantService.getTenantByName(name)
        return ResponseEntity.ok(tenant)
    }

    // TODO: Step 3 - Implement getTenantById
    // @GetMapping("/{tenantId}")
    // @Operation(
    //     operationId = "getTenantById",
    //     summary = "Get tenant by ID",
    //     description = "Retrieves a tenant by its ID"
    // )
    // fun getTenantById(@PathVariable tenantId: String): ResponseEntity<TenantDto> {
    //     val tenant = tenantService.getTenantById(tenantId)
    //     return ResponseEntity.ok(tenant)
    // }

    // TODO: Step 4 - Implement getTenantByDomain
    // @GetMapping("/domain/{domain}")
    // @Operation(
    //     operationId = "getTenantByDomain",
    //     summary = "Get tenant by domain",
    //     description = "Retrieves a tenant by its domain"
    // )
    // fun getTenantByDomain(@PathVariable domain: String): ResponseEntity<TenantDto> {
    //     val tenant = tenantService.getTenantByDomain(domain)
    //     return ResponseEntity.ok(tenant)
    // }
    
    @PostMapping("/{tenantId}/admin")
    @Operation(
        operationId = "createAdminUser",
        summary = "Create admin user for tenant",
        description = "Creates an admin user for an existing tenant",
        responses = [
            ApiResponse(
                responseCode = "201",
                description = "Admin user created successfully"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Tenant not found",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = ErrorResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "409",
                description = "Admin user already exists",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = ErrorResponse::class)
                )]
            )
        ]
    )
    fun createAdminUser(
        @PathVariable tenantId: String,
        @RequestBody request: CreateAdminUserRequest
    ): ResponseEntity<Map<String, String>> {
        logger.info("Creating admin user for tenant: $tenantId")
        val adminFirebaseId = tenantService.createAdminUser(tenantId, request.email, request.password, request.displayName)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("adminFirebaseId" to adminFirebaseId))
    }
}

