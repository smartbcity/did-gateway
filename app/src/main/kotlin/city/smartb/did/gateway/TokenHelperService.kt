package city.smartb.did.gateway

import city.smartb.did.gateway.config.TokenHelperConfig
import city.smartb.did.gateway.model.FulfillWalletResponse
import city.smartb.did.gateway.model.PresentableCredentials
import city.smartb.did.gateway.model.Token
import city.smartb.did.gateway.model.TokenResponse
import city.smartb.did.gateway.model.WalletAuthResponse
import city.smartb.did.gateway.model.WalletRedirectResponse
import city.smartb.did.gateway.utils.getFormDataAsString
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.google.gson.Gson
import id.walt.credentials.w3c.VerifiableCredential
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID
import org.apache.http.NameValuePair
import org.apache.http.client.utils.URLEncodedUtils
import org.springframework.http.MediaType
import org.springframework.stereotype.Service

@Service
class TokenHelperService(
    private val tokenHelperConfig: TokenHelperConfig,
    private val tokenTraderService: TokenTraderService
) {

    private val OBJECT_MAPPER = ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerModule(JavaTimeModule())

    private val HTTP_CLIENT = HttpClient
        .newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun generateJwt(userId: String): String {
        val state = startFlow()
        val walletRequestUri = getWalletRequestUri(state)
        val code = askWallet(walletRequestUri, userId)
        val accessToken = getAccessToken(code)
        println("IDP Access token: $accessToken")
        return tokenTraderService.exchangeToken(Token(accessToken))
    }


    @Throws(Exception::class)
    fun startFlow(): String {
        val clientId = tokenHelperConfig.idpClientId
        println(clientId)
        val responseType = "code" //possibles values: id_token token code
        val scope = "openid+profile" //possible values: vp_token, profile, openid, email
        val idpUrl = tokenHelperConfig.idpUrl

        val startSameDevice = HttpRequest.newBuilder()
            .uri(URI.create(java.lang.String.format("%s/api/oidc/authorize?response_type=%s&client_id=%s&scope=%s", idpUrl, responseType, clientId, scope)))
            .GET()
            .build()

        println(startSameDevice.uri().toString())

        val sameDeviceResponse: HttpResponse<String> = HTTP_CLIENT.send(startSameDevice, HttpResponse.BodyHandlers.ofString())

        val locationHeader = sameDeviceResponse.headers().firstValue("location").get()
        val params: List<NameValuePair> = URLEncodedUtils.parse(URI.create(locationHeader), Charset.forName("UTF-8"))

        println("state: ${params[0].value}")
        return params[0].value
    }

    fun getWalletRequestUri(state: String): String {
        val idpUrl = tokenHelperConfig.idpUrl
        val walletId = "x-device"

        val walletRedirectRequest = HttpRequest.newBuilder()
            .uri(URI.create(java.lang.String.format("%s/api/oidc/web-api/getWalletRedirectAddress?walletId=%s&state=%s", idpUrl, walletId, state)))
            .GET()
            .build()

        println(walletRedirectRequest.uri().toString())

        val walletRedirectResponse = HTTP_CLIENT.send(
            walletRedirectRequest,
            HttpResponse.BodyHandlers.ofString()
        )

        val wrr = OBJECT_MAPPER.readValue(
            walletRedirectResponse.body(),
            WalletRedirectResponse::class.java
        )

        if (wrr.url == null) return "wallet request not found"

        println("oidc url: ${wrr.url}")

        return wrr.url!!
    }

    fun getAccessToken(code: String): String {
        val idpUrl = tokenHelperConfig.idpUrl
        val tokenRequestFormData = mapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to ""
        )

        val accessTokenRequest = HttpRequest.newBuilder()
            .uri(URI.create(String.format("$idpUrl/api/oidc/token")))
            .POST(HttpRequest.BodyPublishers.ofString(getFormDataAsString(tokenRequestFormData)))
            .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .header("Authorization", "Basic ${getBasicAuth()}")
            .build()

        val accessTokenResponse: HttpResponse<String> = HTTP_CLIENT.send(
            accessTokenRequest,
            HttpResponse.BodyHandlers.ofString()
        )

        val accessToken = OBJECT_MAPPER.readValue(
            accessTokenResponse.body(),
            TokenResponse::class.java
        ).accessToken

        if (accessToken.isNullOrEmpty()) return "access not found"

        return accessToken

    }

    fun askWallet(walletRequestUri: String, userId: String): String {
        val walletAuthToken = getWalletAuthToken(userId)
        val sessionId = startWalletPresentation(walletRequestUri, walletAuthToken)
        val presentableCredentials = continueWalletPresentation(sessionId, walletAuthToken)
        return fulfillWallet(sessionId, walletAuthToken, presentableCredentials)
    }

    fun fulfillWallet(sessionId: String, authToken: String, presentableCredentials: PresentableCredentials): String {
        val walletAddress = tokenHelperConfig.walletUrl

        val fulfillWalletRequest = HttpRequest.newBuilder()
            .uri(URI.create(String.format("${walletAddress}/api/wallet/presentation/fulfill?sessionId=$sessionId")))
            .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(presentableCredentials.presentableCredentials)))
            .header("Authorization", "Bearer $authToken")
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build()

        println(fulfillWalletRequest.uri().toString())

        val fulfillWalletResponse: HttpResponse<String> = HTTP_CLIENT.send(
            fulfillWalletRequest,
            HttpResponse.BodyHandlers.ofString()
        )

        println(fulfillWalletResponse.body())

        val fulfillResponse = OBJECT_MAPPER.readValue(
            fulfillWalletResponse.body(),
            FulfillWalletResponse::class.java
        )

        return fulfillResponse.getCode()
    }

    fun continueWalletPresentation(sessionId: String, authToken: String): PresentableCredentials {
        val walletAddress = tokenHelperConfig.walletUrl
        val did = getDid(authToken)

        val walletContinueRequest = HttpRequest.newBuilder()
            .uri(URI.create(String.format("${walletAddress}/api/wallet/presentation/continue?sessionId=${sessionId}&did=${did}")))
            .GET()
            .header("Authorization", "Bearer $authToken")
            .build()

        println(walletContinueRequest.uri().toString())

        val walletContinueResponse: HttpResponse<String> = HTTP_CLIENT.send(
            walletContinueRequest,
            HttpResponse.BodyHandlers.ofString()
        )

        val presentableCredentials = OBJECT_MAPPER.readValue(
            walletContinueResponse.body(),
            PresentableCredentials::class.java
        )

        if (presentableCredentials.presentableCredentials.isEmpty()) {
            // TODO throw error
            println("no credentials available")
        }

        return presentableCredentials
    }

    fun startWalletPresentation(walletRequestUri: String, authToken: String): String {
        val walletAddress = tokenHelperConfig.walletUrl

        val payload = mapOf(
            "oidcUri" to walletRequestUri
        )

        val walletPresentationRequest = HttpRequest.newBuilder()
            .uri(URI.create(String.format("${walletAddress}/api/wallet/presentation/startPresentation")))
            .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)))
            .header("Authorization", "Bearer $authToken")
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build()

        println(walletPresentationRequest.uri().toString())

        val walletPresentationResponse: HttpResponse<String> = HTTP_CLIENT.send(
            walletPresentationRequest,
            HttpResponse.BodyHandlers.ofString()
        )

        println(walletPresentationResponse.body())
        return walletPresentationResponse.body()
    }

    fun getWalletAuthToken(id: String): String {
        val walletAddress = tokenHelperConfig.walletUrl

        val payload = mapOf("id" to id)

        val walletAuthRequest = HttpRequest.newBuilder()
            .uri(URI.create(String.format("${walletAddress}/api/auth/login")))
            .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)))
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build()

        println(walletAuthRequest.uri().toString())

        val walletAuthResponse: HttpResponse<String> = HTTP_CLIENT.send(
            walletAuthRequest,
            HttpResponse.BodyHandlers.ofString()
        )

        val token = OBJECT_MAPPER.readValue(
            walletAuthResponse.body(),
            WalletAuthResponse::class.java
        ).token ?: return "token not found"

        println("auth token: ${token}")
        return token
    }

    fun getBasicAuth(): String {
        return String(Base64.getEncoder().encode("${tokenHelperConfig.idpClientId}:${tokenHelperConfig.idpClientSecret}".toByteArray()))
    }

    fun getDid(authToken: String): String {
        val walletAddress = tokenHelperConfig.walletUrl

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


    fun initVerifiableCredential(userDid: String, type: String, payload: MutableMap<String, Any>): Any {
        val date = LocalDateTime.now().toString()
        val credentialId = UUID.randomUUID().toString()
        payload["id"] = userDid
        val vc = mapOf(
            "type" to listOf("VerifiableCredential", "VerifiableAttestation", type),
            "@context" to listOf("https://www.w3.org/2018/credentials/v1"),
            "id" to "urn:uuid:$credentialId",
            "issuer" to "did:key:issuerDid",
            "issuanceDate" to date,
            "issued" to date,
            "validFrom" to date,
            "credentialSubject" to payload
        )

        return VerifiableCredential.fromString(Gson().toJson(vc))
    }

    fun getCredentials(userId: String): String {
        val walletAddress = tokenHelperConfig.walletUrl
        val authToken = getWalletAuthToken(userId)

        val walletGetCredentials = HttpRequest.newBuilder()
            .uri(URI.create(String.format("${walletAddress}/api/wallet/credentials/list")))
            .GET()
            .header("Authorization", "Bearer $authToken")
            .build()

        val walletGetCredentialsResponse: HttpResponse<String> = HTTP_CLIENT.send(
            walletGetCredentials,
            HttpResponse.BodyHandlers.ofString()
        )

        return walletGetCredentialsResponse.body()
    }
}
