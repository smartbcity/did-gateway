package city.smartb.did.gateway

import city.smartb.did.gateway.commands.IssueCredentialCommand
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/")
open class TokenHelperEndpoint(
    private val tokenHelperService: TokenHelperService,
    private val issuerService: IssuerService
) {

    @GetMapping("/generateJwt")
    fun generateJwt(@RequestParam id: String): String {
        return tokenHelperService.generateJwt(id)
    }

    @GetMapping("/getdid")
    fun getDid(@RequestParam id: String): String {
        return tokenHelperService.getDid(tokenHelperService.getWalletAuthToken(id))
    }

    @PostMapping("/issue")
    fun issueCredential(@RequestBody command: IssueCredentialCommand): String {
        return issuerService.issue(command)
    }

    @GetMapping("/credentials/get")
    fun getCredentials(@RequestParam userId: String): String {
        return tokenHelperService.getCredentials(userId)
    }

}
