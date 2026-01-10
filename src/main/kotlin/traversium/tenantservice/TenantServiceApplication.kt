package traversium.tenantservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import traversium.commonmultitenancy.FlywayTenantMigration
import traversium.commonmultitenancy.MultiTenantAutoConfiguration

@SpringBootApplication
@Import(MultiTenantAutoConfiguration::class, FlywayTenantMigration::class)
class TenantServiceApplication

fun main(args: Array<String>) {
    runApplication<TenantServiceApplication>(*args)
}

