package city.smartb.did.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["city.smartb"])
open class DidGatewayApplication

fun main(args: Array<String>) {
    runApplication<DidGatewayApplication>(*args)
}
