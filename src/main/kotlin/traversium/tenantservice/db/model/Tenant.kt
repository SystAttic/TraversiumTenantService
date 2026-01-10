package traversium.tenantservice.db.model

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "tenants", schema = "public")
class Tenant(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    
    @Column(nullable = false, unique = true)
    var tenantId: String,
    
    @Column(nullable = false, unique = true)
    var name: String,
    
    @Column
    var description: String? = null,
    
    @Column(nullable = false)
    var domain: String, // e.g., "company1" for tenants.company1.traversium.com
    
    @Column(nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
    
    @Column
    var adminEmail: String? = null,
    
    @Column
    var adminFirebaseId: String? = null,
    
    @Column
    var firebaseTenantId: String? = null, // Firebase-generated tenant ID
    
    @Column(nullable = false)
    var isActive: Boolean = true
) {
    constructor() : this(
        id = null,
        tenantId = "",
        name = "",
        description = null,
        domain = "",
        createdAt = OffsetDateTime.now(),
        adminEmail = null,
        adminFirebaseId = null,
        firebaseTenantId = null,
        isActive = true
    )
}

