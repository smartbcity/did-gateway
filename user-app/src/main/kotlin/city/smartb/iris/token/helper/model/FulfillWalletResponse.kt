package city.smartb.iris.token.helper.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.net.URI
import java.nio.charset.Charset
import org.apache.http.NameValuePair
import org.apache.http.client.utils.URLEncodedUtils

class FulfillWalletResponse {
    @JsonProperty("rp_response")
    var rpResponse: String? = null

    fun getCode(): String {
        val params: List<NameValuePair> = URLEncodedUtils.parse(URI.create(rpResponse), Charset.forName("UTF-8"))
        for(p in params) {
            if (p.name == "code") return p.value
        }
        return "code not found"
    }
}
