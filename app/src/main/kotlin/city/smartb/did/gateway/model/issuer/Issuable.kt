package city.smartb.did.gateway.model.issuer

class Issuable(
    val type: String,
    val credentialData: Map<String, Any>? = null
)
