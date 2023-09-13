package city.smartb.did.gateway.config


import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
open class TokenHelperConfig {

    @Value("\${wallet.url}")
    lateinit var walletUrl: String

    @Value("\${idp.url}")
    lateinit var idpUrl: String

    @Value("\${idp.client-id}")
    lateinit var idpClientId: String

    @Value("\${idp.client-secret}")
    lateinit var idpClientSecret: String

    @Value("\${issuer.url}")
    lateinit var issuerUrl: String
}
