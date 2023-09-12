package city.smartb.iris.token.helper.commands

import city.smartb.iris.token.helper.model.issuer.Issuable

class IssueCredentialCommand(
    val userId: String,
    val issuable: Issuable
)
