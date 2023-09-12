package city.smartb.iris.token.helper.config


import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
open class TokenHelperConfig {

    @Value("\${verifier.address}")
    lateinit var verifierAddress: String

    @Value("\${verifier.did}")
    lateinit var verifierDid: String

    @Value("\${wallet.address}")
    lateinit var walletAddress: String

    @Value("\${walletbis}")
    lateinit var walletBis: String

    @Value("\${idp.url}")
    lateinit var idpUrl: String

    @Value("\${idp.client-id}")
    lateinit var idpClientId: String

    @Value("\${idp.client-secret}")
    lateinit var idpClientSecret: String

    @Value("\${issuer.url}")
    lateinit var issuerUrl: String
}
