package uk.nhs.adaptors.scr.utils.spine.mock;

import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import static io.restassured.RestAssured.given;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import uk.nhs.adaptors.scr.utils.EndpointMockData;
import uk.nhs.adaptors.scr.utils.spine.mock.interfaces.SpineMockSetupForHttpMethod;
import uk.nhs.adaptors.scr.utils.spine.mock.interfaces.SpineMockSetupWithHttpStatusCode;
import uk.nhs.adaptors.scr.utils.spine.mock.interfaces.SpineMockSetupWithResponseContent;
import uk.nhs.adaptors.scr.utils.spine.mock.interfaces.SpineMockSetupWithResponseEntity;

@Component
public class SpineMockSetupEndpoint {
    private static final String SETUP_ENDPOINT = "/setup";
    private static final int PORT = 8081;

    public SpineMockSetupForHttpMethod forUrl(String url) {
        Builder builder = new Builder();
        builder.url = url;
        return builder;
    }

    private static class Builder implements SpineMockSetupWithHttpStatusCode, SpineMockSetupForHttpMethod,
        SpineMockSetupWithResponseContent, SpineMockSetupWithResponseEntity {
        private String url;
        private String httpMethod;
        private Integer httpStatusCode;
        private String responseContent;
        private ResponseEntity responseEntity;

        @Override
        public SpineMockSetupWithHttpStatusCode forHttpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
            return this;
        }

        @Override
        public SpineMockSetupWithResponseContent withHttpStatusCode(Integer httpStatusCode) {
            this.httpStatusCode = httpStatusCode;
            return this;
        }

        @Override
        public void withResponseContent(String responseContent) {
            this.responseContent = responseContent;
            setupEndpoint();
        }

        @Override
        public void withResponseEntity(ResponseEntity responseEntity) {
            this.responseEntity = responseEntity;
            setupEndpoint();
        }

        private void setupEndpoint() {
            EndpointMockData endpointMockData = new EndpointMockData();
            endpointMockData.setUrl(url);
            endpointMockData.setHttpMethod(httpMethod);
            endpointMockData.setHttpStatusCode(httpStatusCode);
            endpointMockData.setResponseContent(responseContent);
            endpointMockData.setResponseEntity(responseEntity);

            try {
                given()
                    .port(PORT)
                    .contentType(APPLICATION_JSON_VALUE)
                    .body(new ObjectMapper().writeValueAsString(endpointMockData))
                    .when()
                    .post(SETUP_ENDPOINT)
                    .then()
                    .statusCode(OK.value())
                    .extract();
            } catch (JsonProcessingException e) {
                // skip
            }
            int test =1;
        }
    }
}
