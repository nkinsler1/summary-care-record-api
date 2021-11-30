package uk.nhs.adaptors.scr.exceptions;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.http.HttpStatus;

import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueType.VALUE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

public class BadRequestException extends ScrBaseException implements OperationOutcomeError {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    private static OperationOutcome buildOperationOutcome(String message) {
        var operationOutcome = new OperationOutcome();
        operationOutcome.addIssue()
                .setCode(VALUE)
                .setSeverity(ERROR)
                .setDetails(new CodeableConcept().setText(message));
        return operationOutcome;
    }

    @Override
    public final OperationOutcome getOperationOutcome() {
        return buildOperationOutcome(getMessage());
    }

    @Override
    public final HttpStatus getStatusCode() {
        return BAD_REQUEST;
    }
}
