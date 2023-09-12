package city.smartb.iris.token.helper.utils

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun getFormDataAsString(formData: Map<String?, String?>): String {
    val formBodyBuilder = StringBuilder()
    for ((key, value) in formData) {
        if (formBodyBuilder.length > 0) {
            formBodyBuilder.append("&")
        }
        formBodyBuilder.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
        formBodyBuilder.append("=")
        formBodyBuilder.append(URLEncoder.encode(value, StandardCharsets.UTF_8))
    }
    return formBodyBuilder.toString()
}
