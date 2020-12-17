package uk.nhs.adaptors.scr.controllers.fhir;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.WebAsyncTask;
import uk.nhs.adaptors.scr.components.FhirParser;
import uk.nhs.adaptors.scr.config.ScrConfiguration;
import uk.nhs.adaptors.scr.config.SpineConfiguration;
import uk.nhs.adaptors.scr.exceptions.ScrTimeoutException;
import uk.nhs.adaptors.scr.models.RequestData;
import uk.nhs.adaptors.scr.services.UploadScrService;

import javax.validation.constraints.NotNull;
import java.util.concurrent.Callable;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static uk.nhs.adaptors.scr.controllers.FhirMediaTypes.APPLICATION_FHIR_JSON_VALUE;

@RestController
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Slf4j
public class FhirController {
    private final FhirParser fhirParser;
    private final UploadScrService uploadScrService;
    private final SpineConfiguration spineConfiguration;
    private final ScrConfiguration scrConfiguration;

    @PostMapping(
        path = "/Bundle",
        consumes = {APPLICATION_FHIR_JSON_VALUE},
        produces = {APPLICATION_FHIR_JSON_VALUE})
    @ResponseStatus(OK)
    public WebAsyncTask<ResponseEntity<?>> acceptFhir(
        @RequestHeader("Nhsd-Asid") @NotNull String nhsdAsid,
        @RequestHeader("client-ip") @NotNull String clientIp,
        @RequestHeader("NHSD-Identity-UUID") @NotNull String nhsdIdentity,
        @RequestBody String body) {
        LOGGER.debug("Using cfg: asid-from={} party-from={} asid-to={} party-to={} client-ip={} NHSD-Identity-UUID={}",
            nhsdAsid,
            scrConfiguration.getPartyIdFrom(),
            scrConfiguration.getNhsdAsidTo(),
            scrConfiguration.getPartyIdTo(),
            clientIp,
            nhsdIdentity);

        var requestData = new RequestData();
        requestData.setBundle(fhirParser.parseResource(body, Bundle.class))
            .setNhsdAsid(nhsdAsid)
            .setClientIp(clientIp)
            .setNhsdIdentity(nhsdIdentity);

        var mdcContextMap = MDC.getCopyOfContextMap();
        Callable<ResponseEntity<?>> callable = () -> {
            MDC.setContextMap(mdcContextMap);
            uploadScrService.uploadScr(requestData);
            return ResponseEntity
                .status(CREATED)
                .build();
        };

        var task = new WebAsyncTask<>(spineConfiguration.getScrResultTimeout(), callable);
        task.onTimeout(() -> {
            throw new ScrTimeoutException();
        });

        return task;
    }
}
