DID_GATEWAY_NAME		:= smartbcity/did-gateway
DID_GATEWAY_IMG			:= ${DID_GATEWAY_NAME}:${VERSION}
DID_GATEWAY_PACKAGE 	:= app

package:
	VERSION=${VERSION} IMAGE_NAME=${DID_GATEWAY_NAME} ./gradlew build ${DID_GATEWAY_PACKAGE}:bootBuildImage -x test
	@docker push ${DID_GATEWAY_IMG}
