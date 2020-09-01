package uk.nhs.adaptors.scr.utils;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;

import uk.nhs.adaptors.scr.exceptions.FhirMappingException;

public class FhirHelper {

    public static <T extends Resource> T getDomainResource(Bundle bundle, Class<T> resourceType) {
        return bundle.getEntry().stream()
            .map(Bundle.BundleEntryComponent::getResource)
            .filter(resource -> resource.getClass() == resourceType)
            .map(resourceType::cast)
            .reduce((a, b) -> {
                throw new FhirMappingException("There is more than 1 resource of type " + resourceType.getSimpleName());
            })
            .orElseThrow(() -> new FhirMappingException(resourceType.getSimpleName() + " missing from payload"));
    }
}
