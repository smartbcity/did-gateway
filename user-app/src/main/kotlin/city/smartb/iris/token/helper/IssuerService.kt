package city.smartb.iris.token.helper

import city.smartb.iris.token.helper.commands.IssueCredentialCommand
import city.smartb.iris.token.helper.config.TokenHelperConfig
import city.smartb.iris.token.helper.model.WalletAuthResponse
import city.smartb.iris.token.helper.model.issuer.Issuable
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Service

@Service
class IssuerService(
    private val tokenHelperConfig: TokenHelperConfig
){
    private val OBJECT_MAPPER = ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerModule(JavaTimeModule())

    private val HTTP_CLIENT = HttpClient
        .newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun issue(command: IssueCredentialCommand): String {
        val oidcUrl = startIssuanceFlow(command.issuable)
        val authToken = getWalletAuthToken(command.userId)
        val sessionId = sendOidcUrlToWallet(oidcUrl, authToken)
        return validateIssuance(sessionId, authToken)
    }

    fun startIssuanceFlow(issuable: Issuable): String {
        val isPreAuthorized = true
        val walletId = "x-device" // qr code flow check for a real m2m flow
        val tenantId = "default"

        // TODO make it dynamic
        val credentials = listOf(issuable)

        val payload = mapOf("credentials" to credentials)

        val askCredentialRequest = HttpRequest.newBuilder()
            .uri(URI.create(String.format("${tokenHelperConfig.issuerUrl}/issuer-api/$tenantId/credentials/issuance/request?walletId=$walletId&isPreAuthorized=$isPreAuthorized")))
            .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)))
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build()

        val askCredentialResponse: HttpResponse<String> = HTTP_CLIENT.send(
            askCredentialRequest,
            HttpResponse.BodyHandlers.ofString()
        )

        // returns an oidc url to be scanned by the wallet
        return askCredentialResponse.body()
    }

    fun sendOidcUrlToWallet(oidcUrl: String, authToken: String): String {
        val payload = mapOf("oidcUri" to oidcUrl)

        val sendOidcUrlRequest = HttpRequest.newBuilder()
            .uri(URI.create(String.format("${tokenHelperConfig.walletBis}/api/wallet/issuance/startIssuerInitiatedIssuance")))
            .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)))
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .header("Authorization", "Bearer $authToken")
            .build()

        val sendOidcUrlResponse: HttpResponse<String> = HTTP_CLIENT.send(
            sendOidcUrlRequest,
            HttpResponse.BodyHandlers.ofString()
        )

        return sendOidcUrlResponse.body()
    }

    fun validateIssuance(sessionId: String, authToken: String): String {
        val did = getDid(authToken)

        val validateIssuanceRequest = HttpRequest.newBuilder()
            .uri(URI.create(String.format("${tokenHelperConfig.walletBis}/api/wallet/issuance/continueIssuerInitiatedIssuance?sessionId=$sessionId&did=$did")))
            .GET()
            .header("Authorization", "Bearer $authToken")
            .build()

        val validateIssuanceResponse: HttpResponse<String> = HTTP_CLIENT.send(
            validateIssuanceRequest,
            HttpResponse.BodyHandlers.ofString()
        )

        if (validateIssuanceResponse.statusCode() != 200) {
            println("issuance validation failed")
            return "issuance validation failed"
        }
        return "ok"
    }

    // TODO move this function in a service as it is reused by multiple services
    // TODO Later make wallet authentication stronger
    fun getWalletAuthToken(userId: String): String {
        val walletAddress = tokenHelperConfig.walletBis

        val payload = mapOf("id" to userId)

        val walletAuthRequest = HttpRequest.newBuilder()
            .uri(URI.create(String.format("${walletAddress}/api/auth/login")))
            .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)))
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build()

        val walletAuthResponse: HttpResponse<String> = HTTP_CLIENT.send(
            walletAuthRequest,
            HttpResponse.BodyHandlers.ofString()
        )

        val token = OBJECT_MAPPER.readValue(
            walletAuthResponse.body(),
            WalletAuthResponse::class.java
        ).token ?: return "token not found"

        return token
    }

    // TODO same as wallet auth token function
    fun getDid(authToken: String): String {
        val walletAddress = tokenHelperConfig.walletBis

        val walletGetDid = HttpRequest.newBuilder()
            .uri(URI.create(String.format("${walletAddress}/api/wallet/did/list")))
            .GET()
            .header("Authorization", "Bearer $authToken")
            .build()

        val walletGetDidResponse: HttpResponse<String> = HTTP_CLIENT.send(
            walletGetDid,
            HttpResponse.BodyHandlers.ofString()
        )

        val dids = OBJECT_MAPPER.readValue(walletGetDidResponse.body(), List::class.java)

        if (dids.isEmpty()) return "no did for this user"
        return dids.first() as String
    }
}
