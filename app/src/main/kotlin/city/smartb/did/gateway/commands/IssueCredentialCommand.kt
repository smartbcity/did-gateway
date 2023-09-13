package city.smartb.did.gateway.commands

import city.smartb.did.gateway.model.issuer.Issuable

class IssueCredentialCommand(
    val userId: String,
    val issuable: Issuable
)
