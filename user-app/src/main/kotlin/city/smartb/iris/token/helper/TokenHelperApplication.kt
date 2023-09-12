package city.smartb.iris.token.helper

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["city.smartb.iris.token.helper"])
open class TokenHelperApplication

fun main(args: Array<String>) {
    runApplication<TokenHelperApplication>(*args)
}
