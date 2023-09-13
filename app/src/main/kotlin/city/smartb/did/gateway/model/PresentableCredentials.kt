package city.smartb.did.gateway.model

class PresentableCredentials {
    var presentableCredentials: MutableList<PresentableCredential> = mutableListOf()
}


class PresentableCredential {
    var claimId: String? = null
    var credentialId: String? = null
}
