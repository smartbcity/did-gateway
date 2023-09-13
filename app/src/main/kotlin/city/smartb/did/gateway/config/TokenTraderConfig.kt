package city.smartb.did.gateway.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
open class TokenTraderConfig {

    @Value("\${target-idp.url}")
    lateinit var targetIdpUrl: String

    @Value("\${target-idp.realm}")
    lateinit var targetIdpRealm: String

    @Value("\${target-idp.client-id}")
    lateinit var targetIdpClientId: String

    @Value("\${target-idp.client-secret}")
    lateinit var targetIdpClientSecret: String

    @Value("\${idp.url}")
    lateinit var idpUrl: String
}
