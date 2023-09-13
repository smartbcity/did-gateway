package city.smartb.did.gateway

import city.smartb.did.gateway.config.TokenTraderConfig
import city.smartb.did.gateway.model.Token
import city.smartb.did.gateway.model.TokenResponse
import city.smartb.did.gateway.utils.getFormDataAsString
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import id.walt.credentials.w3c.VerifiableCredential
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Service

@Service
class TokenTraderService(
    private val tokenTraderConfig: TokenTraderConfig
){

    private val OBJECT_MAPPER = ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val HTTP_CLIENT = HttpClient
        .newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun exchangeToken(token: Token): String {
        if (!validateIncomingJwt(token)) {
            return "incoming jwt is invalid"
        }
        return exchangeJwt(token)
    }

    fun validateIncomingJwt(token: Token): Boolean {
        // check if jwt is valid
        if (!VerifiableCredential.isJWT(token.jwt)) {
            println("incoming jwt is malformed")
            return false
        }

        // check jwt expiration date
//        if (JwtUtils.isJwtExpired(token.jwt)) {
//            println("incoming jwt is expired")
//            return false
//        }

        // check jwt signature
        // TODO

        return true
    }

    fun exchangeJwt(token: Token): String {
        val exchangeTokenPayload = mapOf(
            "grant_type" to "urn:ietf:params:oauth:grant-type:token-exchange",
            "requested_token_type" to "urn:ietf:params:oauth:token-type:access_token",
            "client_id" to tokenTraderConfig.targetIdpClientId,
            "client_secret" to tokenTraderConfig.targetIdpClientSecret,
            "subject_token" to token.jwt,
            "subject_issuer" to "${tokenTraderConfig.idpUrl}/api/oidc",
            "audience" to tokenTraderConfig.targetIdpClientId
        )

        val tokenRequest = HttpRequest.newBuilder()
            .uri(URI.create(String.format("${tokenTraderConfig.targetIdpUrl}/realms/${tokenTraderConfig.targetIdpRealm}/protocol/openid-connect/token")))
            .POST(HttpRequest.BodyPublishers.ofString(getFormDataAsString(exchangeTokenPayload)))
            .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .build()

        println("Exchanging idp access token...")
        println(tokenRequest.uri().toString())

        val requestResponse = HTTP_CLIENT.send(
            tokenRequest,
            HttpResponse.BodyHandlers.ofString()
        )

        val accessToken = OBJECT_MAPPER.readValue(
            requestResponse.body(),
            TokenResponse::class.java
        ).accessToken

        if (accessToken == null) return "access token could not be exchanged"

        return accessToken
    }
}
