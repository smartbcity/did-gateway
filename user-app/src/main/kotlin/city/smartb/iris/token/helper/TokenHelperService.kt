package city.smartb.iris.token.helper

import city.smartb.iris.token.helper.config.TokenHelperConfig
import city.smartb.iris.token.helper.model.FulfillWalletResponse
import city.smartb.iris.token.helper.model.PresentableCredentials
import city.smartb.iris.token.helper.model.Token
import city.smartb.iris.token.helper.model.TokenResponse
import city.smartb.iris.token.helper.model.WalletAuthResponse
import city.smartb.iris.token.helper.model.WalletRedirectResponse
import city.smartb.iris.token.helper.utils.getFormDataAsString
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

    private fun initiateLogin(): String {
        return UUID.randomUUID().toString()
    }


    @Throws(Exception::class)
    fun startFlow(): String {
        val clientId = tokenHelperConfig.idpClientId
        val responseType = "code" //possibles values: id_token token code
        val scope = "openid+profile" //possible values: vp_token, profile, openid, email
        val idpUrl = tokenHelperConfig.idpUrl

        val startSameDevice = HttpRequest.newBuilder()
            .uri(URI.create(java.lang.String.format("%s/api/oidc/authorize?response_type=%s&client_id=%s&scope=%s", idpUrl, responseType, clientId, scope)))
            .GET()
            .build()

        val sameDeviceResponse: HttpResponse<String> = HTTP_CLIENT.send(startSameDevice, HttpResponse.BodyHandlers.ofString())

        val locationHeader = sameDeviceResponse.headers().firstValue("location").get()
        val params: List<NameValuePair> = URLEncodedUtils.parse(URI.create(locationHeader), Charset.forName("UTF-8"))

        return params[0].value
    }

    fun getWalletRequestUri(state: String): String {
        val idpUrl = tokenHelperConfig.idpUrl
        val walletId = "x-device"

        val walletRedirectRequest = HttpRequest.newBuilder()
            .uri(URI.create(java.lang.String.format("%s/api/oidc/web-api/getWalletRedirectAddress?walletId=%s&state=%s", idpUrl, walletId, state)))
            .GET()
            .build()

        val walletRedirectResponse = HTTP_CLIENT.send(
            walletRedirectRequest,
            HttpResponse.BodyHandlers.ofString()
        )

        val wrr = OBJECT_MAPPER.readValue(
            walletRedirectResponse.body(),
            WalletRedirectResponse::class.java
        )

        if (wrr.url == null) return "wallet request not found"

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
        val walletAddress = tokenHelperConfig.walletBis

        val fulfillWalletRequest = HttpRequest.newBuilder()
            .uri(URI.create(String.format("${walletAddress}/api/wallet/presentation/fulfill?sessionId=$sessionId")))
            .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(presentableCredentials.presentableCredentials)))
            .header("Authorization", "Bearer $authToken")
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build()

        val fulfillWalletResponse: HttpResponse<String> = HTTP_CLIENT.send(
            fulfillWalletRequest,
            HttpResponse.BodyHandlers.ofString()
        )

        val fulfillResponse = OBJECT_MAPPER.readValue(
            fulfillWalletResponse.body(),
            FulfillWalletResponse::class.java
        )

        return fulfillResponse.getCode()
    }

    fun continueWalletPresentation(sessionId: String, authToken: String): PresentableCredentials {
        val walletAddress = tokenHelperConfig.walletBis
        val did = getDid(authToken)

        val walletContinueRequest = HttpRequest.newBuilder()
            .uri(URI.create(String.format("${walletAddress}/api/wallet/presentation/continue?sessionId=${sessionId}&did=${did}")))
            .GET()
            .header("Authorization", "Bearer $authToken")
            .build()

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
        val walletAddress = tokenHelperConfig.walletBis

        val payload = mapOf(
            "oidcUri" to walletRequestUri
        )

        val walletPresentationRequest = HttpRequest.newBuilder()
            .uri(URI.create(String.format("${walletAddress}/api/wallet/presentation/startPresentation")))
            .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)))
            .header("Authorization", "Bearer $authToken")
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build()

        val walletPresentationResponse: HttpResponse<String> = HTTP_CLIENT.send(
            walletPresentationRequest,
            HttpResponse.BodyHandlers.ofString()
        )

        return walletPresentationResponse.body()
    }

    fun getWalletAuthToken(id: String): String {
        val walletAddress = tokenHelperConfig.walletBis

        val payload = mapOf("id" to id)

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

    fun getBasicAuth(): String {
        return String(Base64.getEncoder().encode("${tokenHelperConfig.idpClientId}:${tokenHelperConfig.idpClientSecret}".toByteArray()))
    }

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

    // FIWARE Legacy version
    // TODO remove
//    fun generateJwt(): String {
//        // the application is called and initiates a login-session(f.e. a frontend would forward to the login page)
//        val state = initiateLogin()
//        assertTrue(
//            state != null,
//            "A login session should have been started."
//        )
//
//        // since we are testing the same-device flow, the application will initiate that flow, to be continued by the wallet
//        // in a frontend application, this would forward to the login page of the verifier, to get a scanable qr
//        // the same device flow expects a redirect, that should be handled by the wallet. In the test, we dont follow the redirect
//        // but instead capture the response and hand it over to the wallet "manually"
//        val sameDeviceParams = startSameDeviceFlow(state)
//        assertNotNull(
//            sameDeviceParams,
//            "A redirect with the parameters for the same device flow should have been returned."
//        )
//
//        // the wallet on the same device will handle the redirect and continue the authorization flow. It will also expect a 302 that will
//        // hand over the flow to the application(to continue the actual jwt retrieval) again
//        val authResponseParams = redirectRequestToWallet(sameDeviceParams)
//        assertNotNull(
//            authResponseParams,
//            "The parameters to be used for the actual token retrieval should have been returned."
//        )
//
//        // the application will receive the redirect and handle the parameters accordingly, e.g. exchange the auth_token
//        // through the token endpoint for the JWT.
//        val jwt = exchangeCodeForJWT(authResponseParams)
//
//        assertNotNull(jwt, "A JWT should have been retrieved.")
//
//
//
//        return jwt!!
//    }
//    @Throws(Exception::class)
//    fun startSameDeviceFlow(state: String): SameDeviceParams {
//        val startSameDevice = HttpRequest.newBuilder()
//            .uri(URI.create(java.lang.String.format("%s/api/v1/samedevice?state=%s", tokenHelperConfig.verifierAddress, state)))
//            .GET()
//            .build()
//
//        val sameDeviceResponse: HttpResponse<String> = HTTP_CLIENT.send(
//            startSameDevice,
//            HttpResponse.BodyHandlers.ofString()
//        )
//
//        assertEquals(
//            302,
//            sameDeviceResponse.statusCode(),
//            "We should receive a redirect."
//        )
//
//        val locationHeader = sameDeviceResponse.headers().firstValue("location").get()
//        val params: List<NameValuePair> = URLEncodedUtils.parse(URI.create(locationHeader), Charset.forName("UTF-8"))
//        val sameDeviceParams = SameDeviceParams()
//
//        params.forEach(Consumer { p: NameValuePair ->
//            when (p.name) {
//                "response_type" -> sameDeviceParams.responseType = p.value
//                "response_mode" -> sameDeviceParams.responseMode = p.value
//                "client_id" -> sameDeviceParams.clientId = p.value
//                "redirect_uri" -> sameDeviceParams.redirectUri = p.value
//                "state" -> sameDeviceParams.state = p.value
//                "nonce" -> sameDeviceParams.nonce = p.value
//                "scope" -> sameDeviceParams.scope = p.value
//                else -> logger.warn("Received an unknown parameter: {}", p.name)
//            }
//        })
//        assertEquals("vp_token", sameDeviceParams.responseType, "Currently, only vp_token is supported.")
//        assertEquals("direct_post", sameDeviceParams.responseMode, "Currently, only direct_post is supported.")
//        assertEquals(
//            tokenHelperConfig.verifierDid, sameDeviceParams.clientId,
//            "The expected participant should have initiated the flow."
//        )
//        assertNotNull(sameDeviceParams.redirectUri, "A redirect_uri should have been received.")
//        assertNotNull(sameDeviceParams.state, "The verifier should have creadet a state.")
//        return sameDeviceParams
//    }
//    fun redirectRequestToWallet(sameDeviceParams: SameDeviceParams): AuthResponseParams {
//        val walletRequest = HttpRequest.newBuilder()
//            .uri(URI.create(String.format("%s/answerAuthRequest", tokenHelperConfig.walletAddress)))
//            .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(sameDeviceParams)))
//            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
//            .build()
//
//        val walletResponse = HTTP_CLIENT.send(
//            walletRequest,
//            HttpResponse.BodyHandlers.ofString()
//        )
//
//        val authResponseParams = OBJECT_MAPPER.readValue(walletResponse.body(), AuthResponseParams::class.java)
//
//        return authResponseParams
//    }
//    fun exchangeCodeForJWT(authResponseParams: AuthResponseParams): String? {
//
//        val tokenRequestFormData = mapOf(
//            "grant_type" to "authorization_code",
//            "code" to authResponseParams.code!!,
//            "redirect_uri" to tokenHelperConfig.verifierAddress + "/"
//        )
//
//        val jwtRequest = HttpRequest.newBuilder()
//            .uri(URI.create(String.format("%s%s", tokenHelperConfig.verifierAddress, "/token")))
//            .POST(HttpRequest.BodyPublishers.ofString(getFormDataAsString(tokenRequestFormData)))
//            .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
//            .build()
//
//        val tokenResponse: HttpResponse<String> = HTTP_CLIENT.send(
//            jwtRequest,
//            HttpResponse.BodyHandlers.ofString()
//        )
//
//        assertEquals(HttpStatus.SC_OK, tokenResponse.statusCode(), "A token should have been returned.")
//
//        val tr = OBJECT_MAPPER.readValue(
//            tokenResponse.body(),
//            TokenResponse::class.java
//        )
//
//        return tr.accessToken
//    }
}
