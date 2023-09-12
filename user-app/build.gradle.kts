plugins {
	id("city.smartb.fixers.gradle.kotlin.jvm")
	id("org.springframework.boot")
}

dependencies {
	api("city.smartb.f2:f2-spring-boot-starter-function-http:${Versions.f2}")
	implementation("city.smartb.s2:s2-spring-boot-starter-utils-logger:${Versions.s2}")

	implementation("org.junit.jupiter:junit-jupiter-api:${Versions.junit}")
	implementation("org.apache.httpcomponents:httpcore:4.4.16")
	implementation("org.apache.httpcomponents:httpclient:4.5.14")

	implementation("city.smartb.i2:i2-spring-boot-starter-auth:${PluginVersions.i2}") // to retrieve jwt in current context

	implementation("id.walt:waltid-ssikit:${Versions.waltIdSsiKit}") {
		exclude("io.javalin", "javalin")
	}
	implementation("id.walt.servicematrix:WaltID-ServiceMatrix:1.1.3")

	implementation("id.walt:waltid-sd-jwt-jvm:1.2306191408.0")

	implementation("city.smartb.iris:iris-vc:0.3.1")

	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootBuildImage> {
	imageName.set("smartbcity/did-gateway:${this.project.version}")
}
