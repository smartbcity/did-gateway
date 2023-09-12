package city.smartb.iris.token.helper.model.issuer

class Issuable(
    val type: String,
    val credentialData: Map<String, Any>? = null
)
