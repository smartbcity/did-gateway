package city.smartb.iris.token.helper.model

import com.fasterxml.jackson.annotation.JsonProperty

class TokenResponse {
    @JsonProperty("access_token")
    val accessToken: String? = null
    @JsonProperty("token_type")
    val tokenType: String? = null
    @JsonProperty("expires_in")
    val expiresIn: Long = 0
}
