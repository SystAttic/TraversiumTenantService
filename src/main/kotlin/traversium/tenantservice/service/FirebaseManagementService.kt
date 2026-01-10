package traversium.tenantservice.service

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for managing Firebase tenants via Firebase Admin SDK
 */
@Service
class FirebaseManagementService(
    private val firebaseAuth: FirebaseAuth
) {
    private val logger = LoggerFactory.getLogger(FirebaseManagementService::class.java)
    
    /**
     * Creates a Firebase tenant using the Firebase Admin SDK
     */
    fun createTenant(displayName: String): com.google.firebase.auth.multitenancy.Tenant {
        try {
            val tenantManager = firebaseAuth.tenantManager
            
            val createRequest = com.google.firebase.auth.multitenancy.Tenant.CreateRequest()
                .setDisplayName(displayName)
                .setPasswordSignInAllowed(true)
                .setEmailLinkSignInEnabled(true)
            
            val tenant = tenantManager.createTenant(createRequest)
            logger.info("Successfully created Firebase tenant: ${tenant.tenantId} with display name: ${tenant.displayName}")
            
            return tenant
        } catch (e: Exception) {
            logger.error("Failed to create Firebase tenant via Admin SDK", e)
            throw RuntimeException("Failed to create Firebase tenant: ${e.message}", e)
        }
    }
    
    /**
     * Checks if a Firebase tenant exists
     */
    fun tenantExists(tenantId: String): Boolean {
        return try {
            firebaseAuth.tenantManager.getAuthForTenant(tenantId)
            true
        } catch (e: FirebaseAuthException) {
            false
        } catch (e: Exception) {
            logger.warn("Error checking if tenant exists: ${e.message}")
            false
        }
    }
    
    /**
     * Lists all Firebase tenants with comprehensive logging
     */
    fun listAllTenants(): List<com.google.firebase.auth.multitenancy.Tenant> {
        logger.info("=== FirebaseManagementService.listAllTenants() START ===")
        
        try {
            logger.info("Step 1: Getting Firebase tenant manager...")
            val tenantManager = firebaseAuth.tenantManager
            logger.info("Step 1 SUCCESS: Tenant manager obtained")
            
            logger.info("Step 2: Initializing tenant list and starting pagination...")
            val tenants = mutableListOf<com.google.firebase.auth.multitenancy.Tenant>()
            
            logger.info("Step 3: Fetching first page of tenants...")
            var page = try {
                val firstPage = tenantManager.listTenants(null)
                val firstPageCount = firstPage.values.count()
                logger.info("Step 3 SUCCESS: First page obtained, contains $firstPageCount tenant(s)")
                firstPage.values.forEachIndexed { index, tenant ->
                    logger.debug("  First page tenant[$index]: tenantId=${tenant.tenantId}, displayName=${tenant.displayName}")
                }
                firstPage
            } catch (e: Exception) {
                logger.error("Step 3 FAILED: Error fetching first page of tenants", e)
                throw RuntimeException("Failed to fetch first page of Firebase tenants: ${e.message}", e)
            }
            
            var pageNumber = 1
            while (page != null) {
                logger.info("Step 4.${pageNumber}: Processing page $pageNumber...")
                try {
                    var pageCount = 0
                    page.values.forEach { tenant ->
                        logger.debug("  Adding tenant from page $pageNumber: tenantId=${tenant.tenantId}, displayName=${tenant.displayName}")
                        tenants.add(tenant)
                        pageCount++
                    }
                    logger.info("Step 4.${pageNumber} SUCCESS: Added $pageCount tenant(s) from page $pageNumber")
                } catch (e: Exception) {
                    logger.error("Step 4.${pageNumber} FAILED: Error processing page $pageNumber", e)
                    throw RuntimeException("Failed to process page $pageNumber: ${e.message}", e)
                }
                
                // Check if there are more pages
                logger.info("Step 5.${pageNumber}: Checking for more pages...")
                page = try {
                    if (page.hasNextPage()) {
                        logger.info("Step 5.${pageNumber} SUCCESS: More pages available, fetching next page...")
                        val nextPage = page.nextPage
                        pageNumber++
                        val nextPageCount = nextPage.values.count()
                        logger.info("Step 5.${pageNumber - 1} SUCCESS: Next page obtained, contains $nextPageCount tenant(s)")
                        nextPage
                    } else {
                        logger.info("Step 5.${pageNumber} SUCCESS: No more pages available")
                        null
                    }
                } catch (e: Exception) {
                    logger.error("Step 5.${pageNumber} FAILED: Error checking/fetching next page", e)
                    throw RuntimeException("Failed to fetch next page: ${e.message}", e)
                }
            }
            
            logger.info("=== FirebaseManagementService.listAllTenants() COMPLETED SUCCESSFULLY: Total ${tenants.size} tenant(s) ===")
            return tenants
            
        } catch (e: Exception) {
            logger.error("=== FirebaseManagementService.listAllTenants() FAILED ===", e)
            throw e
        }
    }
}

