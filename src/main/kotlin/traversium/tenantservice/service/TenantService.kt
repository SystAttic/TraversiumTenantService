package traversium.tenantservice.service

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.UserRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import traversium.commonmultitenancy.FlywayTenantMigration
import traversium.tenantservice.db.model.Tenant
import traversium.tenantservice.db.repository.TenantRepository
import traversium.tenantservice.dto.CreateTenantRequest
import traversium.tenantservice.dto.TenantDto
import traversium.tenantservice.exceptions.*
import traversium.tenantservice.service.FirebaseManagementService
import java.time.OffsetDateTime

/**
 * Service for managing tenants
 * Building step by step with comprehensive logging
 */
@Service
class TenantService(
    private val tenantRepository: TenantRepository,
    private val firebaseManagementService: FirebaseManagementService,
    private val flywayTenantMigration: FlywayTenantMigration,
    private val firebaseAuth: FirebaseAuth
) {
    private val logger = LoggerFactory.getLogger(TenantService::class.java)

    /**
     * Get all tenants from both database and Firebase
     * Step 1: Basic implementation with comprehensive logging
     */
    fun getAllTenants(): List<TenantDto> {
        logger.info("=== Starting getAllTenants() ===")
        
        try {
            // Step 1: Get tenants from database
            logger.info("Step 1: Fetching tenants from database...")
            val dbTenants = try {
                val tenants = tenantRepository.findAll()
                logger.info("Step 1 SUCCESS: Found ${tenants.size} tenant(s) in database")
                tenants.forEachIndexed { index, tenant ->
                    logger.debug("  DB Tenant[$index]: id=${tenant.id}, tenantId=${tenant.tenantId}, name=${tenant.name}, domain=${tenant.domain}, firebaseTenantId=${tenant.firebaseTenantId}")
                }
                tenants
            } catch (e: Exception) {
                logger.error("Step 1 FAILED: Error fetching tenants from database", e)
                throw RuntimeException("Failed to fetch tenants from database: ${e.message}", e)
            }
            
            // Step 2: Get tenants from Firebase
            logger.info("Step 2: Fetching tenants from Firebase Identity Platform...")
            val firebaseTenants = try {
                val tenants = firebaseManagementService.listAllTenants()
                logger.info("Step 2 SUCCESS: Found ${tenants.size} tenant(s) in Firebase")
                tenants.forEachIndexed { index, tenant ->
                    logger.debug("  Firebase Tenant[$index]: tenantId=${tenant.tenantId}, displayName=${tenant.displayName}, allowPasswordSignup=${tenant.isPasswordSignInAllowed}")
                }
                tenants
            } catch (e: Exception) {
                logger.error("Step 2 FAILED: Error fetching tenants from Firebase", e)
                // Don't throw - we can still return DB tenants even if Firebase fails
                logger.warn("Continuing with database tenants only due to Firebase error")
                emptyList()
            }
            
            // Step 3: Create mapping of database tenants by firebaseTenantId
            logger.info("Step 3: Creating database tenant mapping by firebaseTenantId...")
            val dbTenantMapByFirebaseId = try {
                val map = dbTenants.filter { it.firebaseTenantId != null }
                    .associateBy { it.firebaseTenantId!! }
                logger.info("Step 3 SUCCESS: Created map with ${map.size} DB tenant(s) that have firebaseTenantId")
                map.keys.forEach { key ->
                    logger.debug("  DB tenant with firebaseTenantId: $key")
                }
                map
            } catch (e: Exception) {
                logger.error("Step 3 FAILED: Error creating DB tenant map", e)
                emptyMap()
            }
            
            // Step 4: Process all Firebase tenants and sync missing ones to DB
            logger.info("Step 4: Processing all Firebase tenants and syncing missing ones to database...")
            val syncedTenants = try {
                val synced = mutableListOf<Tenant>()
                var syncedCount = 0
                
                firebaseTenants.forEachIndexed { index, firebaseTenant ->
                    logger.debug("  Processing Firebase tenant[$index]: tenantId=${firebaseTenant.tenantId}, displayName=${firebaseTenant.displayName}")
                    
                    val dbTenant = dbTenantMapByFirebaseId[firebaseTenant.tenantId]
                    
                    if (dbTenant == null) {
                        // Firebase tenant not in DB - sync it
                        logger.info("    Firebase tenant '${firebaseTenant.tenantId}' not found in DB, syncing...")
                        val newTenant = syncFirebaseTenantToDb(firebaseTenant)
                        synced.add(newTenant)
                        syncedCount++
                        logger.info("    SUCCESS: Synced Firebase tenant '${firebaseTenant.tenantId}' to database")
                    } else {
                        logger.debug("    Firebase tenant '${firebaseTenant.tenantId}' already exists in DB")
                        synced.add(dbTenant)
                    }
                }
                
                logger.info("Step 4 SUCCESS: Processed ${synced.size} tenant(s), synced $syncedCount new tenant(s) to database")
                synced
            } catch (e: Exception) {
                logger.error("Step 4 FAILED: Error syncing Firebase tenants to database", e)
                // Continue with DB tenants only if sync fails
                logger.warn("Continuing with database tenants only due to sync error")
                dbTenants
            }
            
            // Step 5: Convert all tenants to DTOs
            logger.info("Step 5: Converting all tenants to DTOs...")
            val result = try {
                val dtos = syncedTenants.mapIndexed { index, tenant ->
                    logger.debug("  Converting tenant[$index]: tenantId=${tenant.tenantId}, name=${tenant.name}")
                    
                    val dto = TenantDto(
                        id = tenant.id ?: 0L, // Use 0 for newly synced tenants that haven't been saved yet
                        tenantId = tenant.tenantId,
                        firebaseTenantId = tenant.firebaseTenantId,
                        name = tenant.name,
                        description = tenant.description,
                        domain = tenant.domain,
                        createdAt = tenant.createdAt,
                        adminEmail = tenant.adminEmail,
                        isActive = tenant.isActive
                    )
                    
                    logger.debug("    Created DTO: tenantId=${dto.tenantId}, name=${dto.name}, domain=${dto.domain}")
                    dto
                }
                
                logger.info("Step 5 SUCCESS: Created ${dtos.size} DTO(s)")
                dtos
            } catch (e: Exception) {
                logger.error("Step 5 FAILED: Error creating DTOs", e)
                throw RuntimeException("Failed to create DTOs: ${e.message}", e)
            }
            
            logger.info("=== getAllTenants() COMPLETED SUCCESSFULLY: Returning ${result.size} tenant(s) ===")
            return result
            
        } catch (e: Exception) {
            logger.error("=== getAllTenants() FAILED ===", e)
            throw e
        }
    }
    
    /**
     * Syncs a Firebase tenant to the database if it doesn't exist
     * This acts as a backup/recovery mechanism
     */
    @Transactional
    private fun syncFirebaseTenantToDb(firebaseTenant: com.google.firebase.auth.multitenancy.Tenant): Tenant {
        logger.info("Syncing Firebase tenant to DB: tenantId=${firebaseTenant.tenantId}, displayName=${firebaseTenant.displayName}")
        
        try {
            // Generate a tenantId from displayName or use Firebase tenantId as fallback
            val tenantId = firebaseTenant.displayName
                .lowercase()
                .replace(Regex("[^a-z0-9-]"), "-")
                .replace(Regex("-+"), "-")
                .trim('-')
                .takeIf { it.isNotEmpty() } 
                ?: firebaseTenant.tenantId
            
            // Check if tenant with this tenantId already exists
            val existingTenant = tenantRepository.findByTenantId(tenantId)
            if (existingTenant != null) {
                logger.info("Tenant with tenantId '$tenantId' already exists, updating firebaseTenantId...")
                existingTenant.firebaseTenantId = firebaseTenant.tenantId
                return tenantRepository.save(existingTenant)
            }
            
            // Create new tenant
            val newTenant = Tenant(
                tenantId = tenantId,
                name = firebaseTenant.displayName,
                description = "Synced from Firebase Identity Platform",
                domain = tenantId, // Use tenantId as domain
                createdAt = OffsetDateTime.now(),
                adminEmail = null,
                adminFirebaseId = null,
                firebaseTenantId = firebaseTenant.tenantId,
                isActive = true
            )
            
            val savedTenant = tenantRepository.save(newTenant)
            logger.info("SUCCESS: Synced Firebase tenant '${firebaseTenant.tenantId}' to DB with tenantId='$tenantId'")
            return savedTenant
            
        } catch (e: Exception) {
            logger.error("FAILED: Error syncing Firebase tenant to DB", e)
            // Return a transient tenant object even if save fails, so we can still show it
            return Tenant(
                tenantId = firebaseTenant.tenantId,
                name = firebaseTenant.displayName,
                description = "Failed to sync to database",
                domain = firebaseTenant.tenantId,
                createdAt = OffsetDateTime.now(),
                adminEmail = null,
                adminFirebaseId = null,
                firebaseTenantId = firebaseTenant.tenantId,
                isActive = true
            )
        }
    }
    
    /**
     * Create a new tenant
     * Step 2: Implementation with comprehensive logging
     */
    @Transactional
    fun createTenant(request: CreateTenantRequest): TenantDto {
        logger.info("=== Starting createTenant() ===")
        logger.info("Request: name=${request.name}, description=${request.description}")
        
        try {
            // Step 1: Validate and normalize name (4-20 characters, lowercase)
            logger.info("Step 1: Validating and normalizing name...")
            val normalizedName = request.name.lowercase().trim()
            if (normalizedName.length < 4 || normalizedName.length > 20) {
                logger.error("Step 1 FAILED: Name '${request.name}' length is ${normalizedName.length}, must be 4-20 characters")
                throw InvalidDomainException("Name must be between 4 and 20 characters long")
            }
            if (!normalizedName.matches(Regex("^[a-z0-9-]+$"))) {
                logger.error("Step 1 FAILED: Name '${request.name}' contains invalid characters")
                throw InvalidDomainException("Name must contain only lowercase letters, numbers, and hyphens")
            }
            
            // Check if name already exists
            logger.info("Step 1a: Checking if name '$normalizedName' already exists...")
            val nameExists = try {
                val exists = tenantRepository.existsByName(normalizedName)
                if (exists) {
                    logger.error("Step 1a FAILED: Name '$normalizedName' already exists")
                } else {
                    logger.info("Step 1a SUCCESS: Name '$normalizedName' is available")
                }
                exists
            } catch (e: Exception) {
                logger.error("Step 1a FAILED: Error checking name existence", e)
                throw RuntimeException("Failed to check name existence: ${e.message}", e)
            }
            
            if (nameExists) {
                throw TenantAlreadyExistsException("Tenant with name '$normalizedName' already exists")
            }
            
            logger.info("Step 1 SUCCESS: Name is valid and available: '$normalizedName'")
            
            // Step 2: Create Firebase tenant first to get the Firebase-generated tenant ID
            logger.info("Step 2: Creating Firebase tenant with displayName='$normalizedName'...")
            val firebaseTenant = try {
                val created = firebaseManagementService.createTenant(normalizedName)
                logger.info("Step 2 SUCCESS: Created Firebase tenant with tenantId=${created.tenantId}, displayName=${created.displayName}")
                created
            } catch (e: Exception) {
                logger.error("Step 2 FAILED: Error creating Firebase tenant", e)
                throw TenantCreationException("Failed to create Firebase tenant: ${e.message}", e)
            }
            
            // Step 3: Use Firebase tenant ID as our tenantId
            val tenantId = firebaseTenant.tenantId
            logger.info("Step 3: Using Firebase tenantId='$tenantId' as our tenantId")
            
            // Step 4: Check if tenant ID already exists (shouldn't happen, but safety check)
            logger.info("Step 4: Checking if tenantId '$tenantId' already exists...")
            val tenantIdExists = try {
                val exists = tenantRepository.existsByTenantId(tenantId)
                if (exists) {
                    logger.error("Step 4 FAILED: TenantId '$tenantId' already exists")
                } else {
                    logger.info("Step 4 SUCCESS: TenantId '$tenantId' is available")
                }
                exists
            } catch (e: Exception) {
                logger.error("Step 4 FAILED: Error checking tenantId existence", e)
                throw RuntimeException("Failed to check tenantId existence: ${e.message}", e)
            }
            
            if (tenantIdExists) {
                // If tenant exists, try to get auth for it and return existing tenant
                logger.warn("TenantId '$tenantId' already exists in database, returning existing tenant")
                val existingTenant = tenantRepository.findByTenantId(tenantId)
                if (existingTenant != null) {
                    return toDto(existingTenant)
                }
                throw TenantAlreadyExistsException("Tenant with ID '$tenantId' already exists")
            }
            
            // Step 5: Create tenant record in database
            logger.info("Step 5: Creating tenant record in database...")
            val tenant = try {
                val newTenant = Tenant(
                    tenantId = tenantId, // Use Firebase tenant ID
                    name = request.name, // Keep original name (not normalized) for display
                    description = request.description,
                    domain = tenantId, // Use tenantId as domain for backwards compatibility
                    adminEmail = null,
                    adminFirebaseId = null,
                    firebaseTenantId = tenantId, // Same as tenantId since we use Firebase ID
                    isActive = true
                )
                val saved = tenantRepository.save(newTenant)
                logger.info("Step 5 SUCCESS: Created tenant record with id=${saved.id}, tenantId=${saved.tenantId}")
                saved
            } catch (e: Exception) {
                logger.error("Step 5 FAILED: Error creating tenant record", e)
                // Rollback: Firebase tenant is created, but we can't delete it easily
                logger.warn("WARNING: Firebase tenant '${firebaseTenant.tenantId}' was created but database record failed")
                throw TenantCreationException("Failed to create tenant record: ${e.message}", e)
            }
            
            // Step 6: Create tenant schema using Flyway migration
            logger.info("Step 6: Creating tenant schema using Flyway migration for tenantId='$tenantId'...")
            try {
                flywayTenantMigration.migrateTenant(tenantId)
                logger.info("Step 6 SUCCESS: Tenant schema created and migrated")
            } catch (e: Exception) {
                logger.error("Step 6 FAILED: Error creating tenant schema", e)
                // Rollback: delete tenant record
                logger.info("Rolling back: Deleting tenant record due to schema creation failure...")
                try {
                    tenantRepository.delete(tenant)
                    logger.info("Rollback SUCCESS: Tenant record deleted")
                } catch (rollbackError: Exception) {
                    logger.error("Rollback FAILED: Error deleting tenant record during rollback", rollbackError)
                }
                throw TenantCreationException("Failed to create tenant schema: ${e.message}", e)
            }
            
            // Step 7: Convert to DTO
            logger.info("Step 7: Converting tenant to DTO...")
            val dto = try {
                val result = toDto(tenant)
                logger.info("Step 7 SUCCESS: Created DTO for tenantId=${result.tenantId}")
                result
            } catch (e: Exception) {
                logger.error("Step 7 FAILED: Error creating DTO", e)
                throw RuntimeException("Failed to create DTO: ${e.message}", e)
            }
            
            logger.info("=== createTenant() COMPLETED SUCCESSFULLY: Created tenant with tenantId='$tenantId' (from Firebase) ===")
            return dto
            
        } catch (e: TenantException) {
            logger.error("=== createTenant() FAILED: TenantException ===", e)
            throw e
        } catch (e: Exception) {
            logger.error("=== createTenant() FAILED: Unexpected error ===", e)
            throw TenantCreationException("Failed to create tenant: ${e.message}", e)
        }
    }
    
    /**
     * Create admin user for an existing tenant
     * Step 5: Implementation with comprehensive logging
     */
    @Transactional
    fun createAdminUser(tenantId: String, email: String, password: String, displayName: String): String {
        logger.info("=== Starting createAdminUser() ===")
        logger.info("Request: tenantId=$tenantId, email=$email, displayName=$displayName")
        
        try {
            // Step 1: Find tenant in database
            logger.info("Step 1: Finding tenant with tenantId='$tenantId' in database...")
            val tenant = try {
                val found = tenantRepository.findByTenantId(tenantId)
                if (found == null) {
                    logger.error("Step 1 FAILED: Tenant with tenantId='$tenantId' not found")
                    throw TenantNotFoundException("Tenant with ID '$tenantId' not found")
                }
                logger.info("Step 1 SUCCESS: Found tenant with id=${found.id}, tenantId=${found.tenantId}, firebaseTenantId=${found.firebaseTenantId}")
                found
            } catch (e: TenantNotFoundException) {
                throw e
            } catch (e: Exception) {
                logger.error("Step 1 FAILED: Error finding tenant", e)
                throw RuntimeException("Failed to find tenant: ${e.message}", e)
            }
            
            // Step 2: Check if admin user already exists
            logger.info("Step 2: Checking if admin user already exists for tenantId='$tenantId'...")
            if (tenant.adminFirebaseId != null) {
                logger.error("Step 2 FAILED: Admin user already exists with adminFirebaseId='${tenant.adminFirebaseId}'")
                throw TenantCreationException("Admin user already exists for tenant '$tenantId'")
            }
            logger.info("Step 2 SUCCESS: No admin user exists yet")
            
            // Step 3: Get Firebase tenant ID
            logger.info("Step 3: Getting Firebase tenant ID...")
            val firebaseTenantId = tenant.firebaseTenantId
            if (firebaseTenantId == null) {
                logger.error("Step 3 FAILED: Tenant has no firebaseTenantId")
                throw TenantCreationException("Tenant '$tenantId' has no Firebase tenant ID. Please create Firebase tenant first.")
            }
            logger.info("Step 3 SUCCESS: Found firebaseTenantId='$firebaseTenantId'")
            
            // Step 4: Get Firebase Auth instance for the tenant
            logger.info("Step 4: Getting Firebase Auth instance for tenant firebaseTenantId='$firebaseTenantId'...")
            val tenantAuth = try {
                val auth = firebaseAuth.tenantManager.getAuthForTenant(firebaseTenantId)
                logger.info("Step 4 SUCCESS: Obtained Firebase Auth instance for tenant")
                auth
            } catch (e: FirebaseAuthException) {
                logger.error("Step 4 FAILED: Error getting Firebase Auth instance", e)
                throw FirebaseException("Failed to get Firebase Auth for tenant: ${e.message}", e)
            } catch (e: Exception) {
                logger.error("Step 4 FAILED: Unexpected error getting Firebase Auth instance", e)
                throw FirebaseException("Failed to get Firebase Auth for tenant: ${e.message}", e)
            }
            
            // Step 5: Create admin user in Firebase
            logger.info("Step 5: Creating admin user in Firebase with email='$email'...")
            val userRecord = try {
                val createRequest = UserRecord.CreateRequest()
                    .setEmail(email)
                    .setPassword(password)
                    .setDisplayName(displayName)
                    .setEmailVerified(true)
                
                val created = tenantAuth.createUser(createRequest)
                logger.info("Step 5 SUCCESS: Created admin user with uid='${created.uid}', email='${created.email}'")
                created
            } catch (e: FirebaseAuthException) {
                logger.error("Step 5 FAILED: Error creating admin user in Firebase", e)
                throw FirebaseException("Failed to create admin user in Firebase: ${e.message}", e)
            } catch (e: Exception) {
                logger.error("Step 5 FAILED: Unexpected error creating admin user", e)
                throw FirebaseException("Failed to create admin user: ${e.message}", e)
            }
            
            // Step 6: Set custom claims for admin role
            logger.info("Step 6: Setting custom claims for admin user uid='${userRecord.uid}'...")
            try {
                val customClaims = mapOf(
                    "role" to "admin",
                    "tenantId" to tenantId
                )
                tenantAuth.setCustomUserClaims(userRecord.uid, customClaims)
                logger.info("Step 6 SUCCESS: Set custom claims: role=admin, tenantId=$tenantId")
            } catch (e: Exception) {
                logger.error("Step 6 FAILED: Error setting custom claims", e)
                // Don't fail the whole operation if claims fail - user is still created
                logger.warn("WARNING: Admin user created but custom claims not set. Manual fix may be needed.")
            }
            
            // Step 7: Update tenant record with admin info
            logger.info("Step 7: Updating tenant record with admin information...")
            try {
                tenant.adminFirebaseId = userRecord.uid
                tenant.adminEmail = email
                val saved = tenantRepository.save(tenant)
                logger.info("Step 7 SUCCESS: Updated tenant record with adminFirebaseId='${userRecord.uid}', adminEmail='$email'")
                saved
            } catch (e: Exception) {
                logger.error("Step 7 FAILED: Error updating tenant record", e)
                // Don't fail - user is created in Firebase, just missing the link in DB
                logger.warn("WARNING: Admin user created in Firebase but tenant record not updated. Manual fix may be needed.")
            }
            
            logger.info("=== createAdminUser() COMPLETED SUCCESSFULLY: Created admin user with uid='${userRecord.uid}' for tenantId='$tenantId' ===")
            return userRecord.uid
            
        } catch (e: TenantException) {
            logger.error("=== createAdminUser() FAILED: TenantException ===", e)
            throw e
        } catch (e: Exception) {
            logger.error("=== createAdminUser() FAILED: Unexpected error ===", e)
            throw FirebaseException("Failed to create admin user: ${e.message}", e)
        }
    }
    
    /**
     * Get tenant by name
     * Returns the tenant DTO with tenant ID (Firebase tenant ID) for authentication
     */
    fun getTenantByName(name: String): TenantDto {
        logger.info("=== Starting getTenantByName() ===")
        logger.info("Request: name=$name")
        
        try {
            val normalizedName = name.lowercase().trim()
            logger.info("Step 1: Looking up tenant by name: '$normalizedName'...")
            
            val tenant = try {
                val found = tenantRepository.findByName(normalizedName)
                if (found == null) {
                    logger.warn("Step 1 FAILED: No tenant found with name '$normalizedName'")
                } else {
                    logger.info("Step 1 SUCCESS: Found tenant with tenantId=${found.tenantId}, firebaseTenantId=${found.firebaseTenantId}")
                }
                found
            } catch (e: Exception) {
                logger.error("Step 1 FAILED: Error looking up tenant by name", e)
                throw RuntimeException("Failed to look up tenant by name: ${e.message}", e)
            }
            
            if (tenant == null) {
                throw TenantNotFoundException("Tenant with name '$normalizedName' not found")
            }
            
            val dto = toDto(tenant)
            logger.info("=== getTenantByName() COMPLETED SUCCESSFULLY ===")
            return dto
            
        } catch (e: TenantException) {
            logger.error("=== getTenantByName() FAILED: TenantException ===", e)
            throw e
        } catch (e: Exception) {
            logger.error("=== getTenantByName() FAILED: Unexpected error ===", e)
            throw RuntimeException("Failed to get tenant by name: ${e.message}", e)
        }
    }
    
    /**
     * Helper method to convert Tenant to TenantDto
     */
    private fun toDto(tenant: Tenant): TenantDto {
        return TenantDto(
            id = tenant.id ?: throw IllegalStateException("Tenant ID is null for tenant: ${tenant.tenantId}"),
            tenantId = tenant.tenantId,
            firebaseTenantId = tenant.firebaseTenantId,
            name = tenant.name,
            description = tenant.description,
            domain = tenant.domain,
            createdAt = tenant.createdAt,
            adminEmail = tenant.adminEmail,
            isActive = tenant.isActive
        )
    }
}
