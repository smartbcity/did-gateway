package city.smartb.iris.token.helper.model

class PresentableCredentials {
    var presentableCredentials: MutableList<PresentableCredential> = mutableListOf()
}


class PresentableCredential {
    var claimId: String? = null
    var credentialId: String? = null
}
